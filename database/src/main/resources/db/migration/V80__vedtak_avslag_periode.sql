update vedtak v
set fraogmed = (select (b.stønadsperiode -> 'periode' ->> 'fraOgMed')::date
                from behandling b
                         inner join behandling_vedtak bv on bv.søknadsbehandlingid = b.id
                where v.id = bv.vedtakid)
where v.fraogmed is null;

update vedtak v
set tilogmed = (select (b.stønadsperiode -> 'periode' ->> 'tilOgMed')::date
                from behandling b
                         inner join behandling_vedtak bv on bv.søknadsbehandlingid = b.id
                where v.id = bv.vedtakid)
where v.tilogmed is null;

alter table vedtak
    alter column fraogmed set not null;

alter table vedtak
    alter column tilogmed set not null;