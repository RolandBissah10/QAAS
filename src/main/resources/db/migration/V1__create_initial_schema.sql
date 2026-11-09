CREATE TABLE users (
    id uuid NOT NULL PRIMARY KEY,
    created_at timestamp(6) with time zone NOT NULL,
    email varchar(255) NOT NULL,
    password varchar(255) NOT NULL,
    role varchar(255) NOT NULL,
    display_name varchar(255),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE projects (
    id uuid NOT NULL PRIMARY KEY,
    name varchar(255) NOT NULL,
    description varchar(2000),
    owner_id uuid,
    base_url varchar(2048),
    created_at timestamp(6) with time zone,
    schedule_expression varchar(120),
    schedule_enabled boolean DEFAULT false NOT NULL,
    last_scheduled_at timestamp(6) with time zone
);

CREATE TABLE project_members (
    id uuid NOT NULL PRIMARY KEY,
    project_id uuid NOT NULL,
    user_id uuid NOT NULL,
    member_role varchar(255) NOT NULL,
    joined_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT uk_project_members_project_id_user_id UNIQUE (project_id, user_id)
);

CREATE TABLE analyses (
    id uuid NOT NULL PRIMARY KEY,
    project_id uuid,
    triggered_by_user_id uuid,
    url varchar(255) NOT NULL,
    status varchar(255),
    started_at timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    deep_test boolean DEFAULT false NOT NULL
);

CREATE TABLE pages (
    id uuid NOT NULL PRIMARY KEY,
    analysis_id uuid,
    url varchar(255) NOT NULL,
    title varchar(255),
    page_type varchar(255),
    discovered_at timestamp(6) with time zone
);

CREATE TABLE generated_tests (
    id uuid NOT NULL PRIMARY KEY,
    page_id uuid,
    analysis_id uuid,
    api_endpoint_id uuid,
    name varchar(255),
    type varchar(255),
    status varchar(255),
    target_url varchar(255),
    script_json text
);

CREATE TABLE executions (
    id uuid NOT NULL PRIMARY KEY,
    test_id uuid NOT NULL,
    status varchar(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    started_at timestamp(6) with time zone NOT NULL,
    completed_at timestamp(6) with time zone,
    error_message varchar(2000),
    CONSTRAINT fk_executions_test_id FOREIGN KEY (test_id) REFERENCES generated_tests (id)
);

CREATE TABLE bugs (
    id uuid NOT NULL PRIMARY KEY,
    execution_id uuid NOT NULL,
    analysis_id uuid NOT NULL,
    title varchar(255) NOT NULL,
    description varchar(4000),
    severity varchar(255) NOT NULL,
    status varchar(255) NOT NULL,
    detected_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE reports (
    id uuid NOT NULL PRIMARY KEY,
    analysis_id uuid NOT NULL,
    format varchar(255) NOT NULL,
    file_path varchar(255),
    generated_at timestamp(6) with time zone NOT NULL,
    quality_score integer,
    total_tests integer,
    passed_tests integer,
    failed_tests integer,
    bug_count integer,
    pages_discovered integer
);

CREATE TABLE screenshots (
    id uuid NOT NULL PRIMARY KEY,
    analysis_id uuid NOT NULL,
    path varchar(255),
    captured_at timestamp(6) with time zone
);

CREATE TABLE ui_elements (
    id uuid NOT NULL PRIMARY KEY,
    page_id uuid,
    element_type varchar(255),
    selector varchar(2000),
    label varchar(255)
);

CREATE TABLE deep_findings (
    id uuid NOT NULL PRIMARY KEY,
    analysis_id uuid NOT NULL,
    category varchar(255) NOT NULL,
    severity varchar(255) NOT NULL,
    title varchar(255) NOT NULL,
    description varchar(4000),
    page_url varchar(2048),
    detected_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE api_endpoints (
    id uuid NOT NULL PRIMARY KEY,
    analysis_id uuid NOT NULL,
    url varchar(2048) NOT NULL,
    method varchar(10) NOT NULL,
    observed_status integer,
    requires_auth boolean NOT NULL,
    discovered_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE recordings (
    id uuid NOT NULL PRIMARY KEY,
    project_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    target_url varchar(2048),
    status varchar(255) NOT NULL,
    entry_count integer NOT NULL,
    api_endpoint_count integer NOT NULL,
    captured_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    error_message text
);

CREATE TABLE recording_entries (
    id uuid NOT NULL PRIMARY KEY,
    recording_id uuid NOT NULL,
    method varchar(255),
    url varchar(2048),
    status_code integer NOT NULL,
    request_headers text,
    request_body text,
    response_headers text,
    response_body text,
    time_taken double precision NOT NULL,
    started_at timestamp(6) with time zone,
    entry_index integer NOT NULL
);

CREATE TABLE project_settings (
    id uuid NOT NULL PRIMARY KEY,
    project_id uuid NOT NULL,
    max_pages integer NOT NULL,
    auth_url varchar(2048),
    auth_username varchar(255),
    auth_password varchar(2048),
    excluded_patterns varchar(4096),
    updated_at timestamp(6) with time zone,
    CONSTRAINT uk_project_settings_project_id UNIQUE (project_id)
);

CREATE TABLE refresh_tokens (
    id uuid NOT NULL PRIMARY KEY,
    user_id uuid NOT NULL,
    token varchar(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    revoked boolean NOT NULL,
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_project_members_user_id ON project_members (user_id);
CREATE INDEX idx_analyses_project_id ON analyses (project_id);
CREATE INDEX idx_pages_analysis_id ON pages (analysis_id);
CREATE INDEX idx_generated_tests_page_id ON generated_tests (page_id);
CREATE INDEX idx_generated_tests_analysis_id ON generated_tests (analysis_id);
CREATE INDEX idx_executions_test_id ON executions (test_id);
CREATE INDEX idx_bugs_analysis_id ON bugs (analysis_id);
CREATE INDEX idx_reports_analysis_id ON reports (analysis_id);
CREATE INDEX idx_screenshots_analysis_id ON screenshots (analysis_id);
CREATE INDEX idx_ui_elements_page_id ON ui_elements (page_id);
CREATE INDEX idx_deep_findings_analysis_id ON deep_findings (analysis_id);
CREATE INDEX idx_api_endpoints_analysis_id ON api_endpoints (analysis_id);
CREATE INDEX idx_recordings_project_id ON recordings (project_id);
CREATE INDEX idx_recording_entries_recording_id ON recording_entries (recording_id);
