package no.nav.su.se.bakover.client.person

import no.nav.su.se.bakover.domain.person.SivilstandTyper

data class SivilstandResponse(
    val type: SivilstandTyper,
    val relatertVedSivilstand: String?,
)
