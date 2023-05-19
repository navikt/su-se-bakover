package no.nav.su.se.bakover.domain.beregning

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import java.util.UUID

/**
 * Beregning gjelder for en periode som kan være lengre enn en måned, men er inndelt i månedsvise-beregninger.
 */
data class BeregningMedFradragBeregnetMånedsvis(
    private val id: UUID = UUID.randomUUID(),
    private val opprettet: Tidspunkt,
    override val periode: Periode,
    private val fradrag: List<Fradrag>,
    private val begrunnelse: String?,
    private val sumYtelse: Int,
    private val sumFradrag: Double,
    private val månedsberegninger: NonEmptyList<Månedsberegning>,
) : Beregning {

    init {
        require(fradrag.all { periode inneholder it.periode })
    }

    override fun getId(): UUID = id

    override fun getOpprettet(): Tidspunkt = opprettet

    override fun getSumYtelse(): Int = sumYtelse

    override fun getSumFradrag(): Double = sumFradrag

    override fun getMånedsberegninger(): List<Månedsberegning> = månedsberegninger

    override fun getFradrag(): List<Fradrag> = fradrag

    override fun getBegrunnelse(): String? = begrunnelse

    override fun equals(other: Any?) = (other as? Beregning)?.let { this.equals(other) } ?: false
}
