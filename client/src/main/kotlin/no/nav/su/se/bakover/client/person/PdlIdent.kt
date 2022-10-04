package no.nav.su.se.bakover.client.person

import no.nav.su.se.bakover.common.AktørId
import no.nav.su.se.bakover.common.Fnr

data class PdlIdent(
    val fnr: Fnr,
    val aktørId: AktørId,
)
