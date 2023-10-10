package no.nav.su.se.bakover.common.domain.sak

enum class Sakstype(val value: String) {
    ALDER("alder"),
    UFØRE("uføre"),
    ;

    override fun toString() = value

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
