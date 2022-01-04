package no.nav.su.se.bakover.service.klage

import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak

interface KlagevedtakService {
    fun lagre(klageVedtak: UprosessertFattetKlagevedtak)
    fun håndterUtfallFraKlageinstans()
}
