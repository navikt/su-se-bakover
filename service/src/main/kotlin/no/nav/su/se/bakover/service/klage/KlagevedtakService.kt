package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.klage.KanIkkeTolkeKlagevedtak
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlageinstansvedtak
import no.nav.su.se.bakover.domain.klage.UprosessertKlageinstansvedtak
import java.util.UUID

interface KlagevedtakService {
    fun lagre(klageVedtak: UprosessertFattetKlageinstansvedtak)
    fun håndterUtfallFraKlageinstans(deserializeAndMap: (id: UUID, opprettet: Tidspunkt, json: String) -> Either<KanIkkeTolkeKlagevedtak, UprosessertKlageinstansvedtak>)
}
