-- Client-portal users belong to an existing client company (clients are never deleted)
ALTER TABLE users
    ADD CONSTRAINT fk_users_client FOREIGN KEY (client_id) REFERENCES clients (id);
