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
insert into revurdering (sakid)
    (select rs.sakid from revurderingTilSak rs join revurdering r on r.id = rs.revurderingid);

alter table revurdering alter column sakId set not null;