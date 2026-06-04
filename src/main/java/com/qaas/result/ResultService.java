package com.qaas.result;

import com.qaas.exception.NotFoundException;
import com.qaas.result.ResultDtos.ResultResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ResultService {
    private final TestResultRepository results;

    public ResultService(TestResultRepository results) {
        this.results = results;
    }

    @Transactional(readOnly = true)
    public List<ResultResponse> list() {
        return results.findAll().stream().map(ResultResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ResultResponse find(UUID id) {
        return ResultResponse.from(results.findById(id).orElseThrow(() -> new NotFoundException("Result not found")));
    }
}
