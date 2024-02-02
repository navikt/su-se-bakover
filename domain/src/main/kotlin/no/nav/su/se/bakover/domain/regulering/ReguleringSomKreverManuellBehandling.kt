package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr

data class ReguleringSomKreverManuellBehandling(
    val saksnummer: Saksnummer,
    val fnr: Fnr,
    val reguleringId: ReguleringId,
    val merknader: List<ReguleringMerknad>,
)
