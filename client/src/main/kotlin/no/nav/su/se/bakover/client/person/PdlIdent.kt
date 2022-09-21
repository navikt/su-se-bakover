package no.nav.su.se.bakover.client.person

import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr

data class PdlIdent(
    val fnr: Fnr,
    val aktørId: AktørId,
)
