ALTER TABLE dokument
    ADD COLUMN historisk_revurdering_id UUID REFERENCES historisk_infotrygd_revurdering(id),
    ADD CONSTRAINT dokument_en_revurderingsreferanse_check CHECK (
        revurderingId IS NULL OR historisk_revurdering_id IS NULL
    );

CREATE INDEX dokument_historisk_revurdering_id_idx ON dokument (historisk_revurdering_id)
    WHERE historisk_revurdering_id IS NOT NULL;
