CREATE INDEX historisk_import_rad_person_lopenr
    ON historisk_import_rad (import_id, (data ->> 'PERSON_LOPENR'))
    WHERE tabellnavn = 'T_LOPENR_FNR';
