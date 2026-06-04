package com.qaas.collection;

import com.qaas.collection.CollectionDtos.CollectionRequest;
import com.qaas.collection.CollectionDtos.CollectionResponse;
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
@RequestMapping("/api/collections")
public class TestCollectionController {
    private final TestCollectionService service;

    public TestCollectionController(TestCollectionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CollectionResponse create(@Valid @RequestBody CollectionRequest request) {
        return service.create(request);
    }

    @GetMapping
    List<CollectionResponse> list() {
        return service.list();
    }

    @PutMapping("/{id}")
    CollectionResponse update(@PathVariable UUID id, @Valid @RequestBody CollectionRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
