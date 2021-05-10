ALTER TABLE revurdering
    ADD COLUMN IF NOT EXISTS
        informasjonSomRevurderes jsonb not null default '{}'::jsonb;

ALTER TABLE revurdering
    ALTER COLUMN informasjonSomRevurderes
        DROP DEFAULT;
