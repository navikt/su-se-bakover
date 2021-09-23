package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.prosentForskjell
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.PeriodisertFradrag
import java.util.Stack
import java.util.UUID

data class BeregningMedVirkningstidspunkt(
    private val id: UUID = UUID.randomUUID(),
    private val opprettet: Tidspunkt = Tidspunkt.now(),
    override val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<Fradrag>,
    private val fradragStrategy: FradragStrategy,
    private val begrunnelse: String? = null,
) : Beregning {
    private val beregning = beregn()

    init {
        require(fradrag.all { periode inneholder it.periode })
    }

    override fun getId(): UUID = id

    override fun getOpprettet(): Tidspunkt = opprettet

    override fun getSumYtelse() = beregning.values
        .sumOf { it.getSumYtelse() }

    override fun getSumFradrag() = beregning.values
        .sumOf { it.getSumFradrag() }

    override fun getFradragStrategyName(): FradragStrategyName = fradragStrategy.getName()

    private fun beregn(): Map<Periode, Månedsberegning> {
        val perioder = Stack<Periode>().apply {
            addAll(periode.tilMånedsperioder().reversed())
        }

        val beregnetPeriodisertFradrag = fradragStrategy.beregn(fradrag, periode)

        val virkningstidspunkt = Stack<PeriodisertBeregning>()

        val resultat = Stack<PeriodisertBeregning>()

        while (perioder.isNotEmpty()) {
            val next = perioder.pop()

            val månedsberegning = PeriodisertBeregning(
                periode = next,
                sats = sats,
                fradrag = beregnetPeriodisertFradrag[next] ?: emptyList(),
                fribeløpForEps = fradragStrategy.getEpsFribeløp(next),
            ).let { månedsberegning ->
                when (månedsberegning.ytelseStørreEnn0MenMindreEnnToProsentAvHøySats()) {
                    true -> PeriodisertBeregning(
                        periode = månedsberegning.periode,
                        sats = sats,
                        fradrag = månedsberegning.getFradrag()
                            .plus(månedsberegning.lagFradragForBeløpUnderMinstegrense()),
                    )
                    false -> månedsberegning
                }
            }

            // TODO må ta inn gjeldende utbetaling/fradrag for første måned.
            if (virkningstidspunkt.isEmpty()) {
                virkningstidspunkt.push(månedsberegning)
                resultat.push(månedsberegning)
            } else {
                val gjeldendeBeregning = virkningstidspunkt.pop()

                when {
                    månedsberegning.getSumYtelse() prosentForskjell gjeldendeBeregning.getSumYtelse() < -10.0 -> {
                        virkningstidspunkt.push(månedsberegning)
                        resultat.push(gjeldendeBeregning.shift())
                    }
                    månedsberegning.getSumYtelse() prosentForskjell gjeldendeBeregning.getSumYtelse() > 10.0 -> {
                        virkningstidspunkt.push(månedsberegning)
                        resultat.push(månedsberegning)
                    }
                    else -> {
                        virkningstidspunkt.push(gjeldendeBeregning.shift())
                        resultat.push(gjeldendeBeregning.shift())
                    }
                }
            }
        }

        return resultat.associateBy { it.periode }
    }

    private fun Månedsberegning.lagFradragForBeløpUnderMinstegrense() = PeriodisertFradrag(
        type = Fradragstype.UnderMinstenivå,
        månedsbeløp = getSumYtelse().toDouble(),
        periode = periode,
        utenlandskInntekt = null,
        tilhører = FradragTilhører.BRUKER,
    )

    private fun Månedsberegning.ytelseStørreEnn0MenMindreEnnToProsentAvHøySats() =
        getSumYtelse() > 0 && getSumYtelse() < Sats.toProsentAvHøy(periode)

    override fun getSats(): Sats = sats
    override fun getMånedsberegninger(): List<Månedsberegning> = beregning.values.toList()
    override fun getFradrag(): List<Fradrag> = fradrag
    override fun getBegrunnelse(): String? = begrunnelse
    override fun equals(other: Any?) = (other as? Beregning)?.let { this.equals(other) } ?: false
}
