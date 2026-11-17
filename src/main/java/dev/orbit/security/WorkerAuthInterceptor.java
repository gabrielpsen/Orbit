package dev.orbit.security;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLSession;
import org.springframework.stereotype.Component;

@Component
public class WorkerAuthInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    public static final Context.Key<String> AUTHENTICATED_WORKER_ID = Context.key("orbit-authenticated-worker-id");
    private static final String WORKER_SAN_PREFIX = "urn:orbit:worker:";
    private final OrbitSecuritySettings settings;

    public WorkerAuthInterceptor(OrbitSecuritySettings settings) { this.settings = settings; }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
            Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if (!settings.isValidWorkerAuthorization(headers.get(AUTHORIZATION))) {
            call.close(Status.UNAUTHENTICATED.withDescription("Valid worker bearer token required"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
        String workerId = null;
        if (settings.isProduction()) {
            workerId = workerIdFromClientCertificate(call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION));
            if (workerId == null) {
                call.close(Status.UNAUTHENTICATED.withDescription("A client certificate with the Orbit worker URI SAN is required"), new Metadata());
                return new ServerCall.Listener<>() { };
            }
        }
        Context context = Context.current();
        if (workerId != null) context = context.withValue(AUTHENTICATED_WORKER_ID, workerId);
        return Contexts.interceptCall(context, call, headers, next);
    }

    private String workerIdFromClientCertificate(SSLSession session) {
        if (session == null) return null;
        try {
            Certificate[] certificates = session.getPeerCertificates();
            if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate certificate)) return null;
            Collection<List<?>> names = certificate.getSubjectAlternativeNames();
            if (names == null) return null;
            for (List<?> name : names) {
                if (name.size() >= 2 && Integer.valueOf(6).equals(name.get(0)) && name.get(1) instanceof String uri
                        && uri.startsWith(WORKER_SAN_PREFIX)) {
                    String workerId = uri.substring(WORKER_SAN_PREFIX.length());
                    return workerId.matches("[A-Za-z0-9._-]{1,120}") ? workerId : null;
                }
            }
            return null;
        } catch (Exception exception) {
            return null;
        }
    }
}
