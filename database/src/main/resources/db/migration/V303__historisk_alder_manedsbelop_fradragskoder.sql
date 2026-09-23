ALTER TABLE historisk_alder_manedsbelop
    ADD COLUMN fradragskoder TEXT[] NOT NULL DEFAULT '{}'::TEXT[];

COMMENT ON COLUMN historisk_alder_manedsbelop.fradragskoder IS
    'Rå TYPE_BELOP-koder fra T_BEREGN_GRL med samme vedtak og periode som månedsbeløpet. '
    'Observerte koder er ARBE, ARBM, FTRE, FTRM, PENE, PENM og UTLM; listen er ikke uttømmende.';
