ALTER TABLE historisk_import_tabell
    DROP CONSTRAINT historisk_import_tabell_import_id_fkey,
    ADD CONSTRAINT historisk_import_tabell_import_id_fkey
        FOREIGN KEY (import_id)
        REFERENCES historisk_import (id)
        ON DELETE CASCADE;
