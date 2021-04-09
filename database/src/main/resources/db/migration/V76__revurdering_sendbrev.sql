alter table revurdering add column if not exists sendbrev boolean;

update revurdering
set sendbrev = false
where revurderingstype like '%INGEN_ENDRING%';
