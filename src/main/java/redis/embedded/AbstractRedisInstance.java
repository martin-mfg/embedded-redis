package redis.embedded;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import redis.embedded.enums.RedisInstanceModeEnum;
import redis.embedded.exceptions.EmbeddedRedisException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
abstract class AbstractRedisInstance implements IRedisInstance {
    private Process redisProcess;
    @Setter
    @Getter
    private volatile boolean active = false;

    @Getter
    private List<String> args = new ArrayList<>();
    private ExecutorService executor;


    protected void doUpdateArgs(List<String> args) {
        this.args.addAll(args);
    }

    public void doStart(RedisInstanceModeEnum instanceMode) throws EmbeddedRedisException {
        if (active) {
            log.warn("This redis client instance is already running...");
            throw new EmbeddedRedisException("This redis client instance is already running...");
        }
        try {
            redisProcess = createRedisProcessBuilder().start();
            installExitHook(instanceMode.getValue());
            logStandardError();
            awaitRedisInstanceReady();
            active = true;
        } catch (IOException e) {
            log.warn("Failed to start Redis Client instance. exception: {}", e.getMessage(), e);
            throw new EmbeddedRedisException("Failed to start Redis Client instance", e);
        }
    }

    public void installExitHook(String mode) {
        String name = String.format("Redis%sInstanceCleaner", StrUtil.upperFirst(mode));
        Runtime.getRuntime().addShutdownHook(new Thread(this::doStop, name));
    }

    public void logStandardError() {
        final InputStream errorStream = redisProcess.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
        Runnable printReaderTask = new PrintReaderRunnable(reader);
        executor = Executors.newSingleThreadExecutor();
        executor.submit(printReaderTask);
    }


    public void awaitRedisInstanceReady() throws IOException {
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(redisProcess.getInputStream()));
        try {
            StringBuilder outputStringBuffer = new StringBuilder();
            String outputLine;
            do {
                outputLine = reader.readLine();
                if (outputLine == null) {
                    log.warn(
                            "Can't start redis client. Check logs for details. Redis process log: "
                                    + outputStringBuffer);
                    // Something goes wrong. Stream is ended before server was activated.
                    throw new RuntimeException(
                            "Can't start redis client. Check logs for details. Redis process log: "
                                    + outputStringBuffer);
                } else {
                    outputStringBuffer.append("\n");
                    outputStringBuffer.append(outputLine);
                }
                log.debug(outputLine);
            } while (!outputLine.matches(redisInstanceReadyPattern()));
        } finally {
            IOUtils.closeQuietly(reader, null);
        }
    }

    protected abstract String redisInstanceReadyPattern();


    public ProcessBuilder createRedisProcessBuilder() {
        List<String> args = getArgs();
        File executable = new File(args.get(0));
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(executable.getParentFile());
        return pb;
    }

    public synchronized void doStop() throws EmbeddedRedisException {
        if (active) {
            if ((executor != null) && (!executor.isShutdown())) {
                executor.shutdown();
            }
            redisProcess.destroy();
            tryWaitFor();
            active = false;
        }
    }

    public void tryWaitFor() {
        try {
            redisProcess.waitFor();
        } catch (InterruptedException e) {
            String msg = "Failed to stop redis client instance";
            log.warn("{}. exception: {}", msg, e.getMessage(), e);
            throw new EmbeddedRedisException(msg, e);
        }
    }


    private static class PrintReaderRunnable implements Runnable {
        private final BufferedReader reader;

        private PrintReaderRunnable(BufferedReader reader) {
            this.reader = reader;
        }

        public void run() {
            try {
                readLines();
            } finally {
                IOUtils.closeQuietly(reader, null);
            }
        }

        public void readLines() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info(line);
                }
            } catch (IOException e) {
                log.warn("Failed to readLines. exception: {}", e.getMessage(), e);
            }
        }
    }
}
