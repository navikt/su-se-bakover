
ALTER TABLE manedsbelop DROP CONSTRAINT manedsbelop_stoenad_statistikk_id_fkey;
ALTER TABLE manedsbelop RENAME COLUMN bruttosats TO sats;
ALTER TABLE manedsbelop RENAME COLUMN nettosats TO utbetales;
ALTER TABLE manedsbelop RENAME to manedsbelop_statistikk;

ALTER TABLE inntekt RENAME TO fradrag_statistikk;
ALTER TABLE fradrag_statistikk RENAME COLUMN inntektstype TO fradragstype;

ALTER TABLE stoenad_statistikk  DROP COLUMN aar_maaned;

ALTER TABLE stoenad_maaned_statistikk RENAME COLUMN gjeldende_stonad_utbetalingsstart TO vedtak_fra_og_med;
ALTER TABLE stoenad_maaned_statistikk RENAME COLUMN gjeldende_stonad_utbetalingsstopp TO vedtak_til_og_med;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN sak_id UUID NOT NULL;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN funksjonell_tid TIMESTAMPTZ NOT NULL;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN teknisk_tid TIMESTAMPTZ NOT NULL;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN stonadstype TEXT NOT NULL;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN personnummer_eps TEXT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN vedtakstype TEXT NOT NULL;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN vedtaksresultat TEXT NOT NULL;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN opphorsgrunn TEXT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN opphorsdato DATE;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN behandlende_enhet_kode TEXT NOT NULL;

ALTER TABLE stoenad_maaned_statistikk ADD COLUMN har_utenlandsopphold TEXT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN har_familiegjenforening TEXT;
ALTER TABLE stoenad_maaned_statistikk ADD COLUMN flyktningsstatus TEXT;
