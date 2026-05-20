package no.nav.su.se.bakover.common.domain.sak

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import java.util.UUID

data class SakInfo(
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val fnr: Fnr,
    val type: Sakstype,
)

data class SakInfoNy(
    val sakId: UUID,
    val fnr: Fnr,
    val type: Sakstype,
)
