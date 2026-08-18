-- Sporer én importkjøring av Infotrygd-data. Kun én import kan pågå samtidig.
CREATE TABLE historisk_import
(
    id UUID PRIMARY KEY,
    status TEXT NOT NULL CHECK (status IN ('PÅGÅR', 'FULLFØRT', 'FEILET')),
    opprettet TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fullført TIMESTAMPTZ,
    feilbeskrivelse TEXT
);

CREATE UNIQUE INDEX historisk_import_kun_en_pågående
    ON historisk_import (status)
    WHERE status = 'PÅGÅR';

-- Sporer importstatus per Infotrygd-tabell innenfor én importkjøring, inkludert checkpoint for restart.
CREATE TABLE historisk_import_tabell
(
    import_id UUID NOT NULL REFERENCES historisk_import (id) ON DELETE CASCADE,
    tabellnavn TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PÅGÅR', 'FULLFØRT', 'FEILET')),
    forventet_antall BIGINT NOT NULL CHECK (forventet_antall >= 0),
    importert_antall BIGINT NOT NULL DEFAULT 0 CHECK (importert_antall >= 0),
    neste_iterator TEXT,
    neste_side BIGINT NOT NULL DEFAULT 0 CHECK (neste_side >= 0),
    kolonner JSONB NOT NULL,
    PRIMARY KEY (import_id, tabellnavn)
);

-- Tapsfri råkopi av hver rad fra Infotrygd, lagret som JSONB med kolonnenavn som nøkler.
CREATE TABLE historisk_import_rad
(
    import_id UUID NOT NULL,
    tabellnavn TEXT NOT NULL,
    side BIGINT NOT NULL CHECK (side >= 0),
    radnummer INTEGER NOT NULL CHECK (radnummer >= 0),
    data JSONB NOT NULL,
    PRIMARY KEY (import_id, tabellnavn, side, radnummer),
    FOREIGN KEY (import_id, tabellnavn)
        REFERENCES historisk_import_tabell (import_id, tabellnavn)
        ON DELETE CASCADE
);

