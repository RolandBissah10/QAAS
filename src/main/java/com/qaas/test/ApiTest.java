package com.qaas.test;

import com.qaas.common.BaseEntity;
import com.qaas.environment.EnvironmentConfig;
import com.qaas.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "tests")
public class ApiTest extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(optional = false)
    @JoinColumn(name = "environment_id")
    private EnvironmentConfig environment;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestMethod method;

    @Column(nullable = false)
    private String endpoint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> headers = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_body", columnDefinition = "jsonb")
    private Map<String, Object> requestBody = new LinkedHashMap<>();

    @Column(name = "expected_status", nullable = false)
    private int expectedStatusCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_response", columnDefinition = "jsonb")
    private Map<String, Object> expectedResponse = new LinkedHashMap<>();

    protected ApiTest() {
    }

    public ApiTest(Project project, EnvironmentConfig environment, String name, RequestMethod method, String endpoint,
                   Map<String, Object> headers, Map<String, Object> requestBody, int expectedStatusCode,
                   Map<String, Object> expectedResponse) {
        update(project, environment, name, method, endpoint, headers, requestBody, expectedStatusCode, expectedResponse);
    }

    public void update(Project project, EnvironmentConfig environment, String name, RequestMethod method, String endpoint,
                       Map<String, Object> headers, Map<String, Object> requestBody, int expectedStatusCode,
                       Map<String, Object> expectedResponse) {
        this.project = project;
        this.environment = environment;
        this.name = name;
        this.method = method;
        this.endpoint = endpoint;
        this.headers = headers;
        this.requestBody = requestBody;
        this.expectedStatusCode = expectedStatusCode;
        this.expectedResponse = expectedResponse;
    }

    public Project getProject() {
        return project;
    }

    public EnvironmentConfig getEnvironment() {
        return environment;
    }

    public String getName() {
        return name;
    }

    public RequestMethod getMethod() {
        return method;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public Map<String, Object> getRequestBody() {
        return requestBody;
    }

    public int getExpectedStatusCode() {
        return expectedStatusCode;
    }

    public Map<String, Object> getExpectedResponse() {
        return expectedResponse;
    }
}
