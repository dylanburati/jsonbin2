CREATE TABLE "user" (
    "id" VARCHAR(36) NOT NULL,
    "username" VARCHAR(128) NOT NULL,
    "auth_type" TEXT NOT NULL,
    "password" TEXT,
    PRIMARY KEY ("id"),
    UNIQUE ("username"),
    CHECK ("auth_type" IN ('NONE', 'BCRYPT'))
)
