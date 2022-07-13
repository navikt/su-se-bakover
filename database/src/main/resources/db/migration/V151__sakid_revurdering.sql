alter table revurdering add column sakId uuid references sak(id);

with revurderingTilSak as (
	select
		s.id as sakid,
		r.id as revurderingId
	from revurdering r
		join behandling_vedtak bv
			on bv.vedtakid = r.vedtaksomrevurderesid
		join sak s
			on s.id = bv.sakid
)
update revurdering set sakid = (select sakid from revurderingTilSak where revurderingId = id);

alter table revurdering alter column sakId set not null;