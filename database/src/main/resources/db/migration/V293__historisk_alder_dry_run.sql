UPDATE historisk_alder_vedtak v
SET
    bosituasjon_raw = k.data ->> 'KODE_KLASSE',
    bosituasjon = CASE BTRIM(k.data ->> 'KODE_KLASSE')
        WHEN 'EN' THEN 'ENSLIG'
        WHEN 'EO' THEN 'EPS_OVER_67'
        WHEN 'EU' THEN 'EPS_UNDER_67'
        WHEN 'EV' THEN 'ENSLIG_MED_BOFELLESSKAP'
        ELSE NULL
    END
FROM historisk_import_rad k
WHERE k.import_id = v.import_id
  AND k.tabellnavn = 'T_STONADSKLASSE'
  AND BTRIM(k.data ->> 'VEDTAK_ID') = v.vedtak_id
  AND BTRIM(k.data ->> 'KODE_NIVAA') = '02';

UPDATE historisk_alder_vedtak v
SET aarlig_ytelsesbelop = NULLIF(BTRIM(s.data ->> 'BELOP_BER_GRUNNLAG'), '')::numeric
FROM historisk_import_rad s
WHERE s.import_id = v.import_id
  AND s.tabellnavn = 'T_SU'
  AND BTRIM(s.data ->> 'VEDTAK_ID') = v.vedtak_id
  AND BTRIM(s.data ->> 'BELOP_BER_GRUNNLAG') ~ '^[0-9]+([.][0-9]+)?$';

ALTER TABLE historisk_alder_projeksjon
    ADD COLUMN id UUID NOT NULL DEFAULT GEN_RANDOM_UUID(),
    ADD COLUMN dry_run BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN maks_antall_stonader INTEGER,
    ADD COLUMN antall_stonader INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN avviksoppsummering JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN forbehold JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE historisk_alder_stonad
    ADD COLUMN projeksjon_id UUID;

ALTER TABLE historisk_alder_vedtak
    ADD COLUMN projeksjon_id UUID;

ALTER TABLE historisk_alder_manedsbelop
    ADD COLUMN projeksjon_id UUID;

ALTER TABLE historisk_alder_ytelsesperiode
    ADD COLUMN projeksjon_id UUID;

UPDATE historisk_alder_stonad s
SET projeksjon_id = p.id
FROM historisk_alder_projeksjon p
WHERE p.import_id = s.import_id;

UPDATE historisk_alder_vedtak v
SET projeksjon_id = p.id
FROM historisk_alder_projeksjon p
WHERE p.import_id = v.import_id;

UPDATE historisk_alder_manedsbelop b
SET projeksjon_id = p.id
FROM historisk_alder_projeksjon p
WHERE p.import_id = b.import_id;

UPDATE historisk_alder_ytelsesperiode y
SET projeksjon_id = p.id
FROM historisk_alder_projeksjon p
WHERE p.import_id = y.import_id;

UPDATE historisk_alder_projeksjon p
SET antall_stonader = (
    SELECT COUNT(*)
    FROM historisk_alder_stonad s
    WHERE s.projeksjon_id = p.id
);

ALTER TABLE historisk_alder_stonad
    ALTER COLUMN projeksjon_id SET NOT NULL;

ALTER TABLE historisk_alder_vedtak
    ALTER COLUMN projeksjon_id SET NOT NULL;

ALTER TABLE historisk_alder_manedsbelop
    ALTER COLUMN projeksjon_id SET NOT NULL;

ALTER TABLE historisk_alder_ytelsesperiode
    ALTER COLUMN projeksjon_id SET NOT NULL;

ALTER TABLE historisk_alder_projeksjon
    DROP CONSTRAINT historisk_alder_projeksjon_pkey CASCADE,
    ADD PRIMARY KEY (id),
    ADD UNIQUE (id, import_id),
    ADD CONSTRAINT historisk_alder_projeksjon_dry_run_grense CHECK (
        (dry_run AND maks_antall_stonader > 0)
        OR (NOT dry_run AND maks_antall_stonader IS NULL)
    ),
    ADD CONSTRAINT historisk_alder_projeksjon_antall_stonader CHECK (
        antall_stonader >= 0
        AND (maks_antall_stonader IS NULL OR antall_stonader <= maks_antall_stonader)
    );

ALTER TABLE historisk_alder_stonad
    DROP CONSTRAINT historisk_alder_stonad_pkey CASCADE,
    ADD PRIMARY KEY (projeksjon_id, stonad_id),
    ADD UNIQUE (projeksjon_id, import_id, stonad_id),
    ADD FOREIGN KEY (projeksjon_id, import_id)
        REFERENCES historisk_alder_projeksjon (id, import_id)
        ON DELETE CASCADE;

ALTER TABLE historisk_alder_vedtak
    DROP CONSTRAINT historisk_alder_vedtak_pkey CASCADE,
    ADD PRIMARY KEY (projeksjon_id, vedtak_id),
    ADD UNIQUE (projeksjon_id, import_id, vedtak_id),
    ADD FOREIGN KEY (projeksjon_id, import_id, stonad_id)
        REFERENCES historisk_alder_stonad (projeksjon_id, import_id, stonad_id)
        ON DELETE CASCADE;

ALTER TABLE historisk_alder_manedsbelop
    ADD FOREIGN KEY (projeksjon_id, import_id, vedtak_id)
        REFERENCES historisk_alder_vedtak (projeksjon_id, import_id, vedtak_id)
        ON DELETE CASCADE;

ALTER TABLE historisk_alder_ytelsesperiode
    DROP CONSTRAINT historisk_alder_ytelsesperiode_pkey CASCADE,
    ADD PRIMARY KEY (projeksjon_id, personident, fra_og_med),
    ADD FOREIGN KEY (projeksjon_id, import_id)
        REFERENCES historisk_alder_projeksjon (id, import_id)
        ON DELETE CASCADE,
    ADD FOREIGN KEY (projeksjon_id, import_id, vedtak_id)
        REFERENCES historisk_alder_vedtak (projeksjon_id, import_id, vedtak_id)
        ON DELETE CASCADE;

CREATE INDEX historisk_alder_projeksjon_import
    ON historisk_alder_projeksjon (import_id, opprettet DESC);

CREATE INDEX historisk_alder_stonad_personident_projeksjon
    ON historisk_alder_stonad (personident, projeksjon_id)
    WHERE personident IS NOT NULL;

CREATE INDEX historisk_alder_manedsbelop_projeksjon_vedtak
    ON historisk_alder_manedsbelop (projeksjon_id, vedtak_id, fra_og_med, til_og_med);

DROP FUNCTION siste_fullførte_historiske_alder_projeksjon();

CREATE FUNCTION siste_fullførte_historiske_alder_projeksjon()
RETURNS TABLE (projeksjon_id UUID, import_id UUID)
LANGUAGE SQL
STABLE
AS $$
    SELECT p.id, p.import_id
    FROM historisk_alder_projeksjon p
    WHERE p.status = 'FULLFØRT'
      AND NOT p.dry_run
    ORDER BY p.fullført DESC, p.opprettet DESC, p.id DESC
    LIMIT 1
$$;
