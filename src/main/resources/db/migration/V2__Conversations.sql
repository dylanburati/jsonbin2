CREATE TABLE "conversation" (
    "id" VARCHAR(36) NOT NULL,
    "title" VARCHAR(128) NOT NULL,
    PRIMARY KEY ("id"),
    UNIQUE ("title")
);

CREATE TABLE "conversation_user" (
    "id" VARCHAR(36) NOT NULL,
    "conversation_id" VARCHAR(36) REFERENCES "conversation" ("id") ON DELETE CASCADE,
    "user_id" VARCHAR(36) REFERENCES "user" ("id") ON DELETE CASCADE,
    "nickname" VARCHAR(128) NOT NULL,
    PRIMARY KEY ("id"),
    UNIQUE ("conversation_id", "user_id")
);
