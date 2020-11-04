package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon

interface Månedsberegning : PeriodisertInformasjon {
    fun sum(): Double
    fun fradrag(): Double
    fun grunnbeløp(): Int
    fun sats(): Sats
    fun getSatsbeløp(): Double
}
