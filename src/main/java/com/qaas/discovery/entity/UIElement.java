package com.qaas.discovery.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "ui_elements")
public class UIElement {
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "uuid")
    private UUID pageId;

    @Column
    private String elementType;

    @Column(length = 2000)
    private String selector;

    @Column
    private String label;

    public UIElement() { this.id = UUID.randomUUID(); }
    public UUID getId() { return id; }
    public UUID getPageId() { return pageId; }
    public void setPageId(UUID pageId) { this.pageId = pageId; }
    public String getElementType() { return elementType; }
    public void setElementType(String elementType) { this.elementType = elementType; }
    public String getSelector() { return selector; }
    public void setSelector(String selector) { this.selector = selector; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
