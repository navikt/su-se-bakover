---
name: flyway-migration
description: Databasemigrasjonsmønstre med Flyway og versjonerte SQL-skript
license: MIT
compatibility: Kotlin or Java with Flyway
metadata:
  domain: backend
  tags: database flyway sql migration
---

# Flyway Migration Skill

This skill provides patterns for managing database schema changes with Flyway.

## su-se-bakover profile

Read `.github/instructions/kotlin.instructions.md` first. Find the next free version
across both SQL and Kotlin/Java migration locations. Never modify a migration that
may have run. Use the repository's existing datasource and migration bootstrap;
this skill must not create a new Hikari setup.

## Migration File Naming

```text
db/migration/V{version}__{description}.sql
```

Examples:

- `V1__create_users_table.sql`
- `V2__add_email_to_users.sql`
- `V3__create_payments_table.sql`
- `V1.1__add_phone_to_users.sql`

## Creating Tables

```sql
-- V1__create_users_table.sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- Automatic updated_at trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
```

## Adding Columns

```sql
-- V2__add_phone_to_users.sql
ALTER TABLE users ADD COLUMN phone_number VARCHAR(20);
CREATE INDEX idx_users_phone ON users(phone_number);
```

## Creating Indexes

```sql
-- V3__add_user_indexes.sql
CREATE INDEX CONCURRENTLY idx_users_created_at ON users(created_at DESC);
CREATE INDEX CONCURRENTLY idx_users_name ON users(name);
```

## Adding Foreign Keys

```sql
-- V4__create_orders_table.sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
```

## Data Migrations

```sql
-- V5__set_default_status.sql
UPDATE users
SET status = 'active'
WHERE status IS NULL;

ALTER TABLE users
ALTER COLUMN status SET NOT NULL;
```

## Best Practices

1. **Never modify existing migrations**: Create a new migration instead
2. **Choose index strategy explicitly**: `CONCURRENTLY` requires a dedicated
   non-transactional migration and must follow the repo's Flyway configuration
3. **Test migrations on dev first**: Always test before production
4. **Keep migrations small**: One logical change per migration
5. **Use Flyway transactions**: Do not add manual `BEGIN`/`COMMIT` unless an
   established migration pattern requires it
6. **Add rollback notes**: Comment how to manually rollback if needed
