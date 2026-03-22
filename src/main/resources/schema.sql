CREATE TABLE IF NOT EXISTS conversation (
    id VARCHAR(100) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    provider VARCHAR(50),
    model VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);