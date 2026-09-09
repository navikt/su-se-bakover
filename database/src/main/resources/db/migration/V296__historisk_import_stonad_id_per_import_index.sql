-- Konverteringen finner T_VEDTAK-rader med både import_id og STONAD_ID. Det
-- eksisterende indexet inneholder bare STONAD_ID, så PostgreSQL må hente treff på
-- tvers av importer og filtrere dem etterpå. EXPLAIN ANALYZE estimerte 10 260
-- vedtaksrader for 50 stønader, mens det faktiske antallet var 135.
--
-- Dette indexet avgrenser oppslaget til riktig import før radene hentes.
-- ANALYZE samler statistikk for JSONB-uttrykket slik at indexet kan tas i bruk
-- med riktigere selektivitetsestimater etter migreringen.
-- Manuell rollback: DROP INDEX historisk_import_rad_import_stonad_id;
CREATE INDEX historisk_import_rad_import_stonad_id
    ON historisk_import_rad (
        import_id,
        (data ->> 'STONAD_ID')
    )
    WHERE tabellnavn = 'T_VEDTAK';

ANALYZE historisk_import_rad;
