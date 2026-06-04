package com.qaas.environment;

import com.qaas.environment.EnvironmentDtos.EnvironmentRequest;
import com.qaas.environment.EnvironmentDtos.EnvironmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {
    private final EnvironmentService service;

    public EnvironmentController(EnvironmentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EnvironmentResponse create(@Valid @RequestBody EnvironmentRequest request) {
        return service.create(request);
    }

    @GetMapping
    List<EnvironmentResponse> list() {
        return service.list();
    }

    @PutMapping("/{id}")
    EnvironmentResponse update(@PathVariable UUID id, @Valid @RequestBody EnvironmentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
