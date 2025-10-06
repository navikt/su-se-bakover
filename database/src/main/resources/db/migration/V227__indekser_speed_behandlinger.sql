CREATE INDEX IF NOT EXISTS idx_søknad_lukket_null ON søknad (lukket) WHERE lukket IS NULL;
CREATE INDEX IF NOT EXISTS idx_behandling_lukket_status ON behandling (status) WHERE NOT lukket AND status NOT LIKE 'IVERKSATT%';
-- Disse indeksene mer enn halverer tidsbruken p.t. i prod