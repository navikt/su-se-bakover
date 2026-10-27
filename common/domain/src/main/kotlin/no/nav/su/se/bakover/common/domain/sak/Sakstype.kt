package no.nav.su.se.bakover.common.domain.sak

enum class Sakstype(val value: String) {
    ALDER("alder"),
    UFØRE("uføre"),
    ;

    override fun toString() = value

    companion object {
        fun from(value: String): Sakstype = tryFrom(value)
            ?: throw IllegalArgumentException("Ukjent type angitt")

        fun tryFrom(value: String): Sakstype? = entries.firstOrNull { it.value == value }
    }
}
