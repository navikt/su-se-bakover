package no.nav.su.se.bakover.web.routes.nøkkeltall

import nøkkeltall.domain.Nøkkeltall
import nøkkeltall.domain.NøkkeltallPerSakstype

internal fun List<NøkkeltallPerSakstype>.toJson(): List<NøkkeltallJson> {
    return map { nøkkeltallPerSakstype ->
        nøkkeltallPerSakstype.toJson()
    }
}

internal data class NøkkeltallJson(
    val sakstype: String,
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

internal fun NøkkeltallPerSakstype.toJson() = NøkkeltallJson(
    søknader = nøkkeltall.søknader.toJson(),
    antallUnikePersoner = nøkkeltall.antallUnikePersoner,
    løpendeSaker = nøkkeltall.løpendeSaker,
    sakstype = sakstype.name, // TODO: eller selve konstantsen?
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
