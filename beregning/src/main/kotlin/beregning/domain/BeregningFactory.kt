package beregning.domain

import beregning.domain.fradrag.FradragStrategy
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertBeregning
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertGrunnlag
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
        ): BeregningForMånedRegelspesifisert {
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
        fun ytelseUtenSosialstønad(måned: Måned, strategy: BeregningStrategy): BeregningForMånedRegelspesifisert {
            return beregnMåned(
                måned = måned,
                fradrag = fradrag.utenSosialstønad(),
                strategy = strategy,
            )
        }

        fun BeregningForMånedRegelspesifisert.sosialstønadFørerTilBeløpUnderToProsentAvHøySats(strategy: BeregningStrategy): BeregningUnderToProsent =
            with(verdi) {
                val toProsentAvHøy = fullSupplerendeStønadForMåned.toProsentAvHøyForMåned
                val toProsentAvHøyDouble = toProsentAvHøy.verdi.toDouble()

                // Hvis ytelsen er 2% eller mer av høy sats fører ikke sosialstønad til at vi havner under 2%
                return if (getSumYtelse() >= toProsentAvHøyDouble) {
                    val verdi = false
                    BeregningUnderToProsent(
                        verdi = verdi,
                        benyttetRegel = Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT.benyttRegelspesifisering(
                            verdi = verdi.toString(),
                            avhengigeRegler = listOf(
                                benyttetRegel,
                                toProsentAvHøy.benyttetRegel,
                            ),
                        ),
                    )
                } else {
                    // hvis sum uten sosialstønad gjør at vi havner over 2% er det sosialstønad som har skylda
                    val ytelseUten = ytelseUtenSosialstønad(måned = måned, strategy = strategy)
                    val verdi = ytelseUten.verdi.getSumYtelse() >= toProsentAvHøyDouble
                    BeregningUnderToProsent(
                        verdi = verdi,
                        benyttetRegel = Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT.benyttRegelspesifisering(
                            verdi = verdi.toString(),
                            avhengigeRegler = listOf(
                                benyttetRegel,
                                ytelseUten.benyttetRegel,
                                toProsentAvHøy.benyttetRegel,
                            ),
                        ),
                    )
                }
            }

        fun BeregningForMånedRegelspesifisert.beløpStørreEnn0MenMindreEnnToProsentAvHøySats(): BeregningUnderToProsent =
            with(verdi) {
                val toProsentAvHøyForMåned = fullSupplerendeStønadForMåned.toProsentAvHøyForMåned
                val verdi = getSumYtelse() > 0 && getSumYtelse() < toProsentAvHøyForMåned.verdi.toDouble()
                return BeregningUnderToProsent(
                    verdi = verdi,
                    benyttetRegel = Regelspesifiseringer.REGEL_MINDRE_ENN_2_PROSENT.benyttRegelspesifisering(
                        verdi = verdi.toString(),
                        avhengigeRegler = listOf(
                            benyttetRegel,
                            toProsentAvHøyForMåned.benyttetRegel,
                        ),
                    ),
                )
            }

        fun BeregningForMånedRegelspesifisert.lagFradragForBeløpUnderMinstegrense() = FradragFactory.periodiser(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.UnderMinstenivå,
                månedsbeløp = verdi.getSumYtelse().toDouble(),
                periode = verdi.måned,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        fun beregn(): Map<Måned, BeregningForMånedRegelspesifisert> {
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
                            månedsberegning.verdi.leggTilMerknad(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats)
                            månedsberegning
                        }

                        beløpStørreEnn0MenMindreEnnToProsentAvHøySats.verdi -> {
                            beregnMåned(
                                måned = måned,
                                fradrag = fradrag + månedsberegning.lagFradragForBeløpUnderMinstegrense(),
                                strategy = strategi,
                            ).let {
                                it.verdi.leggTilMerknad(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats)
                                it
                            }
                        }

                        månedsberegning.verdi.getSumYtelse() == 0 -> {
                            månedsberegning.verdi.leggTilMerknad(Merknad.Beregning.Avslag.BeløpErNull)
                            månedsberegning
                        }

                        else -> {
                            månedsberegning
                        }
                    }.let { ny ->
                        BeregningForMånedRegelspesifisert(
                            verdi = ny.verdi,
                            benyttetRegel = Regelspesifiseringer.REGEL_MÅNEDSBEREGNING.benyttRegelspesifisering(
                                verdi = ny.verdi.toString(),
                                avhengigeRegler = listOf(
                                    RegelspesifisertGrunnlag.GRUNNLAG_BOTILSTAND.benyttGrunnlag(strategi.satsgrunn().name),
                                    ny.benyttetRegel,
                                    sosialstønadFørerTilBeløpUnderToProsentAvHøySats.benyttetRegel,
                                    beløpStørreEnn0MenMindreEnnToProsentAvHøySats.benyttetRegel,
                                ),
                            ),
                        )
                    }
                }
            }
        }

        val månedTilMånedsberegning: Map<Måned, BeregningForMånedRegelspesifisert> = beregn()

        return BeregningMedFradragBeregnetMånedsvis(
            id = id,
            opprettet = opprettet,
            periode = beregningsperioder.map { it.periode() }.minsteAntallSammenhengendePerioder().single(),
            fradrag = fradrag,
            begrunnelse = begrunnelse,
            sumYtelse = månedTilMånedsberegning.values
                .sumOf { it.verdi.getSumYtelse() },
            sumFradrag = månedTilMånedsberegning.values
                .sumOf { it.verdi.getSumFradrag() },
            månedsberegninger = månedTilMånedsberegning.values.toList().toNonEmptyList(),
        )
    }
}

data class BeregningUnderToProsent(
    val verdi: Boolean,
    override val benyttetRegel: Regelspesifisering,
) : RegelspesifisertBeregning
