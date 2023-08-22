package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import java.util.UUID

data class ReguleringSomKreverManuellBehandling(
    val saksnummer: Saksnummer,
    val fnr: Fnr,
    val reguleringId: UUID,
    val merknader: List<ReguleringMerknad>,
)
