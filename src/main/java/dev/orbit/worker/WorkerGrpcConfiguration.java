package dev.orbit.worker;

import dev.orbit.grpc.proto.OrbitControlGrpc;
import dev.orbit.security.OrbitSecuritySettings;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import java.io.File;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "orbit.role", havingValue = "worker")
public class WorkerGrpcConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel orbitSchedulerChannel(WorkerProperties properties, OrbitSecuritySettings security)
            throws Exception {
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(
                properties.getSchedulerHost(), properties.getSchedulerGrpcPort());
        if (security.isProduction()) {
            builder.sslContext(GrpcSslContexts.forClient().trustManager(new File(security.getGrpcCa()))
                    .keyManager(new File(security.getGrpcCertificate()), new File(security.getGrpcPrivateKey())).build());
        } else {
            builder.usePlaintext();
        }
        builder.maxInboundMessageSize(4 * 1024 * 1024);
        return builder.build();
    }

    @Bean
    public OrbitControlGrpc.OrbitControlBlockingStub orbitSchedulerStub(
            ManagedChannel channel,
            @Value("${orbit.security.worker-token:local-dev-worker-token-change-me}") String workerToken) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + workerToken);
        return OrbitControlGrpc.newBlockingStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }
}
