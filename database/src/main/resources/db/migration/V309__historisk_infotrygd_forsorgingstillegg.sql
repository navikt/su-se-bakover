ALTER TABLE historisk_infotrygd_revurdering
    ADD COLUMN krever_kontroll_av_historisk_forsorgingstillegg BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN har_bekreftet_kontroll_av_historisk_forsorgingstillegg BOOLEAN NOT NULL DEFAULT FALSE;

-- Manuell rollback: fjern kolonnene
-- krever_kontroll_av_historisk_forsorgingstillegg og
-- har_bekreftet_kontroll_av_historisk_forsorgingstillegg.
