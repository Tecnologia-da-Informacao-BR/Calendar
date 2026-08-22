--TRIGGER table users
CREATE TRIGGER users_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON users
    FOR EACH ROW EXECUTE FUNCTION log_users_audit();

--TRIGGER table tasks
CREATE TRIGGER tasks_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON task
    FOR EACH ROW EXECUTE FUNCTION log_tasks_audit();