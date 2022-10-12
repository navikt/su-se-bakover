package no.nav.su.se.bakover.hendelse.domain

@JvmInline
value class Hendelsesversjon(val value: Long) : Comparable<Hendelsesversjon> {

    init {
        require(value > 0L)
    }

    override fun compareTo(other: Hendelsesversjon): Int {
        return this.value.compareTo(other.value)
    }

    override fun toString() = value.toString()
}
