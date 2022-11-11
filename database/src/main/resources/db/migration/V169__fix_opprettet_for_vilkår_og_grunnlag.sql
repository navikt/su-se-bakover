-- grunnlag_personlig_oppmøte søknadsbehandling
update grunnlag_personlig_oppmøte g
set opprettet = b.opprettet
from behandling b
where b.id = g.behandlingid
  and g.opprettet = '2021-01-01T01:02:03.456789Z'::timestamptz
returning g.*;

-- grunnlag_personlig_oppmøte revurdering
update grunnlag_personlig_oppmøte g
set opprettet = r.opprettet
from revurdering r
where r.id = g.behandlingid
  and g.opprettet = '2021-01-01T01:02:03.456789Z'::timestamptz
returning g.*;

-- vilkårsvurdering_personlig_oppmøte søknadsbehandling
update vilkårsvurdering_personlig_oppmøte g
set opprettet = b.opprettet
from behandling b
where b.id = g.behandlingid
  and g.opprettet = '2021-01-01T01:02:03.456789Z'::timestamptz
returning g.*;

-- vilkårsvurdering_personlig_oppmøte revurdering
update vilkårsvurdering_personlig_oppmøte g
set opprettet = r.opprettet
from revurdering r
where r.id = g.behandlingid
  and g.opprettet = '2021-01-01T01:02:03.456789Z'::timestamptz
returning g.*;

-- vilkårsvurdering_flyktning søknadsbehandling
update vilkårsvurdering_flyktning v
set opprettet = b.opprettet
from behandling b
where b.id = v.behandlingid
  and v.opprettet = '2021-01-01T01:02:03.456789Z'::timestamptz
returning v.*;

-- vilkårsvurdering_flyktning revurdering
update vilkårsvurdering_personlig_oppmøte g
set opprettet = r.opprettet
from revurdering r
where r.id = g.behandlingid
  and g.opprettet = '2021-01-01T01:02:03.456789Z'::timestamptz
returning g.*;

-- vilkårsvurdering_fastopphold søknadsbehandling
update vilkårsvurdering_fastopphold v
set opprettet = b.opprettet
from behandling b
where b.id = v.behandlingid
  and v.opprettet = '2021-01-01T01:02:03.456789Z'::timestamptz
returning v.*;

-- vilkårsvurdering_fastopphold revurdering
update vilkårsvurdering_fastopphold v
set opprettet = r.opprettet
from revurdering r
where r.id = v.behandlingid
  and v.opprettet = '2021-01-01T01:02:03.456789Z'::timestamptz
returning v.*;