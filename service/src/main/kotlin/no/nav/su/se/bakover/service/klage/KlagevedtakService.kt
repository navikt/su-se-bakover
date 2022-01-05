package no.nav.su.se.bakover.service.klage

import no.nav.su.se.bakover.domain.klage.Klagevedtak
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak
import java.util.UUID

interface KlagevedtakService {
    fun lagre(klageVedtak: UprosessertFattetKlagevedtak)
    fun håndterUtfallFraKlageinstans(deserializeAndMap: (id: UUID, json: String) -> Klagevedtak.Uprosessert)
}
