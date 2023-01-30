alter table
    behandling
add column if not exists
    saksbehandling jsonb NOT NULL DEFAULT '{"historikk": []}'::jsonb;
