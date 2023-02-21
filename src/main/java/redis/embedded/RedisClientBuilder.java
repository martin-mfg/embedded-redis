package redis.embedded;

import lombok.extern.slf4j.Slf4j;
import redis.embedded.common.CommonConstant;
import redis.embedded.exceptions.RedisBuildingException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
public class RedisClientBuilder {
    private final Collection<Integer> ports = new ArrayList<>();
    private File executable;
    private RedisExecProvider redisExecProvider = RedisCliExecProvider.defaultProvider();
    private Integer clusterReplicas = 0;

    public RedisClientBuilder redisExecProvider(RedisExecProvider redisExecProvider) {
        this.redisExecProvider = redisExecProvider;
        return this;
    }

    public RedisClientBuilder ports(Collection<Integer> ports) {
        this.ports.addAll(ports);
        return this;
    }

    public RedisClientBuilder clusterReplicas(Integer clusterReplicas) {
        this.clusterReplicas = clusterReplicas;
        return this;
    }

    public RedisClient build() {
        tryResolveConfAndExec();
        List<String> args = buildCommandArgs();
        return new RedisClient(args);
    }

    public void reset() {
        this.ports.clear();
        this.executable = null;
        clusterReplicas = 0;
    }


    private void tryResolveConfAndExec() {
        try {
            resolveConfAndExec();
        } catch (IOException e) {
            log.warn("Could not build client instance. exception: {}", e.getMessage(), e);
            throw new RedisBuildingException("Could not build client instance", e);
        }
    }


    private void resolveConfAndExec() throws IOException {
        try {
            executable = redisExecProvider.get();
        } catch (Exception e) {
            log.warn("Failed to resolve executable. exception: {}", e.getMessage(), e);
            throw new RedisBuildingException("Failed to resolve executable", e);
        }
    }

    private List<String> buildCommandArgs() {
        List<String> args = new ArrayList<>();
        args.add(executable.getAbsolutePath());

        args.add("--cluster");
        args.add("create");

        ports.forEach(port -> {
            String address = CommonConstant.DEFAULT_REDIS_HOST + CommonConstant.SEPARATOR_COLON + port;
            args.add(address);
        });

        if (clusterReplicas > 0) {
            args.add("--cluster-replicas");
            args.add(Integer.toString(clusterReplicas));
        }

        args.add("--cluster-yes");

        return args;
    }
}
