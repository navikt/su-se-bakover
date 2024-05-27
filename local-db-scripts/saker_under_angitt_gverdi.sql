with gVerdiOgSak as (select distinct s.saksnummer,
                                     status,
                                     (b.stønadsperiode -> 'periode' ->>'fraOgMed')::date         as fraOgMed,
                                     (b.stønadsperiode->'periode' ->> 'tilOgMed' )::date        as tilOgMed,
                                     (jsonb_array_elements(beregning -> 'månedsberegninger') ->
                                      'benyttetGrunnbeløp')::integer as gverdi
                     from sak s
                              join behandling b on s.id = b.sakid
                     where status
                         in
                           ('BEREGNET_AVSLAG', 'BEREGNET_INNVILGET', 'SIMULERT', 'TIL_ATTESTERING_INNVILGET',
                            'TIL_ATTESTERING_AVSLAG',
                            'UNDERKJENT_INNVILGET', 'UNDERKJENT_AVSLAG')
                       and b.lukket is false)
select *
from gVerdiOgSak
where gverdi < 118621 and tilOgMed >= '2024-05-01' order by saksnummer;


with gVerdiOgSak as (select distinct s.saksnummer,
                                     r.revurderingstype,
                                     (r.periode ->>'fraOgMed')::date         as fraOgMed,
                                     (r.periode ->> 'tilOgMed' )::date        as tilOgMed,
                                     (jsonb_array_elements(r.beregning -> 'månedsberegninger') ->
                                      'benyttetGrunnbeløp')::integer as gverdi
                     from sak s
                              join revurdering r on s.id = r.sakid
                     where r.avsluttet is null and r.revurderingstype
                         in
                                                   ('BEREGNET_OPPHØRT',
                                                    'BEREGNET_INNVILGET',
                                                    'SIMULERT_INNVILGET',
                                                    'SIMULERT_OPPHØRT',
                                                    'TIL_ATTESTERING_INNVILGET',
                                                    'TIL_ATTESTERING_OPPHØRT',
                                                    'UNDERKJENT_INNVILGET',
                                                    'UNDERKJENT_AVSLAG'))
select *
from gVerdiOgSak
where gverdi < 118621 and tilogmed >= '2024-05-01' order by saksnummer;