package no.nav.su.se.bakover.common.periode

interface PeriodisertInformasjon {
    // Todo ai: Vurder om vi skal byte navn fra 'getX', for å slippe automatisk serialisering
    fun getPeriode(): Periode
}
