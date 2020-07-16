package no.nav.su.se.bakover.client.oppdrag

import java.time.LocalDate

class Utbetalingslinjer(
    internal val fagområde: String,
    internal val fagsystemId: String,
    internal val fødselsnummer: String,
    internal val endringskode: String,
    internal val saksbehandler: String
) : Iterable<Utbetalingslinjer.Utbetalingslinje> {
    private val linjer = mutableListOf<Utbetalingslinje>()

    fun linje(utbetalingslinje: Utbetalingslinje) {
        linjer.add(utbetalingslinje)
    }

    fun isEmpty() = linjer.isEmpty()
    fun førsteDag() = checkNotNull(
        Utbetalingslinje.førsteDato(
            linjer
        )
    ) { "Ingen oppdragslinjer" }

    fun sisteDag() = checkNotNull(
        Utbetalingslinje.sisteDato(
            linjer
        )
    ) { "Ingen oppdragslinjer" }

    fun totalbeløp() =
        Utbetalingslinje.totalbeløp(
            linjer
        )

    override fun iterator() = linjer.toList().listIterator()
    override fun equals(other: Any?) = other is Utbetalingslinjer && this.hashCode() == other.hashCode()
    override fun hashCode() = linjer.hashCode() * 67 + fødselsnummer.hashCode()

    class Utbetalingslinje(
        internal val delytelseId: String,
        internal val endringskode: String,
        internal val klassekode: String,
        internal val fom: LocalDate,
        internal val tom: LocalDate,
        internal val sats: Int,
        internal val refDelytelseId: String?,
        internal val refFagsystemId: String?,
        internal val statusFom: LocalDate?,
        internal val status: String?
    ) {
        internal companion object {
            fun førsteDato(linjer: List<Utbetalingslinje>) = linjer.minBy { it.fom }?.fom
            fun sisteDato(linjer: List<Utbetalingslinje>) = linjer.maxBy { it.tom }?.tom
            fun totalbeløp(linjer: List<Utbetalingslinje>) = linjer.sumBy { it.sats }
        }

        override fun hashCode() = fom.hashCode() * 37 +
            tom.hashCode() * 17 +
            sats.hashCode() * 41 +
            endringskode.hashCode() * 59 +
            statusFom.hashCode() * 23

        override fun equals(other: Any?) = other is Utbetalingslinje && this.hashCode() == other.hashCode()
    }
}
