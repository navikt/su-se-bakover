package no.nav.su.se.bakover.domain.sak

enum class Sakstype(val value: String) {
    ALDER("alder"), UFØRE("uføre");

    companion object {
        fun from(value: String): Sakstype = when (value) {
            UFØRE.value -> UFØRE
            ALDER.value -> ALDER
            else -> {
                throw IllegalArgumentException("Ukjent type angitt")
            }
        }
    }
}
