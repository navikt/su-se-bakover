package no.nav.su.se.bakover.domain.nøkkeltall

data class Nøkkeltall(
    val totalt: Int,
    val iverksattAvslag: Int,
    val iverksattInnvilget: Int,
    val ikkePåbegynt: Int,
    val påbegynt: Int,
    val digitalsøknader: Int,
    val papirsøknader: Int,
    val personer: Int,
)
