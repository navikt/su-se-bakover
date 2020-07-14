create table if not exists beregning
(
    id uuid
        primary key,
    opprettet timestamp with time zone
        not null,
    fom date
        not null,
    tom date
        not null,
    sats text
        not null,
    behandlingId uuid
        not null references behandling(id)
);

create table if not exists månedsberegning
(
    id uuid
        primary key,
    opprettet timestamp with time zone
        not null,
    fom date
        not null,
    tom date
        not null,
    grunnbeløp integer
        not null,
    sats text
        not null,
    beregningId uuid
        not null references beregning(id),
    beløp integer
        not null
);