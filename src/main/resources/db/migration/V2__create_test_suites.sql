CREATE TABLE test_suites (
    id uuid NOT NULL PRIMARY KEY,
    project_id uuid NOT NULL,
    name varchar(160) NOT NULL,
    description varchar(2000),
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT fk_test_suites_project_id FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE INDEX idx_test_suites_project_id ON test_suites (project_id);

CREATE TABLE test_suite_memberships (
    id uuid NOT NULL PRIMARY KEY,
    suite_id uuid NOT NULL,
    test_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT uk_test_suite_memberships_suite_test UNIQUE (suite_id, test_id),
    CONSTRAINT fk_test_suite_memberships_suite_id FOREIGN KEY (suite_id) REFERENCES test_suites (id),
    CONSTRAINT fk_test_suite_memberships_test_id FOREIGN KEY (test_id) REFERENCES generated_tests (id)
);

CREATE INDEX idx_test_suite_memberships_suite_id ON test_suite_memberships (suite_id);

CREATE TABLE test_suite_runs (
    id uuid NOT NULL PRIMARY KEY,
    suite_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    total_tests integer NOT NULL,
    passed_tests integer NOT NULL,
    failed_tests integer NOT NULL,
    error_tests integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    started_at timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    CONSTRAINT fk_test_suite_runs_suite_id FOREIGN KEY (suite_id) REFERENCES test_suites (id)
);

CREATE INDEX idx_test_suite_runs_suite_id ON test_suite_runs (suite_id);

CREATE TABLE test_suite_run_items (
    id uuid NOT NULL PRIMARY KEY,
    run_id uuid NOT NULL,
    test_id uuid NOT NULL,
    execution_id uuid,
    test_name varchar(255) NOT NULL,
    status varchar(20) NOT NULL,
    error_message varchar(2000),
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT uk_test_suite_run_items_run_test UNIQUE (run_id, test_id),
    CONSTRAINT fk_test_suite_run_items_run_id FOREIGN KEY (run_id) REFERENCES test_suite_runs (id),
    CONSTRAINT fk_test_suite_run_items_test_id FOREIGN KEY (test_id) REFERENCES generated_tests (id),
    CONSTRAINT fk_test_suite_run_items_execution_id FOREIGN KEY (execution_id) REFERENCES executions (id)
);

CREATE INDEX idx_test_suite_run_items_run_id ON test_suite_run_items (run_id);
