-- Konverteringen slår opp vedtaksrelaterte rådata med både import_id og VEDTAK_ID.
-- Det eksisterende indexet på VEDTAK_ID finner også rader fra andre importer, som
-- PostgreSQL må hente fra tabellen og filtrere bort i en kostbar Bitmap Heap Scan.
-- Query Insights viste omtrent 6 000 kall med 2 565,5 ms gjennomsnittstid. Et
-- representativt kall returnerte 250 rader på 4,991 sekunder med kostnad 854 989,81;
-- Bitmap Index Scan brukte 950,745 ms og Bitmap Heap Scan 4,569 sekunder.
-- Dette indexet avgrenser oppslaget til riktig import før radene hentes. Partial-
-- betingelsen holder indexet begrenset til tabellene som inngår i vedtaksoppslaget.
-- Manuell rollback: DROP INDEX historisk_import_rad_import_vedtak_id;
CREATE INDEX historisk_import_rad_import_vedtak_id
    ON historisk_import_rad (
        import_id,
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
