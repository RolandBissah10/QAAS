package com.qaas.collection;

import com.qaas.collection.CollectionDtos.CollectionRequest;
import com.qaas.collection.CollectionDtos.CollectionResponse;
import com.qaas.exception.NotFoundException;
import com.qaas.project.ProjectService;
import com.qaas.test.ApiTest;
import com.qaas.test.ApiTestService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TestCollectionService {
    private final TestCollectionRepository collections;
    private final ProjectService projects;
    private final ApiTestService tests;

    public TestCollectionService(TestCollectionRepository collections, ProjectService projects, ApiTestService tests) {
        this.collections = collections;
        this.projects = projects;
        this.tests = tests;
    }

    @Transactional
    public CollectionResponse create(CollectionRequest request) {
        return CollectionResponse.from(collections.save(new TestCollection(
                projects.get(request.projectId()),
                request.name(),
                request.description(),
                loadTests(request.testIds())
        )));
    }

    @Transactional(readOnly = true)
    public List<CollectionResponse> list() {
        return collections.findAll().stream().map(CollectionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TestCollection get(UUID id) {
        return collections.findById(id).orElseThrow(() -> new NotFoundException("Collection not found"));
    }

    @Transactional
    public CollectionResponse update(UUID id, CollectionRequest request) {
        TestCollection collection = get(id);
        collection.update(projects.get(request.projectId()), request.name(), request.description(), loadTests(request.testIds()));
        return CollectionResponse.from(collection);
    }

    @Transactional
    public void delete(UUID id) {
        collections.delete(get(id));
    }

    private Set<ApiTest> loadTests(Set<UUID> ids) {
        Set<ApiTest> loaded = new LinkedHashSet<>();
        if (ids != null) {
            ids.forEach(id -> loaded.add(tests.get(id)));
        }
        return loaded;
    }
}
