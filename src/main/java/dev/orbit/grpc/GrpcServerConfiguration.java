package dev.orbit.grpc;

import dev.orbit.security.OrbitSecuritySettings;
import dev.orbit.security.WorkerAuthInterceptor;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "orbit.role", havingValue = "scheduler", matchIfMissing = true)
public class GrpcServerConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    public Server orbitGrpcServer(OrbitGrpcEndpoint endpoint, WorkerAuthInterceptor auth,
                                  OrbitSecuritySettings security,
                                  @Value("${orbit.grpc.port:9090}") int port) throws IOException {
        NettyServerBuilder builder = NettyServerBuilder.forPort(port).maxInboundMessageSize(4 * 1024 * 1024);
        if (security.isProduction()) {
            builder.sslContext(SslContextBuilder.forServer(new File(security.getGrpcCertificate()),
                            new File(security.getGrpcPrivateKey()))
                    .trustManager(new File(security.getGrpcCa()))
                    .clientAuth(ClientAuth.REQUIRE).build());
        }
        return builder.addService(ServerInterceptors.intercept(endpoint, auth)).build().start();
    }
}
