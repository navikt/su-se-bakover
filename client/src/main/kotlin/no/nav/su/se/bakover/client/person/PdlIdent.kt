package no.nav.su.se.bakover.client.person

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.domain.AktørId

data class PdlIdent(
    val fnr: Fnr,
    val aktørId: AktørId,
)
