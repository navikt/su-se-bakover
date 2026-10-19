CREATE TABLE stoenad_statistikk_aggregat (
    id UUID PRIMARY KEY,
    maaned DATE NOT NULL UNIQUE,
    status TEXT NOT NULL,
    startet TIMESTAMPTZ,
    payload JSONB,
    feilmelding TEXT
);

INSERT INTO stoenad_statistikk_aggregat (id, maaned, status)
SELECT gen_random_uuid(), maaned, 'VENTER'
FROM stoenad_maaned_statistikk
GROUP BY maaned;

CREATE INDEX stoenad_statistikk_aggregat_ventende_idx
    ON stoenad_statistikk_aggregat (maaned)
    WHERE status IN ('VENTER', 'FEILET');

CREATE INDEX idx_stoenad_maaned_statistikk_maaned_sak
    ON stoenad_maaned_statistikk (maaned, sak_id, teknisk_tid DESC, id DESC);
