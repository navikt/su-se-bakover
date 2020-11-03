alter table fradrag
    add column if not exists
        fom date
            default null;

alter table fradrag
    add column if not exists
        tom date
            default null;

alter table fradrag
    add column if not exists
        opprettet timestamp with time zone
            default null;

alter table fradrag drop column if exists inntektDelerAvPeriode;

DROP TABLE IF EXISTS månedsberegning;
