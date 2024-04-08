package no.nav.su.se.bakover.common.tid.periode

interface PeriodisertInformasjon {
    val periode: Periode
}

fun List<PeriodisertInformasjon>.harOverlappende() = this.map { it.periode }.harOverlappende()
