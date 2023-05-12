CREATE TABLE IF NOT EXISTS
    skatt
(
    id            uuid                     not null primary key,
    sakId         uuid references sak (id) not null,
    fnr           text                     not null,
    erEps         boolean                  not null,
    opprettet     timestamp with time zone not null,
    saksbehandler text                     not null,
    årSpurtFor    jsonb                    not null,
    data          jsonb
);

ALTER TABLE
    behandling
    ADD COLUMN IF NOT EXISTS
        søkersSkatteId uuid references skatt (id) default null,
    ADD COLUMN IF NOT EXISTS
        epsSkatteId    uuid references skatt (id) default null