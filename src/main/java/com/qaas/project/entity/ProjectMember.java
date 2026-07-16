package com.qaas.project.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_members",
       uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
public class ProjectMember {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_id", columnDefinition = "uuid", nullable = false)
    private UUID projectId;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String memberRole;   // VIEWER or TESTER

    @Column(nullable = false)
    private OffsetDateTime joinedAt;

    public ProjectMember() { this.id = UUID.randomUUID(); this.joinedAt = OffsetDateTime.now(); }

    public UUID getId()                      { return id; }
    public UUID getProjectId()               { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public UUID getUserId()                  { return userId; }
    public void setUserId(UUID userId)       { this.userId = userId; }
    public String getMemberRole()            { return memberRole; }
    public void setMemberRole(String r)      { this.memberRole = r; }
    public OffsetDateTime getJoinedAt()      { return joinedAt; }
}