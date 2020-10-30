package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.PeriodisertInformasjon
import no.nav.su.se.bakover.domain.beregning.Fradragstype

data class Fradrag(
    private val type: Fradragstype,
    private val beløp: Double,
    private val periode: Periode
) : PeriodisertInformasjon {
    init {
        require(beløp >= 0) { "Fradrag kan ikke være negative" }
    }

    fun månedsbeløp() = beløp / periode.antallMåneder()
    fun fradragstype(): Fradragstype = type
    override fun periode(): Periode = periode

    fun periodiser(): List<Fradrag> = periode.periodiserMåneder()
        .map { this.copy(type = type, beløp = månedsbeløp(), periode = it) }
}
