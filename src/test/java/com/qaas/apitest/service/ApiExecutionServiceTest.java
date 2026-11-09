package com.qaas.apitest.service;

import com.qaas.apitest.entity.ApiEndpoint;
import com.qaas.apitest.repository.ApiEndpointRepository;
import com.qaas.execution.ExecutionStatus;
import com.qaas.execution.TestExecution;
import com.qaas.generator.entity.GeneratedTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExecutionServiceTest {

    @Test
    void apiTestsDoNotFollowRedirectsOutsideTheTargetOrigin() throws Exception {
        AtomicInteger redirectedRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "/destination");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/destination", exchange -> {
            redirectedRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/start";
            ApiEndpoint endpoint = new ApiEndpoint(UUID.randomUUID(), "GET", url, 302);
            ApiEndpointRepository endpoints = mock(ApiEndpointRepository.class);
            when(endpoints.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));
            GeneratedTest test = new GeneratedTest();
            test.setApiEndpointId(endpoint.getId());
            test.setType("api-smoke");
            ApiExecutionService service = new ApiExecutionService(endpoints);

            TestExecution environmentExecution = new TestExecution(test);
            service.execute(environmentExecution, test, null, url, null, null);
            assertThat(environmentExecution.getStatus()).isEqualTo(ExecutionStatus.PASSED);
            assertThat(redirectedRequests).hasValue(0);

            TestExecution defaultExecution = new TestExecution(test);
            service.execute(defaultExecution, test, null);
            assertThat(defaultExecution.getStatus()).isEqualTo(ExecutionStatus.PASSED);
            assertThat(redirectedRequests).hasValue(0);
        } finally {
            server.stop(0);
        }
    }
}
