package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.utenSosialstønad
import java.time.Clock
import java.util.UUID

class BeregningFactory(val clock: Clock) {
    fun ny(
        id: UUID = UUID.randomUUID(),
        opprettet: Tidspunkt = Tidspunkt.now(clock),
        fradrag: List<Fradrag>,
        begrunnelse: String? = null,
        beregningsperioder: List<Beregningsperiode>,
    ): BeregningMedFradragBeregnetMånedsvis {
        if (fradrag.any { it.fradragstype is Fradragstype.AvkortingUtenlandsopphold }) {
            throw IllegalArgumentException("Fradragstype.AvkortingUtenlandsopphold er ikke støttet i nye beregninger.")
        }

        fun beregnMåned(
            måned: Måned,
            fradrag: List<Fradrag>,
            strategy: BeregningStrategy,
        ): BeregningForMåned {
            return MånedsberegningFactory.ny(
                måned = måned,
                strategy = strategy,
                fradrag = fradrag,
            )
        }

        /**
         * Må beregne fradragene fra "scratch" (dvs gjennom å bruke aktuell [FradragStrategy]) uten sosialstønad for å få
         * filtrert vekk eventuell sosialstønad for EPS. Etter at fradragene har vært gjennom [FradragStrategy.beregnFradrag]
         * vil alle EPS sine fradrag være bakt sammen til et element av typen [Fradragstype.BeregnetFradragEPS]
         */
        fun sumYtelseUtenSosialstønad(måned: Måned, strategy: BeregningStrategy): Int {
            return beregnMåned(
                måned = måned,
                fradrag = fradrag.utenSosialstønad(),
                strategy = strategy,
            ).getSumYtelse()
        }

        fun Månedsberegning.sosialstønadFørerTilBeløpUnderToProsentAvHøySats(strategy: BeregningStrategy): Boolean {
            val toProsentAvHøy = fullSupplerendeStønadForMåned.toProsentAvHøyForMånedAsDouble

            // Hvis ytelsen er 2% eller mer av høy sats fører ikke sosialstønad til at vi havner under 2%
            if (getSumYtelse() >= toProsentAvHøy) return false

            // hvis sum uten sosialstønad gjør at vi havner over 2% er det sosialstønad som har skylda
            return sumYtelseUtenSosialstønad(måned = måned, strategy = strategy) >= toProsentAvHøy
        }

        fun Månedsberegning.lagFradragForBeløpUnderMinstegrense() = FradragFactory.periodiser(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.UnderMinstenivå,
                månedsbeløp = getSumYtelse().toDouble(),
                periode = måned,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fun beregn(): Map<Måned, Månedsberegning> {
            val månedTilStrategi: Map<Måned, BeregningStrategy> = beregningsperioder
                .sortedBy { it.periode() }
                .fold(emptyMap()) { acc, beregningsperiode ->
                    acc + beregningsperiode.månedsoversikt()
                }

            return månedTilStrategi.mapValues { (måned, strategi) ->
                beregnMåned(
                    måned = måned,
                    fradrag = fradrag,
                    strategy = strategi,
                ).let { månedsberegning ->
                    when {
                        månedsberegning.sosialstønadFørerTilBeløpUnderToProsentAvHøySats(strategi) -> {
                            månedsberegning.leggTilMerknad(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats)
                            månedsberegning
                        }

                        månedsberegning.beløpStørreEnn0MenMindreEnnToProsentAvHøySats() -> {
                            beregnMåned(
                                måned = måned,
                                fradrag = fradrag + månedsberegning.lagFradragForBeløpUnderMinstegrense(),
                                strategy = strategi,
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

        val månedTilMånedsberegning: Map<Måned, Månedsberegning> = beregn()

        return BeregningMedFradragBeregnetMånedsvis(
            id = id,
            opprettet = opprettet,
            periode = beregningsperioder.map { it.periode() }.minsteAntallSammenhengendePerioder().single(),
            fradrag = fradrag,
            begrunnelse = begrunnelse,
            sumYtelse = månedTilMånedsberegning.values
                .sumOf { it.getSumYtelse() },
            sumFradrag = månedTilMånedsberegning.values
                .sumOf { it.getSumFradrag() },
            månedsberegninger = månedTilMånedsberegning.values.toList().toNonEmptyList(),
        )
    }
}
