with gVerdiOgSak as (
    select distinct
        s.saksnummer,
        b.id as behandlingId,
        status,
        (b.stønadsperiode -> 'periode' ->> 'fraOgMed')::date as fraOgMed,
        (b.stønadsperiode -> 'periode' ->> 'tilOgMed')::date as tilOgMed,
        (jsonb_array_elements(beregning -> 'månedsberegninger')) as månedsberegninger
    from sak s
             join behandling b on s.id = b.sakid
    where status in ('BEREGNET_AVSLAG', 'BEREGNET_INNVILGET', 'SIMULERT', 'TIL_ATTESTERING_INNVILGET', 'TIL_ATTESTERING_AVSLAG', 'UNDERKJENT_INNVILGET', 'UNDERKJENT_AVSLAG')
      and b.lukket is false
      and b.stønadsperiode -> 'periode' ->> 'tilOgMed' >= '2024-05-01'
)
select
    gVerdiOgSak.saksnummer,
    gVerdiOgSak.behandlingId,
    gVerdiOgSak.status,
    (månedsberegninger ->> 'benyttetGrunnbeløp')::integer as gVerdi,
    array_agg(jsonb_build_object('fraOgMed', (månedsberegninger -> 'periode' ->> 'fraOgMed'), 'tilOgMed', (månedsberegninger -> 'periode' ->> 'tilOgMed'))) as periode
from gVerdiOgSak
where (månedsberegninger ->> 'benyttetGrunnbeløp')::integer < 118621
  and (månedsberegninger -> 'periode' ->> 'tilOgMed') >= '2024-05-01'
group by
    gVerdiOgSak.saksnummer,
    gVerdiOgSak.behandlingId,
    gVerdiOgSak.status,
    (månedsberegninger ->> 'benyttetGrunnbeløp')::integer
order by saksnummer;


with gVerdiOgSak as (
    select distinct
        s.saksnummer,
        b.id as behandlingId,
        revurderingstype,
        (b.periode ->> 'fraOgMed')::date as fraOgMed,
        (b.periode ->> 'tilOgMed')::date as tilOgMed,
        (jsonb_array_elements(beregning -> 'månedsberegninger')) as månedsberegninger
    from sak s
             join revurdering b on s.id = b.sakid
    where revurderingstype in ('BEREGNET_OPPHØRT', 'BEREGNET_INNVILGET', 'SIMULERT_INNVILGET',  'SIMULERT_OPPHØRT', 'TIL_ATTESTERING_INNVILGET','TIL_ATTESTERING_OPPHØRT', 'UNDERKJENT_INNVILGET', 'UNDERKJENT_AVSLAG')
      and b.avsluttet is null
      and b.periode ->> 'tilOgMed' >= '2024-05-01'
)
select
    gVerdiOgSak.saksnummer,
    gVerdiOgSak.behandlingId,
    gVerdiOgSak.revurderingstype,
    (månedsberegninger ->> 'benyttetGrunnbeløp')::integer as gVerdi,
    array_agg(jsonb_build_object('fraOgMed', (månedsberegninger -> 'periode' ->> 'fraOgMed'), 'tilOgMed', (månedsberegninger -> 'periode' ->> 'tilOgMed'))) as periode
from gVerdiOgSak
where (månedsberegninger ->> 'benyttetGrunnbeløp')::integer < 118621
  and (månedsberegninger -> 'periode' ->> 'tilOgMed') >= '2024-05-01'
group by
    gVerdiOgSak.saksnummer,
    gVerdiOgSak.behandlingId,
    gVerdiOgSak.revurderingstype,
    (månedsberegninger ->> 'benyttetGrunnbeløp')::integer
order by saksnummer;
