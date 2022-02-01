create table if not exists avkortingsvarsel
(
    id uuid primary key,
    opprettet timestamptz not null,
    sakId uuid not null references sak(id),
    revurderingId uuid not null references revurdering(id),
    simulering jsonb not null,
    status text not null,
    behandlingId uuid
);

alter table behandling add column avkorting json;
alter table revurdering add column avkorting json;

update behandling b set
	avkorting = case
		when not b.lukket and b.status = 'OPPRETTET' then '{"@type":"UHÅNDTERT_KAN_IKKE","uhåndtert":{"@type":"UHÅNDTERT_INGEN_UTESTÅENDE"}}'
		when not b.lukket and b.status = 'VILKÅRSVURDERT_INNVILGET' then '{"@type":"UHÅNDTERT_INGEN_UTESTÅENDE"}'
		when not b.lukket and b.status = 'VILKÅRSVURDERT_AVSLAG' then '{"@type":"UHÅNDTERT_KAN_IKKE","uhåndtert":{"@type":"UHÅNDTERT_INGEN_UTESTÅENDE"}}'
		when not b.lukket and b.status = 'BEREGNET_INNVILGET' then '{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}'
		when not b.lukket and b.status = 'BEREGNET_AVSLAG' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when not b.lukket and b.status = 'SIMULERT' then '{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}'
		when not b.lukket and b.status = 'TIL_ATTESTERING_INNVILGET' then '{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}'
		when not b.lukket and b.status = 'TIL_ATTESTERING_AVSLAG' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when not b.lukket and b.status = 'UNDERKJENT_INNVILGET' then '{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}'
		when not b.lukket and b.status = 'UNDERKJENT_AVSLAG' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when not b.lukket and b.status = 'IVERKSATT_INNVILGET' then '{"@type":"IVERKSATT_INGEN_UTESTÅENDE"}'
		when not b.lukket and b.status = 'IVERKSATT_AVSLAG' then '{"@type":"IVERKSATT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}}'

		when b.lukket and b.status = 'OPPRETTET' then '{"@type":"UHÅNDTERT_KAN_IKKE","uhåndtert":{"@type":"UHÅNDTERT_INGEN_UTESTÅENDE"}}'
		when b.lukket and b.status = 'VILKÅRSVURDERT_INNVILGET' then '{"@type":"UHÅNDTERT_KAN_IKKE","uhåndtert":{"@type":"UHÅNDTERT_INGEN_UTESTÅENDE"}}'
		when b.lukket and b.status = 'VILKÅRSVURDERT_AVSLAG' then '{"@type":"UHÅNDTERT_KAN_IKKE","uhåndtert":{"@type":"UHÅNDTERT_INGEN_UTESTÅENDE"}}'
		when b.lukket and b.status = 'BEREGNET_INNVILGET' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when b.lukket and b.status = 'BEREGNET_AVSLAG' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when b.lukket and b.status = 'SIMULERT' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when b.lukket and b.status = 'TIL_ATTESTERING_INNVILGET' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when b.lukket and b.status = 'TIL_ATTESTERING_AVSLAG' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when b.lukket and b.status = 'UNDERKJENT_INNVILGET' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when b.lukket and b.status = 'UNDERKJENT_AVSLAG' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when b.lukket and b.status = 'IVERKSATT_INNVILGET' then '{"@type":"IVERKSATT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when b.lukket and b.status = 'IVERKSATT_AVSLAG' then '{"@type":"IVERKSATT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_UTESTÅENDE"}}'
	end::json
from behandling bb where b.id = bb.id;

update revurdering r set
	avkorting = case
		when r.avsluttet is null and r.revurderingstype = 'OPPRETTET' then '{"@type":"UHÅNDTERT_INGEN_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'BEREGNET_INNVILGET' then  '{"@type":"DELVIS_HÅNDTERT_INGEN_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'BEREGNET_OPPHØRT' then '{"@type":"DELVIS_HÅNDTERT_INGEN_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'BEREGNET_INGEN_ENDRING' then '{"@type":"DELVIS_HÅNDTERT_INGEN_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'SIMULERT_INNVILGET' then '{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'SIMULERT_OPPHØRT' then '{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'TIL_ATTESTERING_INNVILGET' then '{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'TIL_ATTESTERING_OPPHØRT' then '{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'TIL_ATTESTERING_INGEN_ENDRING' then '{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'IVERKSATT_INNVILGET' then '{"@type":"IVERKSATT_INGEN_NY_ELLER_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'IVERKSATT_OPPHØRT' then '{"@type":"IVERKSATT_INGEN_NY_ELLER_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'IVERKSATT_INGEN_ENDRING' then '{"@type":"IVERKSATT_INGEN_NY_ELLER_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'UNDERKJENT_INNVILGET' then '{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'UNDERKJENT_OPPHØRT' then '{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'UNDERKJENT_INGEN_ENDRING' then '{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}'
		when r.avsluttet is null and r.revurderingstype = 'SIMULERT_STANS' then null
		when r.avsluttet is null and r.revurderingstype = 'AVSLUTTET_STANS' then null
		when r.avsluttet is null and r.revurderingstype = 'IVERKSATT_STANS' then null
		when r.avsluttet is null and r.revurderingstype = 'SIMULERT_GJENOPPTAK' then null
		when r.avsluttet is null and r.revurderingstype = 'AVSLUTTET_GJENOPPTAK' then null
		when r.avsluttet is null and r.revurderingstype = 'IVERKSATT_GJENOPPTAK' then null

		when r.avsluttet is not null and r.revurderingstype = 'OPPRETTET' then '{"@type":"UHÅNDTERT_KAN_IKKE","uhåndtert":{"@type":"UHÅNDTERT_INGEN_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'BEREGNET_INNVILGET' then '{"@type":"DELVIS_KAN_IKKE","delvisHåndtert":{"@type":"DELVIS_HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'BEREGNET_OPPHØRT' then '{"@type":"DELVIS_KAN_IKKE","delvisHåndtert":{"@type":"DELVIS_HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'BEREGNET_INGEN_ENDRING' then '{"@type":"DELVIS_KAN_IKKE","delvisHåndtert":{"@type":"DELVIS_HÅNDTERT_INGEN_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'SIMULERT_INNVILGET' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'SIMULERT_OPPHØRT' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'TIL_ATTESTERING_INNVILGET' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'TIL_ATTESTERING_OPPHØRT' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'TIL_ATTESTERING_INGEN_ENDRING' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'IVERKSATT_INNVILGET' then '{"@type":"IVERKSATT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'IVERKSATT_OPPHØRT' then '{"@type":"IVERKSATT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'IVERKSATT_INGEN_ENDRING' then '{"@type":"IVERKSATT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'UNDERKJENT_INNVILGET' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'UNDERKJENT_OPPHØRT' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'UNDERKJENT_INGEN_ENDRING' then '{"@type":"HÅNDTERT_KAN_IKKE","håndtert":{"@type":"HÅNDTERT_INGEN_NY_ELLER_UTESTÅENDE"}}'
		when r.avsluttet is not null and r.revurderingstype = 'SIMULERT_STANS' then null
		when r.avsluttet is not null and r.revurderingstype = 'AVSLUTTET_STANS' then null
		when r.avsluttet is not null and r.revurderingstype = 'IVERKSATT_STANS' then null
		when r.avsluttet is not null and r.revurderingstype = 'SIMULERT_GJENOPPTAK' then null
		when r.avsluttet is not null and r.revurderingstype = 'AVSLUTTET_GJENOPPTAK' then null
		when r.avsluttet is not null and r.revurderingstype = 'IVERKSATT_GJENOPPTAK' then null
	end::json
from revurdering rr where r.id = rr.id;