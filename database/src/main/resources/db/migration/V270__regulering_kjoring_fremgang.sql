-- Lagrer delvis fremgang for automatisk regulering per batch, slik at vi har resultater
-- også når jobben blir avbrutt midt i (pod-restart, deploy, cluster-autoscaler etc.).
-- Hver rad representerer én batch innenfor en kjøring (kjoering_id), nummerert med
-- batch_nummer (0-indeksert). Resultater for hele kjøringen oppnås ved å hente alle
-- rader for samme kjoering_id og slå sammen jsonb-arrayene.
CREATE TABLE IF NOT EXISTS regulering_kjoring_fremgang
(
    id            BIGSERIAL PRIMARY KEY,
    kjoering_id   UUID        NOT NULL,
    batch_nummer  INT         NOT NULL,
    tidspunkt     TIMESTAMPTZ NOT NULL DEFAULT now(),
    saker_i_batch INT         NOT NULL,
    resultater    JSONB       NOT NULL,
    UNIQUE (kjoering_id, batch_nummer)
);

CREATE INDEX IF NOT EXISTS regulering_kjoring_fremgang_kjoering_id_idx
    ON regulering_kjoring_fremgang (kjoering_id, batch_nummer);
