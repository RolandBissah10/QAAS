CREATE TABLE test_environments (
    id uuid NOT NULL PRIMARY KEY,
    project_id uuid NOT NULL,
    name varchar(100) NOT NULL,
    base_url varchar(2048) NOT NULL,
    auth_username varchar(255),
    auth_password varchar(2048),
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT uk_test_environments_project_name UNIQUE (project_id, name),
    CONSTRAINT fk_test_environments_project_id FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE INDEX idx_test_environments_project_id ON test_environments (project_id);

ALTER TABLE test_suite_runs
    ADD COLUMN environment_id uuid,
    ADD COLUMN environment_name varchar(100),
    ADD COLUMN environment_base_url varchar(2048),
    ADD COLUMN environment_auth_username varchar(255),
    ADD COLUMN environment_auth_password varchar(2048);
