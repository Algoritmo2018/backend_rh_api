-- Test-only table used to check the shared persistence base classes (UUID v7, auditing, Money, enums)
CREATE TABLE test_samples (
    id             UUID PRIMARY KEY,
    name           VARCHAR(100)             NOT NULL,
    province       VARCHAR(30)              NOT NULL,
    price_amount   NUMERIC(19, 2),
    price_currency VARCHAR(3),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
