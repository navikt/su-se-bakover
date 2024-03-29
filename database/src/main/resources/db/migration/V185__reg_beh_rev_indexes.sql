-- regulering table
CREATE INDEX idx_regulering_sakId ON regulering(sakId);
CREATE INDEX idx_regulering_reguleringStatus ON regulering(reguleringStatus);
CREATE INDEX idx_regulering_reguleringType ON regulering(reguleringType);
CREATE INDEX idx_regulering_sakId_reguleringStatus ON regulering(sakId, reguleringStatus);

-- behandling table
CREATE INDEX idx_behandling_status ON behandling(status);
CREATE INDEX idx_behandling_sakId ON behandling(sakId);
CREATE INDEX idx_behandling_lukket ON behandling(lukket);

-- revurdering table
CREATE INDEX idx_revurdering_revurderingstype ON revurdering(revurderingstype);
CREATE INDEX idx_revurdering_sakId ON revurdering(sakId);
CREATE INDEX idx_revurdering_avsluttet_not_null ON revurdering ((avsluttet IS NOT NULL)) WHERE avsluttet IS NOT NULL;
CREATE INDEX idx_revurdering_avsluttet_null ON revurdering ((avsluttet IS NULL)) WHERE avsluttet IS NULL;

-- behandling_vedtak table
CREATE INDEX idx_behandling_vedtak_vedtakid ON behandling_vedtak(vedtakid);
CREATE INDEX idx_behandling_vedtak_sakid ON behandling_vedtak(sakid);