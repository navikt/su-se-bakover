alter table behandling add column if not exists brevvalg json;

update behandling set brevvalg = '{"type":"IKKE_VALGT"}' ::jsonb where brevvalg is null;