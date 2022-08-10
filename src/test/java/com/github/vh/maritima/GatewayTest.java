package com.github.vh.maritima;

import com.github.vh.protobuf.TestServiceGrpc;
import com.google.protobuf.Empty;
import com.linecorp.armeria.common.*;
import com.linecorp.armeria.common.grpc.GrpcSerializationFormats;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.grpc.GrpcService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
public class GatewayTest {

    static class TestService extends TestServiceGrpc.TestServiceImplBase {

        @Override
        public void test(Empty request, StreamObserver<Empty> responseObserver) {
            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        }

        @Override
        public void test2(Empty request, StreamObserver<Empty> responseObserver) {
            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    private static final HttpService service = GrpcService.builder()
            .supportedSerializationFormats(GrpcSerializationFormats.values())
            .enableUnframedRequests(true)
            .addService(new TestService())
            .build()
            .decorate(Maritima.createGateway());

    @Test
    void test1() throws Exception {
        HttpRequest req = HttpRequest.of(HttpMethod.POST, "/TestService/Test", MediaType.JSON_UTF_8, "{}");

        HttpResponse res = service.serve(ServiceRequestContext.of(req), req);

        AggregatedHttpResponse aggregatedRes = res.aggregate().join();
        Assertions.assertEquals(HttpStatus.OK.code(), aggregatedRes.status().code());
    }

    @Test
    void test2() throws Exception {
        HttpRequest req = HttpRequest.of(HttpMethod.POST, "/TestService/Test2", MediaType.JSON_UTF_8, "{}");

        HttpResponse res = service.serve(ServiceRequestContext.of(req), req);

        AggregatedHttpResponse aggregatedRes = res.aggregate().join();
        Assertions.assertEquals(HttpStatus.UNAUTHORIZED.code(), aggregatedRes.status().code());
    }
}
