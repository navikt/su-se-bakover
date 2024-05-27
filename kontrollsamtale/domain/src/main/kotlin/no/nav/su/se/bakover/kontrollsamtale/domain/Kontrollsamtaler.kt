package no.nav.su.se.bakover.kontrollsamtale.domain

data class Kontrollsamtaler(
    val kontrollsamtaler: List<Kontrollsamtale>,
) : List<Kontrollsamtale> by kontrollsamtaler {

    constructor(vararg kontrollsamtaler: Kontrollsamtale) : this(kontrollsamtaler.toList())
    companion object {
        fun empty() = Kontrollsamtaler(emptyList())
    }
}
