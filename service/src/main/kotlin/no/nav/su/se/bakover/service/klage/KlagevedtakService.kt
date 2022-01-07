package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import no.nav.su.se.bakover.domain.klage.KanIkkeTolkeKlagevedtak
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak
import no.nav.su.se.bakover.domain.klage.UprosessertKlagevedtak
import java.util.UUID

interface KlagevedtakService {
    fun lagre(klageVedtak: UprosessertFattetKlagevedtak)
    fun håndterUtfallFraKlageinstans(deserializeAndMap: (id: UUID, json: String) -> Either<KanIkkeTolkeKlagevedtak, UprosessertKlagevedtak>)
}
