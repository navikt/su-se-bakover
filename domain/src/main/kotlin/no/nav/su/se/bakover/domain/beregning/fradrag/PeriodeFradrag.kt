package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode

internal data class PeriodeFradrag(
    private val type: Fradragstype,
    private val beløp: Double,
    private val periode: Periode,
    private val utenlandskInntekt: UtenlandskInntekt? = null
) : AbstractFradrag() {
    init {
        require(beløp >= 0) { "Fradrag kan ikke være negative" }
    }

    override fun getFradragPerMåned() = beløp / periode.antallMåneder()
    override fun getFradragstype(): Fradragstype = type
    override fun getTotaltFradrag(): Double = beløp
    override fun getUtenlandskInntekt(): UtenlandskInntekt? = utenlandskInntekt

    override fun periode(): Periode = periode

    override fun periodiser(): List<Fradrag> = periode.tilMånedsperioder()
        .map { this.copy(type = type, beløp = getFradragPerMåned(), periode = it) }
}
