-- Adiciona a tabela de deletar, a endsAt é para quando se encerra a task
ALTER TABLE task
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
