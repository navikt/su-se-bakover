package beregning.domain

import beregning.domain.fradrag.FradragStrategy
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertBeregning
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.utenSosialstønad
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

        fun Månedsberegning.sosialstønadFørerTilBeløpUnderToProsentAvHøySats(strategy: BeregningStrategy): BeregningUnderToProsent {
            val toProsentAvHøy = fullSupplerendeStønadForMåned.toProsentAvHøyForMåned
            val toProsentAvHøyDouble = toProsentAvHøy.verdi.toDouble()

            // Hvis ytelsen er 2% eller mer av høy sats fører ikke sosialstønad til at vi havner under 2%
            return if (getSumYtelse() >= toProsentAvHøyDouble) {
                false
            } else {
                // hvis sum uten sosialstønad gjør at vi havner over 2% er det sosialstønad som har skylda
                sumYtelseUtenSosialstønad(måned = måned, strategy = strategy) >= toProsentAvHøyDouble
            }.let {
                BeregningUnderToProsent(
                    verdi = it,
                    benyttetRegel = Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT.benyttRegelspesifisering(
                        avhengigeRegler = listOf(toProsentAvHøy.benyttetRegel),
                    ),
                )
            }
        }

        fun Månedsberegning.beløpStørreEnn0MenMindreEnnToProsentAvHøySats(): BeregningUnderToProsent {
            val toProsentAvHøyForMåned = fullSupplerendeStønadForMåned.toProsentAvHøyForMåned
            return BeregningUnderToProsent(
                verdi = getSumYtelse() > 0 && getSumYtelse() < toProsentAvHøyForMåned.verdi.toDouble(),
                benyttetRegel = Regelspesifiseringer.REGEL_MINDRE_ENN_2_PROSENT.benyttRegelspesifisering(
                    avhengigeRegler = listOf(toProsentAvHøyForMåned.benyttetRegel),
                ),
            )
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
                .fold<Beregningsperiode, Map<Måned, BeregningStrategy>>(emptyMap()) { acc, beregningsperiode ->
                    acc + beregningsperiode.månedsoversikt()
                }.toSortedMap()

            return månedTilStrategi.mapValues { (måned, strategi) ->
                beregnMåned(
                    måned = måned,
                    fradrag = fradrag,
                    strategy = strategi,
                ).let { månedsberegning ->

                    val sosialstønadFørerTilBeløpUnderToProsentAvHøySats =
                        månedsberegning.sosialstønadFørerTilBeløpUnderToProsentAvHøySats(strategi)
                    val beløpStørreEnn0MenMindreEnnToProsentAvHøySats =
                        månedsberegning.beløpStørreEnn0MenMindreEnnToProsentAvHøySats()

                    when {
                        sosialstønadFørerTilBeløpUnderToProsentAvHøySats.verdi -> {
                            månedsberegning.leggTilMerknad(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats)
                            månedsberegning
                        }

                        beløpStørreEnn0MenMindreEnnToProsentAvHøySats.verdi -> {
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
                    }.copy(
                        benyttetRegel = (månedsberegning.benyttetRegel as Regelspesifisering.Beregning).let {
                            it.copy(
                                avhengigeRegler = it.avhengigeRegler + listOf(
                                    sosialstønadFørerTilBeløpUnderToProsentAvHøySats.benyttetRegel,
                                    beløpStørreEnn0MenMindreEnnToProsentAvHøySats.benyttetRegel,
                                ),
                            )
                        },
                    )
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

data class BeregningUnderToProsent(
    val verdi: Boolean,
    override val benyttetRegel: Regelspesifisering,
) : RegelspesifisertBeregning
