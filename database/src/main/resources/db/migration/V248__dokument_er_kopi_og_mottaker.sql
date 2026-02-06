ALTER TABLE dokument
    ADD COLUMN er_kopi boolean NOT NULL DEFAULT false,
    ADD COLUMN ekstra_mottaker text,
    ADD COLUMN navn_ekstra_mottaker text;
