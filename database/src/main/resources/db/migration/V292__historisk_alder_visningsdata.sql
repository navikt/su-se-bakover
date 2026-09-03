ALTER TABLE historisk_alder_vedtak
    ADD COLUMN bosituasjon_raw TEXT,
    ADD COLUMN bosituasjon TEXT CHECK (
        bosituasjon IN ('ENSLIG', 'EPS_OVER_67', 'EPS_UNDER_67', 'ENSLIG_MED_BOFELLESSKAP')
    ),
    ADD COLUMN aarlig_ytelsesbelop NUMERIC CHECK (aarlig_ytelsesbelop >= 0);
