ALTER TABLE personhendelse
    ADD COLUMN IF NOT EXISTS behandlet_automatisk boolean NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS personhendelse_automatisk_idx
    ON personhendelse (type, behandlet_automatisk)
    WHERE behandlet_automatisk = false;
