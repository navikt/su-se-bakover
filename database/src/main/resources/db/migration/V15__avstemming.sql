create table if not exists avstemming
(
    id uuid
        primary key,
    opprettet timestamp with time zone
        not null,
    fom
        timestamp with time zone,
    tom
        timestamp with time zone,
    utbetalinger
        jsonb,
    avstemmingXmlRequest
        text
);

alter table utbetaling
    add column if not exists
        avstemmingId uuid;