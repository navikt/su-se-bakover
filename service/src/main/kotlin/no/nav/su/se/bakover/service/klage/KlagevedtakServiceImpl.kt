package no.nav.su.se.bakover.service.klage

import no.nav.su.se.bakover.domain.klage.KlagevedtakRepo
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak

class KlagevedtakServiceImpl(
    private val klagevedtakRepo: KlagevedtakRepo,
) : KlagevedtakService {

    override fun lagre(klageVedtak: UprosessertFattetKlagevedtak) {
        klagevedtakRepo.lagre(klageVedtak)
    }
}
