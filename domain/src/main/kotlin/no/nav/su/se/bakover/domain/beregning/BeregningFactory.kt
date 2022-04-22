package no.nav.su.se.bakover.domain.beregning

import arrow.core.Nel
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Månedsperiode
import no.nav.su.se.bakover.common.periode.reduser
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragskategori
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragskategoriWrapper
import no.nav.su.se.bakover.domain.beregning.fradrag.utenAvkorting
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
    ): Beregning {

        fun beregnMåned(
            måned: Månedsperiode,
            fradrag: List<Fradrag>,
            strategy: BeregningStrategy,
        ): PeriodisertBeregning {
            return PeriodisertBeregning(
                måned = måned,
                sats = strategy.sats(),
                fradrag = strategy.fradragStrategy().beregn(fradrag, måned)[måned] ?: emptyList(),
                fribeløpForEps = strategy.fradragStrategy().getEpsFribeløp(måned),
            )
        }

        /**
         * Må beregne fradragene fra "scratch" (dvs gjennom å bruke aktuell [FradragStrategy]) uten sosialstønad for å få
         * filtrert vekk eventuell sosialstønad for EPS. Etter at fradragene har vært gjennom [FradragStrategy.beregnFradrag]
         * vil alle EPS sine fradrag være bakt sammen til et element av typen [Fradragstype.BeregnetFradragEPS]
         */
        fun sumYtelseUtenSosialstønad(måned: Månedsperiode, strategy: BeregningStrategy): Int {
            return beregnMåned(
                måned = måned,
                fradrag = fradrag.utenSosialstønad(),
                strategy = strategy,
            ).getSumYtelse()
        }

        fun sumYtelseUtenAvkorting(måned: Månedsperiode, strategy: BeregningStrategy): Int {
            return beregnMåned(
                måned = måned,
                fradrag = fradrag.utenAvkorting(),
                strategy = strategy,
            ).getSumYtelse()
        }

        fun Månedsberegning.sosialstønadFørerTilBeløpUnderToProsentAvHøySats(strategy: BeregningStrategy): Boolean {
            // hvis sum er mer enn 2%, er alt good
            if (getSumYtelse() >= Sats.toProsentAvHøy(måned)) return false

            // hvis sum uten avkorting gjør at vi havner under 2% er det sosialstønad som har skylda
            if (sumYtelseUtenAvkorting(måned = måned, strategy = strategy) < Sats.toProsentAvHøy(måned) &&
                sumYtelseUtenSosialstønad(
                        måned = måned,
                        strategy = strategy
                    ) != getSumYtelse() // se om det finnes sosialstønad
            ) return true

            // hvis vi er under 2% og har kommet hit, er det avkorting sin skyld og ikke sosialstønad
            return false
        }

        fun Månedsberegning.avkortingFørerTilBeløpUnderToProsentAvHøySats(strategy: BeregningStrategy): Boolean {
            // hvis sum er mer enn 2%, er alt good
            if (getSumYtelse() >= Sats.toProsentAvHøy(måned)) return false

            // hvis sum uten avkorting gjør at vi havner under 2% er det sosialstønad som har skylda
            if (sumYtelseUtenAvkorting(måned = måned, strategy = strategy) < Sats.toProsentAvHøy(måned) &&
                sumYtelseUtenSosialstønad(
                        måned = måned,
                        strategy = strategy
                    ) != getSumYtelse() // se om det finnes sosialstønad
            ) return false

            // hvis vi er under 2% og har kommet hit, er det avkorting sin skyld hvis det finnes noen avkorting
            if (sumYtelseUtenAvkorting(måned, strategy = strategy) != getSumYtelse()) return true
            return false
        }

        fun Månedsberegning.lagFradragForBeløpUnderMinstegrense() = FradragFactory.periodiser(
            FradragFactory.ny(
                fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.UnderMinstenivå),
                månedsbeløp = getSumYtelse().toDouble(),
                periode = måned,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fun beregn(): Map<Månedsperiode, Månedsberegning> {
            val månedsperiodeTilStrategi: Map<Månedsperiode, BeregningStrategy> =
                beregningsperioder.fold(emptyMap()) { acc, beregningsperiode ->
                    acc + beregningsperiode.månedsoversikt()
                }

            return månedsperiodeTilStrategi.mapValues { (måned, strategi) ->
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
                        månedsberegning.avkortingFørerTilBeløpUnderToProsentAvHøySats(strategi) -> {
                            månedsberegning.leggTilMerknad(Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats)
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

        val månedsperiodeTilMånedsberegning: Map<Månedsperiode, Månedsberegning> = beregn()

        return BeregningMedFradragBeregnetMånedsvis(
            id = id,
            opprettet = opprettet,
            periode = beregningsperioder.map { it.periode() }.reduser().single(),
            sats = beregningsperioder.first().sats(),
            fradrag = fradrag,
            // TODO jah+jacob: Denne brukes kun fra brev og bør kunne utledes fra under-periodene
            fradragStrategy = beregningsperioder.first().fradragStrategy(),
            begrunnelse = begrunnelse,
            beregningsperioder = beregningsperioder,
            sumYtelse = månedsperiodeTilMånedsberegning.values
                .sumOf { it.getSumYtelse() },
            sumFradrag = månedsperiodeTilMånedsberegning.values
                .sumOf { it.getSumFradrag() },
            månedsberegninger = Nel.fromListUnsafe(månedsperiodeTilMånedsberegning.values.toList()),
        )
    }
}
