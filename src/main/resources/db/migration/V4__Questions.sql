CREATE TABLE "question_source" (
    "id" VARCHAR(36) NOT NULL,
    "title" VARCHAR(128) NOT NULL,
    "created_at" TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "question" (
    "id" VARCHAR(36) NOT NULL,
    "source_id" VARCHAR(36) NOT NULL REFERENCES "question_source" ("id") ON DELETE CASCADE,
    "type" TEXT NOT NULL,
    "data" JSONB NOT NULL,
    PRIMARY KEY ("id")
)
