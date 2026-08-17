-- Indexes for efficient per-stønad and per-vedtak lookups in raw import data.
-- Enables the projector to work in batches without loading all data into memory.

CREATE INDEX historisk_import_rad_stonad_id
    ON historisk_import_rad ((data ->> 'STONAD_ID'))
    WHERE tabellnavn IN ('INFOTRYGD_SUQ.T_VEDTAK', 'INFOTRYGD_SUQ.T_STONAD');

CREATE INDEX historisk_import_rad_vedtak_id
    ON historisk_import_rad ((data ->> 'VEDTAK_ID'))
    WHERE tabellnavn IN (
        'INFOTRYGD_SUQ.T_BEREGN_GRL',
        'INFOTRYGD_SUQ.T_BESLUT',
        'INFOTRYGD_SUQ.T_DELYTELSE',
        'INFOTRYGD_SUQ.T_ENDRING',
        'INFOTRYGD_SUQ.T_ROLLE',
        'INFOTRYGD_SUQ.T_STONADSKLASSE',
        'INFOTRYGD_SUQ.T_SU'
    );

CREATE INDEX historisk_import_rad_person_lopenr
    ON historisk_import_rad ((data ->> 'PERSON_LOPENR'))
    WHERE tabellnavn = 'INFOTRYGD_SUQ.T_LOPENR_FNR';
