package com.qaas.discovery.repository;

import com.qaas.discovery.entity.UIElement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UIElementRepository extends JpaRepository<UIElement, UUID> {
    List<UIElement> findByPageId(UUID pageId);
}
