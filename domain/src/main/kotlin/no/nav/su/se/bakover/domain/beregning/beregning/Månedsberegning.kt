package no.nav.su.se.bakover.domain.beregning.beregning

import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.Sats

interface Månedsberegning : PeriodisertInformasjon {
    fun sum(): Double
    fun fradrag(): Double
    fun grunnbeløp(): Int
    fun sats(): Sats
    fun getSatsbeløp(): Double
}
