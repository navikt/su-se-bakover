update grunnlag_bosituasjon g
set fraOgMed = (stønadsperiode -> 'periode' ->> 'fraOgMed')::date,
    tilOgmed = (stønadsperiode -> 'periode' ->> 'tilOgMed')::date
from behandling b
where b.id = g.behandlingid
  and (stønadsperiode -> 'periode' ->> 'fraOgMed')::date is not null
  and (stønadsperiode -> 'periode' ->> 'tilOgMed')::date is not null;
  
