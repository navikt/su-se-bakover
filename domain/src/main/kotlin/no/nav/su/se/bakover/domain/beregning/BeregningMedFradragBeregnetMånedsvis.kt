package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.utenSosialstønadOgAvkorting
import java.util.UUID

data class BeregningMedFradragBeregnetMånedsvis(
    private val id: UUID = UUID.randomUUID(),
    private val opprettet: Tidspunkt,
    override val periode: Periode,
    private val sats: Sats,
    private val fradrag: List<Fradrag>,
    private val fradragStrategy: FradragStrategy,
    private val begrunnelse: String?,
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
        val perioder = periode.tilMånedsperioder()

        return perioder.associateWith { periode ->
            beregnMåned(
                periode = periode,
                fradrag = fradrag,
            ).let { månedsberegning ->
                when {
                    månedsberegning.sosialstønadOgAvkortingFørerTilBeløpUnderToProsentAvHøySats(månedsberegning.periode) -> {
                        månedsberegning.leggTilMerknad(Merknad.Beregning.SosialstønadOgAvkortingFørerTilBeløpLavereEnnToProsentAvHøySats)
                        månedsberegning
                    }
                    månedsberegning.beløpStørreEnn0MenMindreEnnToProsentAvHøySats() -> {
                        beregnMåned(
                            periode = periode,
                            fradrag = fradrag + månedsberegning.lagFradragForBeløpUnderMinstegrense(),
                        ).let {
                            it.leggTilMerknad(Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats)
                            it
                        }
                    }
                    månedsberegning.getSumYtelse() == 0 -> {
                        månedsberegning.leggTilMerknad(Merknad.Beregning.BeløpErNull)
                        månedsberegning
                    }
                    else -> månedsberegning
                }
            }
        }
    }

    private fun beregnMåned(
        periode: Periode,
        fradrag: List<Fradrag>,
    ): PeriodisertBeregning {
        return PeriodisertBeregning(
            periode = periode,
            sats = sats,
            fradrag = fradragStrategy.beregn(fradrag, periode)[periode] ?: emptyList(),
            fribeløpForEps = fradragStrategy.getEpsFribeløp(periode),
        )
    }

    private fun Månedsberegning.sosialstønadOgAvkortingFørerTilBeløpUnderToProsentAvHøySats(periode: Periode): Boolean {
        return getSumYtelse() < Sats.toProsentAvHøy(periode) &&
            sumYtelseUtenSosialstønadOgAvkorting(periode) >= Sats.toProsentAvHøy(periode)
    }

    /**
     * Må beregne fradragene fra "scratch" (dvs gjennom å bruke aktuell [FradragStrategy]) uten sosialstønad for å få
     * filtrert vekk eventuell sosialstønad for EPS. Etter at fradragene har vært gjennom [FradragStrategy.beregnFradrag]
     * vil alle EPS sine fradrag være bakt sammen til et element av typen [Fradragstype.BeregnetFradragEPS]
     */
    private fun sumYtelseUtenSosialstønadOgAvkorting(periode: Periode): Int {
        return beregnMåned(
            periode = periode,
            fradrag = fradrag.utenSosialstønadOgAvkorting(),
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
