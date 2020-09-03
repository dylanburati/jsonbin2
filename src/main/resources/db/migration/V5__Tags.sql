ALTER TABLE "conversation_user" ADD COLUMN
    "is_owner" BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE "conversation_user" ALTER COLUMN
    "is_owner" DROP DEFAULT;

CREATE TABLE "conversation_tag" (
    "conversation_id" VARCHAR(36) REFERENCES "conversation" ("id") ON DELETE CASCADE,
    "tag" VARCHAR(128) NOT NULL,
    PRIMARY KEY ("conversation_id", "tag")
);

INSERT INTO "conversation_tag" ("conversation_id", "tag")
    SELECT "id", 'chat' FROM "conversation";
