--function TRIGGER for audit users
CREATE OR REPLACE FUNCTION log_users_audit()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO audit_users (users_id, action, old_data, new_data)
        VALUES (NEW.id, 'INSERT', NULL, row_to_json(NEW)::jsonb);
RETURN NEW;

ELSIF (TG_OP = 'UPDATE') THEN
        -- Registra o log APENAS se algum valor realmente mudou
        IF (NEW IS DISTINCT FROM OLD) THEN
            INSERT INTO audit_users (users_id, action, old_data, new_data)
            VALUES (NEW.id, 'UPDATE', row_to_json(OLD)::jsonb, row_to_json(NEW)::jsonb);
END IF;
RETURN NEW;

ELSIF (TG_OP = 'DELETE') THEN
        INSERT INTO audit_users (users_id, action, old_data, new_data)
        VALUES (OLD.id, 'DELETE', row_to_json(OLD)::jsonb, NULL);
RETURN OLD;
END IF;

RETURN NULL;
END;
$$ LANGUAGE plpgsql;

--function TRIGGER for audit tasks
   CREATE OR REPLACE FUNCTION log_tasks_audit()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO audit_tasks (task_id, action, old_data, new_data)
        VALUES (NEW.id, 'INSERT', NULL, row_to_json(NEW)::jsonb);
RETURN NEW;

ELSIF (TG_OP = 'UPDATE') THEN
        -- Registra o log APENAS se algum valor da task realmente mudou
        IF (NEW IS DISTINCT FROM OLD) THEN
            INSERT INTO audit_tasks (task_id, action, old_data, new_data)
            VALUES (NEW.id, 'UPDATE', row_to_json(OLD)::jsonb, row_to_json(NEW)::jsonb);
END IF;
RETURN NEW;

ELSIF (TG_OP = 'DELETE') THEN
        INSERT INTO audit_tasks (task_id, action, old_data, new_data)
        VALUES (OLD.id, 'DELETE', row_to_json(OLD)::jsonb, NULL);
RETURN OLD;
END IF;

RETURN NULL;
END;
$$ LANGUAGE plpgsql;