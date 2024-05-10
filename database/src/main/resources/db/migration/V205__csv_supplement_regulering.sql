ALTER TABLE reguleringssupplement
    ADD COLUMN IF NOT EXISTS
        csv text not null default '';