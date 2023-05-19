package no.nav.su.se.bakover.client.person

import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr

data class PdlIdent(
    val fnr: Fnr,
    val aktørId: AktørId,
)
