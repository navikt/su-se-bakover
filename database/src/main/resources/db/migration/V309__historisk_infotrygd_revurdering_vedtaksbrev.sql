ALTER TABLE historisk_infotrygd_revurdering
    ADD COLUMN vedtaksbrevvalg TEXT NOT NULL DEFAULT 'IKKE_VALGT',
    ADD COLUMN vedtaksbrev_fritekst TEXT,
    ADD CONSTRAINT historisk_infotrygd_revurdering_vedtaksbrevvalg
        CHECK (vedtaksbrevvalg IN ('IKKE_VALGT', 'SEND', 'IKKE_SEND'));
