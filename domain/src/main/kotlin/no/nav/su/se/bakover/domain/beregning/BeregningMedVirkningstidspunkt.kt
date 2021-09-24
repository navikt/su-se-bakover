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

        val gjeldendeBeregninger = Stack<PeriodisertBeregning>()

        val resultat = Stack<PeriodisertBeregning>()

        while (perioder.isNotEmpty()) {
            val next = perioder.pop()

            val inneværendeMåned = PeriodisertBeregning(
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
            if (gjeldendeBeregninger.isEmpty()) {
                gjeldendeBeregninger.push(inneværendeMåned)
                resultat.push(inneværendeMåned)
            } else {
                val gjeldendeBeregning = gjeldendeBeregninger.pop()

                when {
                    inneværendeMåned.getSumYtelse() prosentForskjell gjeldendeBeregning.getSumYtelse() <= -10.0 -> {
                        /**
                         * Gjeldende beregning videreføres for inneværendene måned. Siden gjeldende beregning er gjort
                         * for forrige måned, må denne forskyves en måned fram.
                         *
                         * Ved forskyvning vil gjeldende beregning utføres på nytt med grunnbeløp for måneden etter,
                         * noe som fører til at g-regulering implisitt er fritatt for 10% sjekk.
                         */
                        resultat.push(gjeldendeBeregning.forskyv(1))
                        /**
                         * Den ubenyttede beregningen for inneværende måned skal ikke tre i kraft før tidligst påfølgende
                         * måned og settes til ny gjeldende beregning. Påfølgende måned vil sammenlignes mot denne.
                         */
                        gjeldendeBeregninger.push(inneværendeMåned)
                    }
                    inneværendeMåned.getSumYtelse() prosentForskjell gjeldendeBeregning.getSumYtelse() >= 10.0 -> {
                        /**
                         * Beregningen for inneværende måned skal tre i kraft umiddelbart og legges til sluttresultat
                         * og settes som ny gjeldende beregning.
                         *
                         * Inneværende måned vil være beregnet med korrekt grunnbeløp, og dermed trengs det ikke noen
                         * spesiell håndtering av g-regulering.
                         */
                        inneværendeMåned.let {
                            resultat.push(it)
                            gjeldendeBeregninger.push(it)
                        }
                    }
                    else -> {
                        /**
                         * I tilfeller hvor endring i ytelse er mindre enn 10%, videreføres gjeldende beregning.
                         * Den videreførte beregningen settes også til ny gjeldende beregning. Siden gjeldende beregning
                         * er gjort for forrige måned, må denne forskyves en måned fram.
                         *
                         * Ved forskyvning vil gjeldende beregning utføres på nytt med grunnbeløp for måneden etter,
                         * noe som fører til at g-regulering implisitt er fritatt for 10% sjekk.
                         */
                        gjeldendeBeregning.forskyv(1).let {
                            resultat.push(it)
                            gjeldendeBeregninger.push(it)
                        }
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
