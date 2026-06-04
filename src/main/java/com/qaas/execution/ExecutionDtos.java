package com.qaas.execution;

import com.qaas.result.ResultDtos.ResultResponse;

import java.util.List;

public final class ExecutionDtos {
    private ExecutionDtos() {
    }

    public record CollectionExecutionResponse(List<ResultResponse> results, long passed, long failed) {
    }
}
