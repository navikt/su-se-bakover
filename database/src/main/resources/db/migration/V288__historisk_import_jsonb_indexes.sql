-- Indexes for efficient per-stønad and per-vedtak lookups in raw import data.
-- Enables the projector to work in batches without loading all data into memory.

CREATE INDEX historisk_import_rad_stonad_id
    ON historisk_import_rad ((data ->> 'STONAD_ID'))
    WHERE tabellnavn = 'T_VEDTAK';

CREATE INDEX historisk_import_rad_vedtak_id
    ON historisk_import_rad ((data ->> 'VEDTAK_ID'))
    WHERE tabellnavn IN (
        'T_BEREGN_GRL',
        'T_BESLUT',
        'T_DELYTELSE',
        'T_ENDRING',
        'T_ROLLE',
        'T_STONADSKLASSE',
        'T_SU'
    );
