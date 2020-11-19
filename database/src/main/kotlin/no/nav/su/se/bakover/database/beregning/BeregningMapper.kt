package no.nav.su.se.bakover.database.beregning

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

internal data class Beregnet(
    private val id: UUID,
    private val opprettet: Tidspunkt,
    private val sats: Sats,
    private val månedsberegninger: List<BeregnetMåned>,
    private val fradrag: List<BeregnetFradrag>,
    private val sumYtelse: Int,
    private val sumFradrag: Double,
    private val sumYtelseErUnderMinstebeløp: Boolean,
    private val periode: Periode,
    private val fradragStrategyName: FradragStrategyName
) : Beregning {
    override fun getId(): UUID = id
    override fun getOpprettet(): Tidspunkt = opprettet
    override fun getSats(): Sats = sats
    override fun getMånedsberegninger(): List<Månedsberegning> = månedsberegninger
    override fun getFradrag(): List<Fradrag> = fradrag
    override fun getSumYtelse(): Int = sumYtelse
    override fun getSumFradrag(): Double = sumFradrag
    override fun getSumYtelseErUnderMinstebeløp(): Boolean = sumYtelseErUnderMinstebeløp
    override fun getFradragStrategyName(): FradragStrategyName = fradragStrategyName

    override fun getPeriode(): Periode = periode
}

internal data class BeregnetMåned(
    private val sumYtelse: Int,
    private val sumFradrag: Double,
    private val benyttetGrunnbeløp: Int,
    private val sats: Sats,
    private val satsbeløp: Double,
    private val fradrag: List<BeregnetFradrag>,
    private val periode: Periode
) : Månedsberegning {
    override fun getSumYtelse(): Int = sumYtelse
    override fun getSumFradrag(): Double = sumFradrag
    override fun getBenyttetGrunnbeløp(): Int = benyttetGrunnbeløp
    override fun getSats(): Sats = sats
    override fun getSatsbeløp(): Double = satsbeløp
    override fun getFradrag(): List<Fradrag> = fradrag
    override fun getPeriode(): Periode = periode
}

internal data class BeregnetFradrag(
    private val fradragstype: Fradragstype,
    private val totaltFradrag: Double,
    private val utenlandskInntekt: UtenlandskInntekt?,
    private val fradragPerMåned: Double,
    private val periode: Periode,
    private val tilhører: FradragTilhører
) : Fradrag {
    override fun getFradragstype(): Fradragstype = fradragstype
    override fun getTotaltFradrag(): Double = totaltFradrag
    override fun getUtenlandskInntekt(): UtenlandskInntekt? = utenlandskInntekt
    override fun periodiser(): List<Fradrag> = listOf(this) // TODO probably refactor
    override fun getFradragPerMåned(): Double = fradragPerMåned
    override fun getTilhører(): FradragTilhører = tilhører

    override fun getPeriode(): Periode = periode
}

internal fun Beregning.toSnapshot() = Beregnet(
    id = getId(),
    opprettet = getOpprettet(),
    sats = getSats(),
    månedsberegninger = getMånedsberegninger().map { it.toSnapshot() },
    fradrag = getFradrag().map { it.toSnapshot() },
    sumYtelse = getSumYtelse(),
    sumFradrag = getSumFradrag(),
    sumYtelseErUnderMinstebeløp = getSumYtelseErUnderMinstebeløp(),
    periode = getPeriode(),
    fradragStrategyName = getFradragStrategyName()
)

internal fun Månedsberegning.toSnapshot() = BeregnetMåned(
    sumYtelse = getSumYtelse(),
    sumFradrag = getSumFradrag(),
    benyttetGrunnbeløp = getBenyttetGrunnbeløp(),
    sats = getSats(),
    satsbeløp = getSatsbeløp(),
    fradrag = getFradrag().map { it.toSnapshot() },
    periode = getPeriode()
)

internal fun Fradrag.toSnapshot() = BeregnetFradrag(
    fradragstype = getFradragstype(),
    totaltFradrag = getTotaltFradrag(),
    utenlandskInntekt = getUtenlandskInntekt(),
    fradragPerMåned = getFradragPerMåned(),
    periode = getPeriode(),
    tilhører = getTilhører()
)
