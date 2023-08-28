-- Henter ut alle klager som har et vedtak med et dokument som ikke har fritekst
select count(distinct v.id)
from klage k
         join vedtak v on k.vedtakid = v.id
         join dokument d on v.id = d.vedtakid
where length(trim(d.generertdokumentjson ->> 'fritekst')) < 1;

-- Henter ut alle avslagsvedtak som har et dokument som ikke har fritekst
-- I tillegg kan det legges til perioden som du vil sjekke for
select count(d.generertdokumentjson)
from vedtak v
         join dokument d on v.id = d.vedtakid
where length(trim(d.generertdokumentjson ->> 'fritekst')) < 1
  and v.vedtaktype = 'AVSLAG';
-- and Date(v.opprettet) >= '2023-05-01'
-- and Date(v.opprettet) <= '2023-08-31';

