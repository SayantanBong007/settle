package dev.settle.account.config;

import dev.settle.account.controller.AccountGrpcController;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Starts the embedded gRPC Netty server on settle.grpc.port (9090) using Java 21 Virtual Threads,
 * and shuts it down gracefully when Spring Boot stops.
 */
@Component
public class GrpcServerConfig implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);

    private final AccountGrpcController accountGrpcController;
    private final int port;
    private Server server;

    public GrpcServerConfig(
            AccountGrpcController accountGrpcController,
            @Value("${settle.grpc.port:9090}") int port) {
        this.accountGrpcController = accountGrpcController;
        this.port = port;
    }

    @Override
    public void start() {
        if (port <= 0) {
            return; // Disabled when port=0 in tests
        }
        try {
            this.server = ServerBuilder.forPort(port)
                    // Java 21 Virtual Threads: each gRPC request runs on a lightweight virtual thread!
                    .executor(Executors.newVirtualThreadPerTaskExecutor())
                    .addService(accountGrpcController)
                    .build()
                    .start();
            log.info("gRPC Server started on port {} with Java 21 virtual threads", port);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (this.server != null) {
            this.server.shutdown();
            log.info("gRPC Server stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return this.server != null && !this.server.isShutdown();
    }
}
