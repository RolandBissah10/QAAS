package com.qaas.execution;

import com.qaas.result.ResultDtos.ResultResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/executions")
public class ExecutionController {
    private final ExecutionService service;

    public ExecutionController(ExecutionService service) {
        this.service = service;
    }

    @PostMapping("/test/{id}")
    ResultResponse runTest(@PathVariable UUID id) {
        return service.runTest(id);
    }

    @PostMapping("/collection/{id}")
    ExecutionDtos.CollectionExecutionResponse runCollection(@PathVariable UUID id) {
        return service.runCollection(id);
    }
}
