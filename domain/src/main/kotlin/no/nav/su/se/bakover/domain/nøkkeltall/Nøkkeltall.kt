package no.nav.su.se.bakover.domain.nøkkeltall

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
        val digitalsøknader: Int,
        val papirsøknader: Int,
    )
}
