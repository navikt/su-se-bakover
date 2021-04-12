update behandling set beregning = jsonb_set(beregning, '{begrunnelse}', '"migrert"') where beregning #>> '{begrunnelse}' is null;

