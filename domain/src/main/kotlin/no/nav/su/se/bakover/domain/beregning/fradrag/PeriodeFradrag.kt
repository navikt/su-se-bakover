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

    override fun månedsbeløp() = beløp / periode.antallMåneder()
    override fun type(): Fradragstype = type
    override fun totalBeløp(): Double = beløp
    override fun utenlandskInntekt(): UtenlandskInntekt? = utenlandskInntekt

    override fun periode(): Periode = periode

    override fun periodiser(): List<Fradrag> = periode.tilMånedsperioder()
        .map { this.copy(type = type, beløp = månedsbeløp(), periode = it) }
}
