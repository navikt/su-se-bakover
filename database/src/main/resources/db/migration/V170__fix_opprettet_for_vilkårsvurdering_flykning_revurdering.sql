-- vilkårsvurdering_flyktning revurdering
update vilkårsvurdering_flyktning v
set opprettet = r.opprettet
from revurdering r
where r.id = v.behandlingid
  and v.opprettet = '2021-01-01T01:02:03.456789Z'::timestamptz
returning v.*;
