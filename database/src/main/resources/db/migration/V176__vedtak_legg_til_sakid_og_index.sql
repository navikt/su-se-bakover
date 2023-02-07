alter table vedtak add column sakId uuid references sak(id);

with vedtakTilSak as (
    select
        bv.sakid as sakid,
        v.id as vedtakId
    from vedtak v
             join behandling_vedtak bv
                  on bv.vedtakid = v.id
)
update vedtak set sakid = (select sakid from vedtakTilSak where vedtakId = id);

alter table vedtak alter column sakId set not null;

create index if not exists vedtak_fraogmed_tilogmed_index on vedtak(fraogmed asc,tilogmed asc);

create index if not exists vedtak_sakid_index on vedtak(sakid);