update behandling set beregning = jsonb_set(beregning, '{begrunnelse}', '""') where beregning #>> '{begrunnelse}' is null;

