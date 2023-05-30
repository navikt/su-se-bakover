with gVerdiOgSak as (select distinct s.saksnummer,
                                     (jsonb_array_elements(beregning -> 'månedsberegninger') ->
                                      'benyttetGrunnbeløp')::integer as gverdi
                     from sak s
                              join behandling b on s.id = b.sakid
                     where status
                         in
                           ('BEREGNET_AVSLAG', 'BEREGNET_INNVILGET', 'SIMULERT', 'TIL_ATTESTERING_INNVILGET',
                            'TIL_ATTESTERING_AVSLAG',
                            'UNDERKJENT_INNVILGET', 'UNDERKJENT_AVSLAG')
                       and lukket is false)
select *
from gVerdiOgSak
where gverdi < 118620;


with gVerdiOgSak as (select distinct s.saksnummer,
                                     (b.periode ->>'fraOgMed')::date         as fraOgMed,
                                     (b.periode ->> 'tilOgMed' )::date        as tilOgMed,
                                     (jsonb_array_elements(beregning -> 'månedsberegninger') ->
                                      'benyttetGrunnbeløp')::integer as gverdi
                     from sak s
                              join revurdering b on s.id = b.sakid
                     where revurderingstype
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
where gverdi < 118620 and fraOgMed > '2023-05-01';