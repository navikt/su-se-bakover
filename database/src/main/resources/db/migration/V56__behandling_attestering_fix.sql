update behandling set attestering = jsonb_set(attestering, '{attestant}', attestering #> '{attestant, navIdent}')
