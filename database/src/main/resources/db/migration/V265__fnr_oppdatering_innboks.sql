CREATE TABLE IF NOT EXISTS fnr_oppdatering_innboks
(
    id         uuid PRIMARY KEY,
    sak_id     uuid        NOT NULL REFERENCES sak (id),
    opprettet  timestamptz NOT NULL,
    prosessert timestamptz
);

-- Vi henter ut ubehandlede ofte – delvis indeks holder den liten.
CREATE INDEX IF NOT EXISTS fnr_oppdatering_innboks_ubehandlet_idx
    ON fnr_oppdatering_innboks (opprettet)
    WHERE prosessert IS NULL;

