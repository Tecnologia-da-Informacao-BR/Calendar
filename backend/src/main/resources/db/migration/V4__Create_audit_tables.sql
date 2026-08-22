CREATE TABLE audit_users(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY ,
    users_id VARCHAR(21) NOT NULL ,
    action VARCHAR(10) NOT NULL ,
    old_data JSONB,
    new_data JSONB,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE audit_tasks(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY ,
    task_id BIGINT NOT NULL,
    action VARCHAR(10) NOT NULL ,
    old_data JSONB,
    new_data JSONB,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP
);
