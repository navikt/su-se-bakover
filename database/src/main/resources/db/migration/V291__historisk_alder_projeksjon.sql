CREATE TABLE historisk_alder_projeksjon (
    import_id UUID PRIMARY KEY REFERENCES historisk_import (id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('PÅGÅR', 'FULLFØRT', 'FEILET')),
    opprettet TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fullført TIMESTAMPTZ,
    feilbeskrivelse TEXT
);

CREATE UNIQUE INDEX historisk_alder_projeksjon_kun_en_paagaaende
    ON historisk_alder_projeksjon (status)
    WHERE status = 'PÅGÅR';

CREATE TABLE historisk_alder_stonad (
    import_id UUID NOT NULL REFERENCES historisk_alder_projeksjon (import_id) ON DELETE CASCADE,
    stonad_id TEXT NOT NULL,
    person_lopenummer TEXT NOT NULL,
    personident TEXT,
    startdato DATE,
    opphorsdato DATE,
    PRIMARY KEY (import_id, stonad_id)
);

CREATE INDEX historisk_alder_stonad_personident
    ON historisk_alder_stonad (personident, import_id)
    WHERE personident IS NOT NULL;

CREATE TABLE historisk_alder_vedtak (
    import_id UUID NOT NULL,
    vedtak_id TEXT NOT NULL,
    stonad_id TEXT NOT NULL,
    sakstype_raw TEXT NOT NULL,
    sakstype TEXT,
    resultat_raw TEXT NOT NULL,
    resultat TEXT,
    fra_og_med DATE,
    til_og_med DATE,
    registrert_tidspunkt TIMESTAMP,
    registrert_av TEXT,
    gyldig BOOLEAN NOT NULL,
    PRIMARY KEY (import_id, vedtak_id),
    FOREIGN KEY (import_id, stonad_id)
        REFERENCES historisk_alder_stonad (import_id, stonad_id)
        ON DELETE CASCADE
);

CREATE INDEX historisk_alder_vedtak_stonad
    ON historisk_alder_vedtak (import_id, stonad_id, fra_og_med, til_og_med);

CREATE TABLE historisk_alder_manedsbelop (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    import_id UUID NOT NULL,
    vedtak_id TEXT NOT NULL,
    linje_id TEXT,
    fra_og_med DATE,
    til_og_med DATE,
    sats NUMERIC NOT NULL CHECK (sats >= 0),
    fradrag NUMERIC NOT NULL CHECK (fradrag >= 0 AND fradrag <= sats),
    FOREIGN KEY (import_id, vedtak_id)
        REFERENCES historisk_alder_vedtak (import_id, vedtak_id)
        ON DELETE CASCADE
);

CREATE INDEX historisk_alder_manedsbelop_vedtak
    ON historisk_alder_manedsbelop (import_id, vedtak_id, fra_og_med, til_og_med);

CREATE TABLE historisk_alder_ytelsesperiode (
    import_id UUID NOT NULL REFERENCES historisk_alder_projeksjon (import_id) ON DELETE CASCADE,
    personident TEXT NOT NULL,
    stonad_id TEXT NOT NULL,
    vedtak_id TEXT NOT NULL,
    fra_og_med DATE NOT NULL,
    til_og_med DATE NOT NULL,
    sats NUMERIC NOT NULL CHECK (sats >= 0),
    fradrag NUMERIC NOT NULL CHECK (fradrag >= 0 AND fradrag <= sats),
    PRIMARY KEY (import_id, personident, fra_og_med),
    CHECK (fra_og_med <= til_og_med),
    FOREIGN KEY (import_id, vedtak_id)
        REFERENCES historisk_alder_vedtak (import_id, vedtak_id)
        ON DELETE CASCADE
);

CREATE INDEX historisk_alder_ytelsesperiode_person_periode
    ON historisk_alder_ytelsesperiode (personident, fra_og_med, til_og_med, import_id);

CREATE INDEX historisk_alder_projeksjon_fullfort
    ON historisk_alder_projeksjon (fullført DESC)
    WHERE status = 'FULLFØRT';

CREATE FUNCTION siste_fullførte_historiske_alder_projeksjon()
RETURNS TABLE (import_id UUID)
LANGUAGE SQL
STABLE
AS $$
    SELECT p.import_id
    FROM historisk_alder_projeksjon p
    WHERE p.status = 'FULLFØRT'
    ORDER BY p.fullført DESC, p.opprettet DESC, p.import_id DESC
    LIMIT 1
$$;
