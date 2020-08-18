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
    endringskode text
        not null,
    simulering jsonb
        null,
    utbetalingsfrekvens text
        not null,
    oppdragGjelder text
        not null,
    saksbehandler text
        not null,
    attestant text
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
    endringskode text
        not null,
    oppdragId uuid
        not null references oppdrag(id),
    refOppdragslinjeId uuid
        null references oppdragslinje(id),
    refSakId uuid
        not null references sak(id),
    beløp int
        not null,
    klassekode text
        not null,
    status text
        null,
    statusFom date
        null,
    beregningsfrekvens text
        not null
);



