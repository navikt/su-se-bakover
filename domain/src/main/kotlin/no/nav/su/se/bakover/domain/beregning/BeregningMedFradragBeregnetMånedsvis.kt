package no.nav.su.se.bakover.domain.beregning

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import java.util.UUID

data class BeregningMedFradragBeregnetMånedsvis(
    private val id: UUID = UUID.randomUUID(),
    private val opprettet: Tidspunkt,
    override val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<Fradrag>,
    private val fradragStrategy: FradragStrategy,
    private val begrunnelse: String?,
    private val sumYtelse: Int,
    private val sumFradrag: Double,
    private val månedsberegninger: NonEmptyList<Månedsberegning>,
    private val beregningsperioder: List<Beregningsperiode>,
) : Beregning {

    init {
        require(fradrag.all { periode inneholder it.periode })
    }

    override fun getId(): UUID = id

    override fun getOpprettet(): Tidspunkt = opprettet

    override fun getSumYtelse(): Int = sumYtelse

    override fun getSumFradrag(): Double = sumFradrag

    override fun getFradragStrategyName(): FradragStrategyName = fradragStrategy.getName()

    override fun getSats(): Sats = sats

    override fun getMånedsberegninger(): List<Månedsberegning> = månedsberegninger

    override fun getFradrag(): List<Fradrag> = fradrag

    override fun getBegrunnelse(): String? = begrunnelse

    override fun equals(other: Any?) = (other as? Beregning)?.let { this.equals(other) } ?: false
}
