package no.nav.su.se.bakover.common.periode

interface PeriodisertInformasjon {
    val periode: Periode
}

fun List<PeriodisertInformasjon>.harOverlappende() = this.map { it.periode }.harOverlappende()

fun <T : PeriodisertInformasjon> List<T>.kronologisk(): List<T> {
    return sortedBy { it.periode }
}
