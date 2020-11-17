UPDATE
    behandling
SET
    behandlingsinformasjon = jsonb_set(
        behandlingsinformasjon,
        '{ektefelle, navn}',
        '{"fornavn": "", "mellomnavn": null, "etternavn": ""}'::jsonb
    )
where behandlingsinformasjon #>> '{ektefelle, type}' = 'Ektefelle' and not behandlingsinformasjon #> '{ektefelle}' ? 'navn'