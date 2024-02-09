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



/*
  Dersom det skulle være behov for å hente ut fnr for de vedtakene som ikke har fritekst,
  og samtidig er interessert i antall vedtak som er fattet i perioden, kan følgende spørring brukes:

  Får tilbake 3 kolonner:
    - antallAvslagsvedtakUtenFritekstForPeriode
    - antallAvslagsvedtakFattetForPeriode
    - fnr (en rad for hvert fnr - andre kolonner skal ha samme resultatet)
 */
with periode (fom, tom) as (values ('2024-01-01'::date, '2024-01-31'::date)),
     antallAvslagsvedtakUtenFritekstForPeriode as (select count(d.generertdokumentjson) as antallAvslagsvedtakUtenFritekstForPeriode
                                            from vedtak v
                                                     join dokument d on v.id = d.vedtakid
                                                     join periode on true
                                            where length(trim(d.generertdokumentjson ->> 'fritekst')) < 1
                                              and v.vedtaktype = 'AVSLAG'
                                              and Date(v.opprettet) >= periode.fom
                                              and Date(v.opprettet) <= periode.tom),
     antallAvslagsvedtakFattetForPeriode as (select count(*) as antallAvslagsvedtakFattetForPeriode
                                      from vedtak
                                               join periode on true
                                      where vedtaktype = 'AVSLAG'
                                        and Date(opprettet) >= periode.fom
                                        and Date(opprettet) <= periode.tom),
     fnrForVedtakUtenFritekstForPeriode as (select s.fnr
                                            from vedtak v
                                                     join dokument d on v.id = d.vedtakid
                                                     join sak s on v.sakid = s.id
                                                     join periode on true
                                            where length(trim(d.generertdokumentjson ->> 'fritekst')) < 1
                                              and v.vedtaktype = 'AVSLAG'
                                              and Date(v.opprettet) >= periode.fom
                                              and Date(v.opprettet) <= periode.tom)
select *
from antallAvslagsvedtakUtenFritekstForPeriode,
     antallAvslagsvedtakFattetForPeriode,
     fnrForVedtakUtenFritekstForPeriode;