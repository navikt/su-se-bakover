CREATE INDEX IF NOT EXISTS hendelse_sak_idx on hendelse(hendelseid);
CREATE INDEX IF NOT EXISTS hendelse_sak_idx on hendelse(sakid);
CREATE INDEX IF NOT EXISTS hendelse_entitetId_idx on hendelse(entitetid);
CREATE INDEX IF NOT EXISTS hendelse_sakId_type_idx on hendelse(sakid, type);
CREATE INDEX IF NOT EXISTS hendelse_konsument_hendelseId_konsumentId_idx on hendelse_konsument(hendelseid, konsumentid);
