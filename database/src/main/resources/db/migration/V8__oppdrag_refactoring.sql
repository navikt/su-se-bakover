drop table if exists oppdragslinje;

drop table if exists oppdrag;

create table if not exists oppdrag -- 1 til 1 med sak
(
    id        varchar(30) -- begrensning i oppdragssystemet
        primary key,
    opprettet timestamp with time zone
        not null,
    sakId     uuid
        not null references sak (id) unique
);

create table if not exists utbetaling
(
    id           varchar(30) -- begrensning i oppdragssystemet
        primary key,
    opprettet    timestamp with time zone
        not null,
    oppdragId    varchar(30) -- begrensning i oppdragssystemet
        not null references oppdrag (id),
    behandlingId uuid
        not null references behandling (id),
    simulering   jsonb
        null
);

create table if not exists utbetalingslinje
(
    id                        varchar(30) -- begrensning i oppdragssystemet
        primary key,
    opprettet                 timestamp with time zone
        not null,
    fom                       date
        not null,
    tom                       date
        not null,
    utbetalingId              varchar(30) -- begrensning i oppdragssystemet
        not null references utbetaling (id),
    forrigeUtbetalingslinjeId varchar(30) -- begrensning i oppdragssystemet
        null references utbetalingslinje (id),
    beløp                     int
        not null
);
