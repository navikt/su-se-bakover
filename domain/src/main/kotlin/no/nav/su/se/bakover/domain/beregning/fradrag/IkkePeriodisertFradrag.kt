package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode

internal data class IkkePeriodisertFradrag(
    private val type: Fradragstype,
    private val beløp: Double,
    private val periode: Periode,
    private val utenlandskInntekt: UtenlandskInntekt? = null,
    private val tilhører: FradragTilhører
) : Fradrag {
    init {
        require(beløp >= 0) { "Fradrag kan ikke være negative" }
    }

    override fun getTilhører(): FradragTilhører = tilhører
    override fun getFradragstype(): Fradragstype = type
    override fun getTotaltFradrag(): Double = beløp
    override fun getUtenlandskInntekt(): UtenlandskInntekt? = utenlandskInntekt
    override fun getPeriode(): Periode = periode
}
