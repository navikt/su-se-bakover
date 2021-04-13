alter table revurdering add column if not exists behandlingsinformasjon jsonb;

update revurdering
set behandlingsinformasjon = ( select behandlingsinformasjon from vedtak v where vedtaksomrevurderesid = v.id )