package no.nav.su.se.bakover.client.person

import no.nav.su.se.bakover.domain.person.AktørId
import no.nav.su.se.bakover.domain.person.Fnr

data class PdlIdent(
    val fnr: Fnr,
    val aktørId: AktørId
)
