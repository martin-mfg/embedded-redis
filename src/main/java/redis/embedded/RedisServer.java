package redis.embedded;

import lombok.extern.slf4j.Slf4j;
import redis.embedded.common.CommonConstant;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class RedisServer extends AbstractRedisServerInstance {
    private static final String REDIS_SERVER_READY_PATTERN = ".*(R|r)eady to accept connections.*";

    public RedisServer() {
        this(CommonConstant.DEFAULT_REDIS_STANDALONE_PORT);
        log.debug("args: " + this.args);
    }

    public RedisServer(int port) {
        super(port);
        this.args = builder().port(port).build().args;
        log.debug("args: " + this.args);
    }

    public RedisServer(File executable, int port) {
        super(port);
        this.args = Arrays.asList(executable.getAbsolutePath(), "--port", Integer.toString(port));
        log.debug("args: " + this.args);
    }

    public RedisServer(RedisServerExecProvider redisExecProvider, int port) throws IOException {
        super(port);
        this.args =
                Arrays.asList(redisExecProvider.get().getAbsolutePath(), "--port", Integer.toString(port));
        log.debug("args: " + this.args);
    }

    RedisServer(List<String> args, int port) {
        super(port);
        this.args = new ArrayList<>(args);
        log.debug("args: " + this.args);
    }

    public static RedisServerBuilder builder() {
        return new RedisServerBuilder();
    }

    @Override
    protected String redisReadyPattern() {
        return REDIS_SERVER_READY_PATTERN;
    }
}
