package nøkkeltall.domain

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
