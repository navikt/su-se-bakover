CREATE INDEX IF NOT EXISTS idx_hendelse_fil_hendelseid ON hendelse_fil (hendelseid);
CREATE INDEX IF NOT EXISTS idx_dokument_sakid_duplikatav_null ON dokument (sakid) WHERE duplikatav IS NULL;
