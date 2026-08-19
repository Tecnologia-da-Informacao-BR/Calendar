CREATE TYPE task_priority AS ENUM ('low', 'medium', 'high');

ALTER TABLE task
    ADD COLUMN priority task_priority;