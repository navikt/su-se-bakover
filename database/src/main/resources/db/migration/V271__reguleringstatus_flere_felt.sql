ALTER TABLE regulering_status_utestaaende
    ADD COLUMN opprettet TIMESTAMPTZ NOT NULL DEFAULT now()