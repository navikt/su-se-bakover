alter table revurdering add column if not exists brevvalg json;

with data as (
	select id, revurderingstype, friteksttilbrev, skalføretilbrevutsending, saksbehandler from revurdering
),
brevvalg as (
	select
		id,
		revurderingstype,
		friteksttilbrev,
		skalføretilbrevutsending,
		saksbehandler,
		case
			when revurderingstype like '%IVERKS%INGEN%' and skalføretilbrevutsending then json_build_object('type','SEND_BREV','fritekst',friteksttilbrev,'begrunnelse',null,'bestemtav',saksbehandler)
			when revurderingstype like '%TIL_ATT%INGEN%' and not skalføretilbrevutsending then json_build_object('type','IKKE_SEND_BREV','fritekst',friteksttilbrev,'begrunnelse',null,'bestemtav',saksbehandler)
			when revurderingstype like '%IVERKS%'and skalføretilbrevutsending then json_build_object('type','SEND_BREV','fritekst',friteksttilbrev,'begrunnelse',null,'bestemtav','srvsupstonad')
			when revurderingstype like '%TIL_ATT%' and skalføretilbrevutsending then json_build_object('type','SEND_BREV','fritekst',friteksttilbrev,'begrunnelse',null,'bestemtav','srvsupstonad')
			when not skalføretilbrevutsending then json_build_object('type','IKKE_SEND_BREV','fritekst',friteksttilbrev,'begrunnelse',null,'bestemtav','srvsupstonad')
			else json_build_object('type','IKKE_VALGT')
		end as valg
	from data
),
migrert as (
    select
		id,
		valg
	from brevvalg
) update revurdering set brevvalg = (select valg from migrert where id = revurdering.id);