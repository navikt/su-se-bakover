package no.nav.su.se.bakover.common.periode

interface PeriodisertInformasjon {
    val periode: Periode
}

fun List<PeriodisertInformasjon>.harOverlappende() =
    this.any { p1 -> this.minus(p1).any { p2 -> p1.periode overlapper p2.periode } }

fun <T : PeriodisertInformasjon> List<T>.kronologisk(): List<T> {
    return sortedBy { it.periode }
}
