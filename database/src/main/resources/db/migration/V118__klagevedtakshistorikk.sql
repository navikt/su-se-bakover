alter table klage
    add column if not exists klagevedtakshistorikk jsonb not null default '[]'::jsonb