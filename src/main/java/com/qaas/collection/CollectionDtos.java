package com.qaas.collection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public final class CollectionDtos {
    private CollectionDtos() {
    }

    public record CollectionRequest(@NotNull UUID projectId, @NotBlank String name, String description, Set<UUID> testIds) {
    }

    public record CollectionResponse(UUID id, UUID projectId, String name, String description, Set<UUID> testIds) {
        public static CollectionResponse from(TestCollection collection) {
            return new CollectionResponse(
                    collection.getId(),
                    collection.getProject().getId(),
                    collection.getName(),
                    collection.getDescription(),
                    collection.getTests().stream().map(test -> test.getId()).collect(java.util.stream.Collectors.toSet())
            );
        }
    }
}
