package no.nav.su.se.bakover.database.beregning

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import java.util.UUID

internal data class PersistertBeregning(
    private val id: UUID,
    private val opprettet: Tidspunkt,
    private val sats: Sats,
    private val månedsberegninger: List<PersistertMånedsberegning>,
    private val fradrag: List<PersistertFradrag>,
    private val sumYtelse: Int,
    private val sumFradrag: Double,
    override val periode: Periode,
    private val fradragStrategyName: FradragStrategyName,
    private val begrunnelse: String?
) : Beregning {
    override fun getId(): UUID = id
    override fun getOpprettet(): Tidspunkt = opprettet
    override fun getSats(): Sats = sats
    override fun getMånedsberegninger(): List<Månedsberegning> = månedsberegninger
    override fun getFradrag(): List<Fradrag> = fradrag
    override fun getSumYtelse(): Int = sumYtelse
    override fun getSumFradrag(): Double = sumFradrag
    override fun getFradragStrategyName(): FradragStrategyName = fradragStrategyName
    override fun getBegrunnelse(): String? = begrunnelse

    override fun equals(other: Any?) = (other as? Beregning)?.let { this.equals(other) } ?: false

    override fun hashCode(): Int {
        var result = sats.hashCode()
        result = 31 * result + månedsberegninger.hashCode()
        result = 31 * result + fradrag.hashCode()
        result = 31 * result + sumYtelse
        result = 31 * result + sumFradrag.hashCode()
        result = 31 * result + periode.hashCode()
        result = 31 * result + fradragStrategyName.hashCode()
        return result
    }
}

internal data class PersistertMånedsberegning(
    private val sumYtelse: Int,
    private val sumFradrag: Double,
    private val benyttetGrunnbeløp: Int,
    private val sats: Sats,
    private val satsbeløp: Double,
    private val fradrag: List<PersistertFradrag>,
    override val periode: Periode
) : Månedsberegning {
    override fun getSumYtelse(): Int = sumYtelse
    override fun getSumFradrag(): Double = sumFradrag
    override fun getBenyttetGrunnbeløp(): Int = benyttetGrunnbeløp
    override fun getSats(): Sats = sats
    override fun getSatsbeløp(): Double = satsbeløp
    override fun getFradrag(): List<Fradrag> = fradrag

    override fun equals(other: Any?) = (other as? Månedsberegning)?.let { this.equals(other) } ?: false

    override fun hashCode(): Int {
        var result = sumYtelse
        result = 31 * result + sumFradrag.hashCode()
        result = 31 * result + benyttetGrunnbeløp
        result = 31 * result + sats.hashCode()
        result = 31 * result + satsbeløp.hashCode()
        result = 31 * result + fradrag.hashCode()
        result = 31 * result + periode.hashCode()
        return result
    }
}

internal data class PersistertFradrag(
    private val fradragstype: Fradragstype,
    private val månedsbeløp: Double,
    private val utenlandskInntekt: UtenlandskInntekt?,
    override val periode: Periode,
    private val tilhører: FradragTilhører
) : Fradrag {
    override fun getFradragstype(): Fradragstype = fradragstype

    @JsonAlias("totaltFradrag")
    override fun getMånedsbeløp(): Double = månedsbeløp
    override fun getUtenlandskInntekt(): UtenlandskInntekt? = utenlandskInntekt
    override fun getTilhører(): FradragTilhører = tilhører

    override fun equals(other: Any?) = (other as? Fradrag)?.let { this.equals(other) } ?: false

    override fun hashCode(): Int {
        var result = fradragstype.hashCode()
        result = 31 * result + månedsbeløp.hashCode()
        result = 31 * result + (utenlandskInntekt?.hashCode() ?: 0)
        result = 31 * result + periode.hashCode()
        result = 31 * result + tilhører.hashCode()
        return result
    }
}

internal fun Beregning.toSnapshot() = PersistertBeregning(
    id = getId(),
    opprettet = getOpprettet(),
    sats = getSats(),
    månedsberegninger = getMånedsberegninger().map { it.toSnapshot() },
    fradrag = getFradrag().map { it.toSnapshot() },
    sumYtelse = getSumYtelse(),
    sumFradrag = getSumFradrag(),
    periode = periode,
    fradragStrategyName = getFradragStrategyName(),
    begrunnelse = getBegrunnelse()
)

internal fun Månedsberegning.toSnapshot() = PersistertMånedsberegning(
    sumYtelse = getSumYtelse(),
    sumFradrag = getSumFradrag(),
    benyttetGrunnbeløp = getBenyttetGrunnbeløp(),
    sats = getSats(),
    satsbeløp = getSatsbeløp(),
    fradrag = getFradrag().map { it.toSnapshot() },
    periode = periode
)

internal fun Fradrag.toSnapshot() = PersistertFradrag(
    fradragstype = getFradragstype(),
    månedsbeløp = getMånedsbeløp(),
    utenlandskInntekt = getUtenlandskInntekt(),
    periode = periode,
    tilhører = getTilhører()
)
