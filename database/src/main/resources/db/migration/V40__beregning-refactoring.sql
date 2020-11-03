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

--Patch existing fradrag - setting values to those of parent beregning.
with existingBeregning as(select id, opprettet, fom, tom from beregning)
update fradrag set
    opprettet = existingBeregning.opprettet,
    fom = existingBeregning.fom,
    tom = existingBeregning.tom from existingBeregning
where existingBeregning.id = fradrag.beregningid;

alter table fradrag alter column fom set not null;
alter table fradrag alter column fom drop default;

alter table fradrag alter column tom set not null;
alter table fradrag alter column tom drop default;

alter table fradrag alter column opprettet set not null;
alter table fradrag alter column opprettet drop default;

alter table fradrag drop column if exists inntektDelerAvPeriode;

drop table if exists månedsberegning;

alter table fradrag alter column beløp type decimal;
alter table utbetalingslinje alter column beløp type decimal;
