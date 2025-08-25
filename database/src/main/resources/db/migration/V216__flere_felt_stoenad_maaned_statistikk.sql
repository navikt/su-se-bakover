
ALTER TABLE manedsbelop DROP CONSTRAINT manedsbelop_stoenad_statistikk_id_fkey;

ALTER TABLE stoenad_statistikk  DROP COLUMN aar_maaned;

ALTER TABLE stoenad_maaned_statistikk RENAME COLUMN gjeldende_stonad_utbetalingsstart TO vedtak_fra_og_med;
ALTER TABLE stoenad_maaned_statistikk RENAME COLUMN gjeldende_stonad_utbetalingsstopp TO vedtak_til_og_med;
