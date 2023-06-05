CREATE INDEX if not exists idx_grunnlag_fradrag_behandlingid_fradragstype ON grunnlag_fradrag(behandlingid, fradragstype);
CREATE INDEX if not exists idx_grunnlag_fradrag_behandlingid ON grunnlag_fradrag(behandlingid);
CREATE INDEX if not exists idx_regulering_sakId_reguleringStatus_reguleringType ON regulering(sakId, reguleringStatus, reguleringType);