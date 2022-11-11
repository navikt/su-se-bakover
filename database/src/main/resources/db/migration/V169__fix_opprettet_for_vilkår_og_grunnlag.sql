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

--revurdering: e50989cc-6ef7-447d-a095-375fe068db2b, opprettet: 2022-10-11 13:02:02.869034+02, iverksatt: 2022-10-14T06:39:04.477079Z
update revurdering
set attestering = (
	select (
		jsonb_agg(
			case
				when attesteringer ->> 'opprettet' like '%2021-01-01T01:02:03.456789%'
					then jsonb_set(attesteringer, '{opprettet}','"2022-10-14T06:00:00.000000Z"')
				else attesteringer
			end
		)
	) from
		revurdering,
		jsonb_array_elements(attestering) attesteringer
		where revurdering.id = 'e50989cc-6ef7-447d-a095-375fe068db2b'
)
where revurdering.id = 'e50989cc-6ef7-447d-a095-375fe068db2b';

--revurdering: b006e127-0b31-44fa-8ba7-a09fe431b75e, opprettet: 2022-10-03 13:22:02.412406+02, iverksatt: 2022-10-03T11:43:06.625497Z
update revurdering
set attestering = (
	select (
		jsonb_agg(
			case
				when attesteringer ->> 'opprettet' like '%2021-01-01T01:02:03.456789%'
					then jsonb_set(attesteringer, '{opprettet}','2022-10-03T11:40:00.000000Z')
				else attesteringer
			end
		)
	) from
		revurdering,
		jsonb_array_elements(attestering) attesteringer
		where revurdering.id = 'b006e127-0b31-44fa-8ba7-a09fe431b75e'
)
where revurdering.id = 'b006e127-0b31-44fa-8ba7-a09fe431b75e';

--revurdering: d0b0834c-0323-4698-b985-017d1b860cd5, opprettet: 2022-09-13 14:46:14.885083+02, iverksatt: 2022-10-14T08:43:02.044011Z
update revurdering
set attestering = (
	select (
		jsonb_agg(
			case
				when attesteringer ->> 'opprettet' like '%2021-01-01T01:02:03.456789%'
					then jsonb_set(attesteringer, '{opprettet}','2022-10-14T08:40:00.000000Z')
				else attesteringer
			end
		)
	) from
		revurdering,
		jsonb_array_elements(attestering) attesteringer
		where revurdering.id = 'd0b0834c-0323-4698-b985-017d1b860cd5'
)
where revurdering.id = 'd0b0834c-0323-4698-b985-017d1b860cd5';

--revurdering: d6c698c8-2318-4c13-add4-21da067aae9d, opprettet: 2022-10-17 10:18:04.357037+02, iverksatt: ikke iverksatt enda
update revurdering
set attestering = (
	select (
		jsonb_agg(
			case
				when attesteringer ->> 'opprettet' like '%2021-01-01T01:02:03.456789%'
					then jsonb_set(attesteringer, '{opprettet}','2022-10-17T08:30:00.000000Z')
				else attesteringer
			end
		)
	) from
		revurdering,
		jsonb_array_elements(attestering) attesteringer
		where revurdering.id = 'd6c698c8-2318-4c13-add4-21da067aae9d'
)
where revurdering.id = 'd6c698c8-2318-4c13-add4-21da067aae9d';

--revurdering: fcf6b038-f943-43b1-a42f-9097d01fd2d4, opprettet: 2022-09-28 14:52:12.732443+02, iverksatt: 2022-10-06T08:25:08.369831Z
update revurdering
set attestering = (
	select (
		jsonb_agg(
			case
				when attesteringer ->> 'opprettet' like '%2021-01-01T01:02:03.456789%'
					then jsonb_set(attesteringer, '{opprettet}','2022-10-06T08:00:00.00000Z')
				else attesteringer
			end
		)
	) from
		revurdering,
		jsonb_array_elements(attestering) attesteringer
		where revurdering.id = 'fcf6b038-f943-43b1-a42f-9097d01fd2d4'
)
where revurdering.id = 'fcf6b038-f943-43b1-a42f-9097d01fd2d4';

--revurdering: 6621460f-e271-4b0d-981d-bdb3c542c731, opprettet: 2022-09-06 13:45:48.202999+02, iverksatt: 2022-10-11T13:01:58.304289Z
update revurdering
set attestering = (
	select (
		jsonb_agg(
			case
				when attesteringer ->> 'opprettet' like '%2021-01-01T01:02:03.456789%'
					then jsonb_set(attesteringer, '{opprettet}','2022-10-11T13:00:00.000000Z')
				else attesteringer
			end
		)
	) from
		revurdering,
		jsonb_array_elements(attestering) attesteringer
		where revurdering.id = '6621460f-e271-4b0d-981d-bdb3c542c731'
)
where revurdering.id = '6621460f-e271-4b0d-981d-bdb3c542c731';

--revurdering: 7552c83d-8ad2-4146-85e9-0fbbd9d9918e, opprettet: 2022-06-30 10:35:18.718543+02, iverksatt: 2022-10-17T12:37:10.444097Z
update revurdering
set attestering = (
	select (
		jsonb_agg(
			case
				when attesteringer ->> 'opprettet' like '%2021-01-01T01:02:03.456789%'
					then jsonb_set(attesteringer, '{opprettet}','2022-10-17T12:00:00.000000Z')
				else attesteringer
			end
		)
	) from
		revurdering,
		jsonb_array_elements(attestering) attesteringer
		where revurdering.id = '7552c83d-8ad2-4146-85e9-0fbbd9d9918e'
)
where revurdering.id = '7552c83d-8ad2-4146-85e9-0fbbd9d9918e';

--revurdering: 4b2b255e-bea6-4a17-923c-f4eefb26caa4, opprettet: 2022-10-25 11:01:04.031271+02, iverksatt: 2022-10-26T06:32:21.160225Z
update revurdering
set attestering = (
	select (
		jsonb_agg(
			case
				when attesteringer ->> 'opprettet' like '%2021-01-01T01:02:03.456789%'
					then jsonb_set(attesteringer, '{opprettet}','2022-10-26T06:00:00.000000Z')
				else attesteringer
			end
		)
	) from
		revurdering,
		jsonb_array_elements(attestering) attesteringer
		where revurdering.id = '4b2b255e-bea6-4a17-923c-f4eefb26caa4'
)
where revurdering.id = '4b2b255e-bea6-4a17-923c-f4eefb26caa4';