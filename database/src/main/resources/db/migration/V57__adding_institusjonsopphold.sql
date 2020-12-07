update behandling set behandlingsinformasjon
                          = jsonb_set(behandlingsinformasjon, '{institusjonsopphold}', '{"status": "VilkårOppfylt", "begrunnelse": "migration"}')
where behandlingsinformasjon #>> '{institusjonsopphold}' is null;
