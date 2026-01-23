package io.adserve.orchestration.config;

import io.grpc.*;

public class TraceIdInterceptor implements ClientInterceptor {

    public static final Metadata.Key<String> TRACE_ID_KEY =
            Metadata.Key.of("trace-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> REQUEST_ID_KEY =
            Metadata.Key.of("request-id", Metadata.ASCII_STRING_MARSHALLER);

    private final String traceId;
    private final String requestId;

    public TraceIdInterceptor(String traceId, String requestId) {
        this.traceId = traceId;
        this.requestId = requestId;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(TRACE_ID_KEY, traceId);
                headers.put(REQUEST_ID_KEY, requestId);
                super.start(responseListener, headers);
            }
        };
    }
}
