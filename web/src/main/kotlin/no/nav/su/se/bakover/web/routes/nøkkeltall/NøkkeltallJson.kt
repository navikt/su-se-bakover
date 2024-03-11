package no.nav.su.se.bakover.web.routes.nøkkeltall

import nøkkeltall.domain.Nøkkeltall

internal data class NøkkeltallJson(
    val søknader: SøknaderJson,
    val antallUnikePersoner: Int,
    val løpendeSaker: Int,
) {
    data class SøknaderJson(
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

internal fun Nøkkeltall.toJson() = NøkkeltallJson(
    søknader = søknader.toJson(),
    antallUnikePersoner = antallUnikePersoner,
    løpendeSaker = løpendeSaker,
)

internal fun Nøkkeltall.Søknader.toJson() =
    NøkkeltallJson.SøknaderJson(
        totaltAntall = totaltAntall,
        iverksatteAvslag = iverksatteAvslag,
        iverksatteInnvilget = iverksatteInnvilget,
        ikkePåbegynt = ikkePåbegynt,
        påbegynt = påbegynt,
        lukket = lukket,
        digitalsøknader = digitalsøknader,
        papirsøknader = papirsøknader,
    )
