create table if not exists oppdrag
(
    id uuid
        primary key,
    opprettet timestamp with time zone
        not null,
    sakId uuid
        not null references sak(id),
    behandlingId uuid
        not null references behandling(id),
    simulering jsonb
        null
);
create table if not exists oppdragslinje
(
    id uuid
        primary key,
    opprettet timestamp with time zone
        not null,
    fom date
        not null,
    tom date
        not null,
    oppdragId uuid
        not null references oppdrag(id),
    forrigeOppdragslinjeId uuid
        null references oppdragslinje (id),
    beløp int
        not null
);
