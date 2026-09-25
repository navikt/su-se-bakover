ALTER TABLE historisk_infotrygd_revurdering
    ADD COLUMN forhandsvarsel JSONB NOT NULL DEFAULT '{"type":"IKKE_VALGT"}'::JSONB;

-- Manuell rollback: fjern kolonnen forhandsvarsel.
