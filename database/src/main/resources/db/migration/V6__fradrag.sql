create table if not exists fradrag
(
    id uuid
        primary key,
    beregningId uuid
        not null references beregning(id),
    fradragstype text
        not null,
    beløp int
        not null,
    beskrivelse text
);

alter table månedsberegning
    add column if not exists
        fradrag int not null
            default 0;

alter table månedsberegning
    alter column fradrag drop default;
