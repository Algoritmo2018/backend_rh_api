-- Client companies (the companies FEH OIRAM recruits for). Never deleted: deactivated (status).
-- The company does not charge fees to clients: there is no billing data here (decision of stage 4).
CREATE TABLE clients (
    id         UUID PRIMARY KEY,
    name       VARCHAR(120)             NOT NULL,
    sector     VARCHAR(20)              NOT NULL,
    -- Angolan tax identification number (9 to 14 digits, like the frontend)
    nif        VARCHAR(14)              NOT NULL,
    province   VARCHAR(30)              NOT NULL,
    city       VARCHAR(80)              NOT NULL,
    address    VARCHAR(200)             NOT NULL,
    website    VARCHAR(200),
    status     VARCHAR(10)              NOT NULL DEFAULT 'ACTIVE',
    notes      VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uk_clients_nif UNIQUE (nif),
    CONSTRAINT fk_clients_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_clients_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_clients_nif CHECK (nif ~ '^[0-9]{9,14}$'),
    CONSTRAINT ck_clients_sector CHECK (sector IN ('OIL_GAS', 'LOGISTICS', 'CONSTRUCTION', 'SERVICES')),
    CONSTRAINT ck_clients_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_clients_name ON clients (lower(name));

-- People to talk to at a client company (personal data: name, role, email, phone)
CREATE TABLE client_contacts (
    id         UUID PRIMARY KEY,
    client_id  UUID                     NOT NULL,
    name       VARCHAR(120)             NOT NULL,
    role       VARCHAR(80)              NOT NULL,
    email      VARCHAR(160)             NOT NULL,
    phone      VARCHAR(16)              NOT NULL,
    is_primary BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_client_contacts_client FOREIGN KEY (client_id) REFERENCES clients (id) ON DELETE CASCADE,
    CONSTRAINT fk_client_contacts_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_client_contacts_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ix_client_contacts_client_id ON client_contacts (client_id);
-- At most one primary contact per client
CREATE UNIQUE INDEX uk_client_contacts_primary ON client_contacts (client_id) WHERE is_primary;
