create table if not exists stoppbehandling
(
    id            uuid
        primary key,
    opprettet     timestamp with time zone
        not null,
    sakId         uuid
        not null references sak (id),
    status        text
        not null,
    utbetaling    text
        not null references utbetaling (id),
    stoppÅrsak    text
        not null,
    saksbehandler text
        not null
);
