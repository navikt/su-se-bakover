alter table if exists hendelse
    drop column if exists hendelsesnummer;

alter table hendelse
    add column hendelsesnummer
        bigint generated always as identity unique;
