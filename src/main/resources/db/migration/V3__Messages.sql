CREATE TABLE message (
    "id" VARCHAR(36) NOT NULL,
    "sender_id" VARCHAR(36) NOT NULL REFERENCES "conversation_user" ("id") ON DELETE CASCADE,
    "time" TIMESTAMP NOT NULL,
    "target" TEXT NOT NULL,
    "content" JSONB NOT NULL,
    PRIMARY KEY ("id")
)
