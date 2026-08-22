-- V3 - enforce email uniqueness at the database level

-- Signup only checked for an existing email at the application layer
-- (UserService#createUser), which has a race condition: two concurrent
-- signup requests with the same email can both pass that check before
-- either INSERT commits, creating two users with the same email.

ALTER TABLE users
    ADD CONSTRAINT uq_users_email UNIQUE (email);
