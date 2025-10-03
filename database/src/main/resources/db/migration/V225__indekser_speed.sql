CREATE INDEX idx_revurdering_aarsak ON revurdering(årsak);
CREATE INDEX idx_klage_type ON klage(type);
CREATE INDEX idx_soknad_lukket_null ON søknad((lukket IS NULL));
CREATE INDEX idx_behandling_soknadid ON behandling(søknadid);