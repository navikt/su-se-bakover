package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype

interface Månedsberegning : PeriodisertInformasjon {
    fun getSumYtelse(): Int
    fun getSumFradrag(): Double
    fun getBenyttetGrunnbeløp(): Int
    fun getSats(): Sats
    fun getSatsbeløp(): Double
    fun getFradrag(): List<Fradrag>

    fun erFradragForEpsBenyttetIBeregning() = getFradrag().any { it.getFradragstype() == Fradragstype.BeregnetFradragEPS }
}
