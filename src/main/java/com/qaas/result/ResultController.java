package com.qaas.result;

import com.qaas.result.ResultDtos.ResultResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/results")
public class ResultController {
    private final ResultService service;

    public ResultController(ResultService service) {
        this.service = service;
    }

    @GetMapping
    List<ResultResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    ResultResponse get(@PathVariable UUID id) {
        return service.find(id);
    }
}
