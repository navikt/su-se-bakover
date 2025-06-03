select * from kontrollsamtale where innkallingsdato = '2025-03-01';

select distinct(status), count(status) from kontrollsamtale where frist = '2025-03-31' group by status;

select * from behandling_vedtak limit 10;

select id as vedtakId,opprettet,fraogmed,tilogmed,saksbehandler,attestant,vedtaktype,sakid from vedtak where vedtaktype in ('STANS_AV_YTELSE') AND fraogmed = '2025-04-01' and saksbehandler = 'srvsupstonad' ;

select   s.saksnummer,v.sakid, v.id as vedtakId, v.opprettet, v.fraogmed, v.tilogmed, v.saksbehandler, v.attestant, v.vedtaktype, r.begrunnelse
from vedtak v
join behandling_vedtak bv on v.id = bv.vedtakid
join revurdering r on r.id = bv.revurderingid
join sak s on s.id = v.sakId
where v.vedtaktype in ('STANS_AV_YTELSE') AND v.fraogmed = '2025-04-01' and v.saksbehandler != 'srvsupstonad';

select id as vedtakId,opprettet,fraogmed,tilogmed,saksbehandler,attestant,vedtaktype,sakid from vedtak where vedtaktype in ('GJENOPPTAK_AV_YTELSE') AND fraogmed = '2025-04-01'  order by opprettet ;

SELECT
  DATE_TRUNC('day',  opprettet)::date as dag,
  COUNT(*)
FROM vedtak
WHERE vedtaktype IN ('GJENOPPTAK_AV_YTELSE') AND fraogmed = '2025-04-01'
GROUP BY 1
ORDER BY 1;