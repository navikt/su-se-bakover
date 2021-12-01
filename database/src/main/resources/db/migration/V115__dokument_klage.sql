ALTER TABLE dokument
    ADD COLUMN IF NOT EXISTS
        klageId uuid references klage(id);
