package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Fnr

data class Ident(
    val fnr: Fnr,
    val aktørId: AktørId,
)
