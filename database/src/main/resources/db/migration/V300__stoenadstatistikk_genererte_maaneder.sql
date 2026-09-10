-- Manuell rollback:
-- DROP TABLE stoenad_maaned_statistikk_generering;
-- DROP INDEX idx_stoenad_maaned_statistikk_maaned_sak;

CREATE TABLE stoenad_maaned_statistikk_generering (
    maaned DATE PRIMARY KEY,
    generert TIMESTAMPTZ NOT NULL
);

INSERT INTO stoenad_maaned_statistikk_generering (maaned, generert)
SELECT maaned, max(teknisk_tid)
FROM stoenad_maaned_statistikk
GROUP BY maaned;

CREATE INDEX idx_stoenad_maaned_statistikk_maaned_sak
    ON stoenad_maaned_statistikk (maaned, sak_id, teknisk_tid DESC, id DESC);
