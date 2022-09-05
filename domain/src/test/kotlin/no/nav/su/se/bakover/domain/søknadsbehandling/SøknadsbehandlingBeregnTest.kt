package no.nav.su.se.bakover.domain.søknadsbehandling

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nåtidForSimuleringStub
import no.nav.su.se.bakover.test.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simuleringOpphørt
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class SøknadsbehandlingBeregnTest {
    @Test
    fun `beregner uten avkorting`() {
        søknadsbehandlingVilkårsvurdertInnvilget().let { (_, førBeregning) ->
            førBeregning.beregn(
                begrunnelse = "kakota",
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let { etterBeregning ->
                etterBeregning.beregning.getFradrag() shouldHaveSize 1
                etterBeregning.beregning.getSumFradrag() shouldBe 0.0
                etterBeregning.beregning.getSumYtelse() shouldBe førBeregning.periode.måneder()
                    .sumOf { satsFactoryTestPåDato().høyUføre(it).satsForMånedAvrundet }
                etterBeregning.beregning.getBegrunnelse() shouldBe "kakota"
                etterBeregning.grunnlagsdata shouldBe førBeregning.grunnlagsdata
            }
        }
    }

    @Test
    fun `fjerner evt gammelt grunnlag for avkorting dersom ingen avkorting skal finne sted`() {
        søknadsbehandlingVilkårsvurdertInnvilget().let { (_, vilkårsvurdert) ->
            vilkårsvurdert.leggTilFradragsgrunnlag(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 15000.0,
                            periode = vilkårsvurdert.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            )
        }.getOrFail().let { førBeregning ->
            førBeregning.beregn(
                begrunnelse = "kakota",
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let { etterBeregning ->
                etterBeregning.beregning.getFradrag() shouldHaveSize 1
                etterBeregning.beregning.getSumFradrag() shouldBe 0
                etterBeregning.beregning.getSumYtelse() shouldBe førBeregning.periode.måneder()
                    .sumOf { satsFactoryTestPåDato().høyUføre(it).satsForMånedAvrundet }
                etterBeregning.grunnlagsdata shouldNotBe førBeregning.grunnlagsdata
            }
        }
    }

    @Test
    fun `beregner med fradrag`() {
        søknadsbehandlingVilkårsvurdertInnvilget().let { (_, vilkårsvurdert) ->
            vilkårsvurdert.leggTilFradragsgrunnlag(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 15000.0,
                            periode = vilkårsvurdert.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            )
        }.getOrFail().let { førBeregning ->
            førBeregning.beregn(
                begrunnelse = "kakota",
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let { etterBeregning ->
                etterBeregning.beregning.getFradrag() shouldHaveSize 2
                etterBeregning.beregning.getSumFradrag() shouldBe førBeregning.periode.getAntallMåneder() * 15000
                etterBeregning.beregning.getSumYtelse() shouldBe førBeregning.periode.måneder()
                    .sumOf { satsFactoryTestPåDato().høyUføre(it).satsForMånedAvrundet - 15000 }
                etterBeregning.grunnlagsdata shouldBe førBeregning.grunnlagsdata
            }
        }
    }

    @Test
    fun `beregner med avkorting`() {
        val antallMånederMedFeilutbetaling = 3L
        val eksisterendeUtbetalinger = listOf(oversendtUtbetalingMedKvittering())
        val expectedAvkortingBeløp = eksisterendeUtbetalinger.flatMap { it.utbetalingslinjer }
            .sumOf { it.beløp } * antallMånederMedFeilutbetaling.toDouble()

        søknadsbehandlingVilkårsvurdertInnvilget(
            stønadsperiode = stønadsperiode2021
        ).let { (_, førBeregning) ->
            førBeregning.copy(
                avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                    Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        id = UUID.randomUUID(),
                        sakId = førBeregning.id,
                        revurderingId = UUID.randomUUID(),
                        opprettet = førBeregning.opprettet,
                        simulering = simuleringOpphørt(
                            opphørsperiode = Periode.create(
                                fraOgMed = LocalDate.now(nåtidForSimuleringStub).startOfMonth().minusMonths(antallMånederMedFeilutbetaling),
                                tilOgMed = stønadsperiode2021.periode.tilOgMed
                            ),
                            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                            fnr = førBeregning.fnr,
                            sakId = førBeregning.sakId,
                            saksnummer = førBeregning.saksnummer,
                            clock = fixedClock,
                        ),
                    ).skalAvkortes(),
                ),
            ).beregn(
                begrunnelse = "kakota",
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let { etterBeregning ->
                etterBeregning.beregning.getFradrag() shouldHaveSize 4
                etterBeregning.beregning.getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold } shouldHaveSize 3
                etterBeregning.beregning.getSumFradrag() shouldBe expectedAvkortingBeløp.plusOrMinus(0.5)
                etterBeregning.beregning.getSumYtelse() shouldBe førBeregning.periode.måneder()
                    .sumOf { satsFactoryTestPåDato().høyUføre(it).satsForMånedAvrundet } - expectedAvkortingBeløp
                etterBeregning.grunnlagsdata.fradragsgrunnlag
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe expectedAvkortingBeløp.plusOrMinus(0.5)
            }
        }
    }

    @Test
    fun `fjerner evt gammelt grunnlag for avkorting dersom avkorting skal beregnes på nytt`() {
        val antallMånederMedFeilutbetaling = 3L
        val eksisterendeUtbetalinger = listOf(oversendtUtbetalingMedKvittering())
        val expectedAvkortingBeløp = eksisterendeUtbetalinger.flatMap { it.utbetalingslinjer }
            .sumOf { it.beløp } * antallMånederMedFeilutbetaling.toDouble()

        søknadsbehandlingVilkårsvurdertInnvilget(
            stønadsperiode = stønadsperiode2021
        ).let { (_, vilkårsvurdert) ->
            vilkårsvurdert.leggTilFradragsgrunnlag(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 5000.0,
                            periode = vilkårsvurdert.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
            ).getOrFail().copy(
                avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                    Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        id = UUID.randomUUID(),
                        sakId = vilkårsvurdert.id,
                        revurderingId = UUID.randomUUID(),
                        opprettet = vilkårsvurdert.opprettet,
                        simulering = simuleringOpphørt(
                            opphørsperiode = Periode.create(
                                fraOgMed = LocalDate.now(nåtidForSimuleringStub).startOfMonth().minusMonths(antallMånederMedFeilutbetaling),
                                tilOgMed = stønadsperiode2021.periode.tilOgMed
                            ),
                            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                            fnr = vilkårsvurdert.fnr,
                            sakId = vilkårsvurdert.sakId,
                            saksnummer = vilkårsvurdert.saksnummer,
                            clock = fixedClock,
                        ),
                    ).skalAvkortes(),
                ),
            ).beregn(
                begrunnelse = "kakota",
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let { etterBeregning ->
                etterBeregning.beregning.getFradrag() shouldHaveSize 4
                etterBeregning.beregning.getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold } shouldHaveSize 3
                etterBeregning.beregning.getSumFradrag() shouldBe expectedAvkortingBeløp.plusOrMinus(0.5)
                etterBeregning.beregning.getSumYtelse() shouldBe vilkårsvurdert.periode.måneder()
                    .sumOf { satsFactoryTestPåDato().høyUføre(it).satsForMånedAvrundet } - expectedAvkortingBeløp
                etterBeregning.grunnlagsdata.fradragsgrunnlag
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe expectedAvkortingBeløp.plusOrMinus(0.5)
            }
        }
    }
}
