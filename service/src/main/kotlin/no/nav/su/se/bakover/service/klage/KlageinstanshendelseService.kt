package no.nav.su.se.bakover.service.klage

import arrow.core.Either
import behandling.klage.domain.UprosessertKlageinstanshendelse
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.klage.KunneIkkeTolkeKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import java.util.UUID

interface KlageinstanshendelseService {
    fun lagre(hendelse: UprosessertKlageinstanshendelse)
    fun håndterUtfallFraKlageinstans(deserializeAndMap: (id: UUID, opprettet: Tidspunkt, json: String) -> Either<KunneIkkeTolkeKlageinstanshendelse, TolketKlageinstanshendelse>)
}
