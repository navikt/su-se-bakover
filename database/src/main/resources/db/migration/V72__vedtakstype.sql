alter table vedtak add column vedtaktype text;

update vedtak set vedtaktype = case
	when vedtak_søknadsbehandling.status = 'IVERKSATT_INNVILGET' then 'SØKNAD'
	when vedtak_søknadsbehandling.status = 'IVERKSATT_AVSLAG' then 'AVSLAG'
end
from (select v.id, b.status
	  from vedtak v
	  join behandling_vedtak bv on bv.vedtakid = v.id
	  join behandling b on b.id = bv.søknadsbehandlingid
	 ) as vedtak_søknadsbehandling
where vedtak.id = vedtak_søknadsbehandling.id;

update vedtak set vedtaktype = case
	when vedtak_revurdering.revurderingstype = 'IVERKSATT' then 'ENDRING'
end
from (select v.id, r.revurderingstype
	  from vedtak v
	  join behandling_vedtak bv on bv.vedtakid = v.id
	  join revurdering r on r.id = bv.revurderingid
	 ) as vedtak_revurdering
where vedtak.id = vedtak_revurdering.id;

alter table vedtak alter column vedtaktype set not null;
