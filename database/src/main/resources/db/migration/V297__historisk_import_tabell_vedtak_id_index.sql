-- Separate vedtaksoppslag filtrerer på import_id, tabellnavn og VEDTAK_ID.
-- En målt T_DELYTELSE-spørring brukte primærnøkkelindeksen til å hente 362 302
-- rader og filtrerte bort 362 032 før den returnerte 270 rader på 1,57 sekunder.
-- Dette indexet kan finne riktig import, tabell og vedtak direkte.
--
-- Partialbetingelsen må også stå eksplisitt i spørringen for at PostgreSQL skal
-- kunne bruke indexet med en parameterisert tabellnavnverdi i generiske planer.
CREATE INDEX historisk_import_rad_import_tabell_vedtak_id
    ON historisk_import_rad (
        import_id,
        tabellnavn,
        (data ->> 'VEDTAK_ID')
    )
    WHERE tabellnavn IN (
        'T_BEREGN_GRL',
        'T_BESLUT',
        'T_DELYTELSE',
        'T_ENDRING',
        'T_ROLLE',
        'T_STONADSKLASSE',
        'T_SU'
    );

ANALYZE historisk_import_rad;
