package nøkkeltall.domain

import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class Nøkkeltall(
    val søknader: Søknader,
    val antallUnikePersoner: Int,
    val løpendeSaker: Int,
) {
    data class Søknader(
        val totaltAntall: Int,
        val iverksatteAvslag: Int,
        val iverksatteInnvilget: Int,
        val ikkePåbegynt: Int,
        val påbegynt: Int,
        val lukket: Int,
        val digitalsøknader: Int,
        val papirsøknader: Int,
    )
}

data class NøkkeltallPerSakstype(
    val sakstype: Sakstype,
    val nøkkeltall: Nøkkeltall,
)
