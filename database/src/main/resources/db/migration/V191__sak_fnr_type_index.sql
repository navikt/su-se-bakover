CREATE INDEX IF NOT EXISTS sak_fnr_idx on sak(fnr);
CREATE UNIQUE INDEX IF NOT EXISTS sak_fnr_type_idx on sak(fnr, type);