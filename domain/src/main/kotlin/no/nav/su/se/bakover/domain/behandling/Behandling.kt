package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import java.util.UUID

interface Behandling {
    val id: UUID
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr
}
