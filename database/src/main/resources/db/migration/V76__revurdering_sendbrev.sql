alter table revurdering add column if not exists sendbrev boolean;

update revurdering
set sendbrev = true
where revurderingstype like 'TIL_ATTESTERING%';

update revurdering
set sendbrev = true
where revurderingstype like 'IVERKSATT%';

update revurdering
set sendbrev = true
where revurderingstype like 'UNDERKJENT%';

update revurdering
set sendbrev = false
where revurderingstype in ('TIL_ATTESTERING_INGEN_ENDRING', 'IVERKSATT_INGEN_ENDRING', 'UNDERKJENT_INGEN_ENDRING');