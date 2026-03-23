CREATE TABLE IF NOT EXISTS conversation (
    id VARCHAR(100) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS conversation_message (
    id IDENTITY PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    content CLOB NOT NULL,
    provider VARCHAR(50),
    model VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_conversation_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation(id)
);

CREATE INDEX IF NOT EXISTS conversation_message_conversation_created_idx
    ON conversation_message (conversation_id, created_at, id);

CREATE TABLE IF NOT EXISTS message_entry (
    id VARCHAR(100) PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL,
    query_language VARCHAR(50),
    prompt CLOB NOT NULL,
    prompted_at TIMESTAMP NOT NULL,
    response CLOB,
    responded_at TIMESTAMP,
    CONSTRAINT fk_message_entry_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation(id)
);

CREATE INDEX IF NOT EXISTS message_entry_conversation_prompted_idx
    ON message_entry (conversation_id, prompted_at, id);
