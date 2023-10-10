package no.nav.su.se.bakover.client.person

import person.domain.SivilstandTyper

data class SivilstandResponse(
    val type: SivilstandTyper,
    val relatertVedSivilstand: String?,
)
