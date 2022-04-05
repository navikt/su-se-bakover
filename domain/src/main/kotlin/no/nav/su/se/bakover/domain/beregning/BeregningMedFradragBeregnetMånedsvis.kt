package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.utenAvkorting
import no.nav.su.se.bakover.domain.beregning.fradrag.utenSosialstønad
import java.util.UUID

data class BeregningMedFradragBeregnetMånedsvis(
    private val id: UUID = UUID.randomUUID(),
    private val opprettet: Tidspunkt,
    override val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<Fradrag>,
    private val fradragStrategy: FradragStrategy,
    private val begrunnelse: String?,
    private val beregningsperioder: List<Beregningsperiode>,
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
        val periodeStrategyMap =
            beregningsperioder.fold(emptyMap<Periode, BeregningStrategy>()) { acc, beregningsperiode ->
                acc + beregningsperiode.månedsoversikt()
            }

        return periodeStrategyMap.keys.associateWith { periode ->
            val månedStrategy = periodeStrategyMap[periode]!!
            beregnMåned(
                periode = periode,
                fradrag = fradrag,
                strategy = månedStrategy,
            ).let { månedsberegning ->
                when {
                    månedsberegning.sosialstønadFørerTilBeløpUnderToProsentAvHøySats(månedStrategy) -> {
                        månedsberegning.leggTilMerknad(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats)
                        månedsberegning
                    }
                    månedsberegning.avkortingFørerTilBeløpUnderToProsentAvHøySats(månedStrategy) -> {
                        månedsberegning.leggTilMerknad(Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats)
                        månedsberegning
                    }
                    månedsberegning.beløpStørreEnn0MenMindreEnnToProsentAvHøySats() -> {
                        beregnMåned(
                            periode = periode,
                            fradrag = fradrag + månedsberegning.lagFradragForBeløpUnderMinstegrense(),
                            strategy = månedStrategy,
                        ).let {
                            it.leggTilMerknad(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats)
                            it
                        }
                    }
                    månedsberegning.getSumYtelse() == 0 -> {
                        månedsberegning.leggTilMerknad(Merknad.Beregning.Avslag.BeløpErNull)
                        månedsberegning
                    }
                    else -> {
                        månedsberegning
                    }
                }
            }
        }
    }

    private fun beregnMåned(
        periode: Periode,
        fradrag: List<Fradrag>,
        strategy: BeregningStrategy,
    ): PeriodisertBeregning {
        return PeriodisertBeregning(
            periode = periode,
            sats = strategy.sats(),
            fradrag = strategy.fradragStrategy().beregn(fradrag, periode)[periode] ?: emptyList(),
            fribeløpForEps = strategy.fradragStrategy().getEpsFribeløp(periode),
        )
    }

    private fun Månedsberegning.sosialstønadFørerTilBeløpUnderToProsentAvHøySats(strategy: BeregningStrategy): Boolean {
        // hvis sum er mer enn 2%, er alt good
        if (getSumYtelse() >= Sats.toProsentAvHøy(periode)) return false

        // hvis sum uten avkorting gjør at vi havner under 2% er det sosialstønad som har skylda
        if (sumYtelseUtenAvkorting(periode = periode, strategy = strategy) < Sats.toProsentAvHøy(periode) &&
            sumYtelseUtenSosialstønad(
                    periode = periode,
                    strategy = strategy,
                ) != getSumYtelse() // se om det finnes sosialstønad
        ) return true

        // hvis vi er under 2% og har kommet hit, er det avkorting sin skyld og ikke sosialstønad
        return false
    }

    private fun Månedsberegning.avkortingFørerTilBeløpUnderToProsentAvHøySats(strategy: BeregningStrategy): Boolean {
        // hvis sum er mer enn 2%, er alt good
        if (getSumYtelse() >= Sats.toProsentAvHøy(periode)) return false

        // hvis sum uten avkorting gjør at vi havner under 2% er det sosialstønad som har skylda
        if (sumYtelseUtenAvkorting(periode = periode, strategy = strategy) < Sats.toProsentAvHøy(periode) &&
            sumYtelseUtenSosialstønad(
                    periode = periode,
                    strategy = strategy,
                ) != getSumYtelse() // se om det finnes sosialstønad
        ) return false

        // hvis vi er under 2% og har kommet hit, er det avkorting sin skyld hvis det finnes noen avkorting
        if (sumYtelseUtenAvkorting(periode = periode, strategy = strategy) != getSumYtelse()) return true
        return false
    }

    /**
     * Må beregne fradragene fra "scratch" (dvs gjennom å bruke aktuell [FradragStrategy]) uten sosialstønad for å få
     * filtrert vekk eventuell sosialstønad for EPS. Etter at fradragene har vært gjennom [FradragStrategy.beregnFradrag]
     * vil alle EPS sine fradrag være bakt sammen til et element av typen [Fradragstype.BeregnetFradragEPS]
     */
    private fun sumYtelseUtenSosialstønad(periode: Periode, strategy: BeregningStrategy): Int {
        return beregnMåned(
            periode = periode,
            fradrag = fradrag.utenSosialstønad(),
            strategy = strategy,
        ).getSumYtelse()
    }

    private fun sumYtelseUtenAvkorting(periode: Periode, strategy: BeregningStrategy): Int {
        return beregnMåned(
            periode = periode,
            fradrag = fradrag.utenAvkorting(),
            strategy = strategy,
        ).getSumYtelse()
    }

    private fun Månedsberegning.lagFradragForBeløpUnderMinstegrense() = FradragFactory.periodiser(
        FradragFactory.ny(
            type = Fradragstype.UnderMinstenivå,
            månedsbeløp = getSumYtelse().toDouble(),
            periode = periode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.BRUKER,
        ),
    )

    override fun getSats(): Sats = sats
    override fun getMånedsberegninger(): List<Månedsberegning> = beregning.values.toList()
    override fun getFradrag(): List<Fradrag> = fradrag
    override fun getBegrunnelse(): String? = begrunnelse
    override fun equals(other: Any?) = (other as? Beregning)?.let { this.equals(other) } ?: false
}
