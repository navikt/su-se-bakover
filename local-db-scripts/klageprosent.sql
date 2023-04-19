-- Oversikt over klager (antall og klageprosen) som er iverksatt avvist eller oversendt
select count(v.vedtaktype),
       v.vedtaktype,
       (select count(*) from vedtak where vedtaktype = v.vedtaktype) as totalvedtak,
       ((count(v.vedtaktype)::float / (select count(*) from vedtak where vedtaktype = v.vedtaktype))) * 100 as prosent
from klage k
         join vedtak v on k.vedtakid = v.id
where k.type in (
                 'oversendt',
                 'iverksatt_avvist'
    )
group by v.vedtaktype;

-- Oversikt over prosentmessig klager vs. vedtak.
select count(v.*),
       (select count(*) from vedtak) as totalvedtak,
       (count(v)::float / (select count(*) from vedtak)) * 100 as prosent
from klage k
         join vedtak v on k.vedtakid = v.id
where k.type in (
                 'oversendt',
                 'iverksatt_avvist'
    )