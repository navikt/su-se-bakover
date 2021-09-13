with b (id, fom, tom) as (
    select id,
           (stønadsperiode -> 'periode' ->> 'fraOgMed')::date,
           (stønadsperiode -> 'periode' ->> 'tilOgMed')::date
    from behandling
    where (stønadsperiode -> 'periode' ->> 'fraOgMed')::date is not null
      and (stønadsperiode -> 'periode' ->> 'tilOgMed')::date is not null
)

update grunnlag_bosituasjon g
set fraOgMed = b.fom,
    tilogmed = b.tom
from b
where b.id = g.behandlingid;