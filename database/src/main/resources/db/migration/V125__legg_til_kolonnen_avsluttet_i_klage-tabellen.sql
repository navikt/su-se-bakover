alter table klage
    add column if not exists avsluttet jsonb default null;
