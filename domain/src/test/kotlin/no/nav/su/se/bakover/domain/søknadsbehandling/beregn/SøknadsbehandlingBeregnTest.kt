package no.nav.su.se.bakover.domain.søknadsbehandling.beregn

import arrow.core.nonEmptyListOf
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import io.kotest.matchers.types.shouldNotBeTypeOf
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.common.extensions.startOfMonth
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.FRITEKST_TIL_BREV
import no.nav.su.se.bakover.domain.søknadsbehandling.KanBeregnes
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.beregnetAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.beregnetInnvilget
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksattAvslagBeregning
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksattAvslagVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksattInnvilget
import no.nav.su.se.bakover.domain.søknadsbehandling.lukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.opprettet
import no.nav.su.se.bakover.domain.søknadsbehandling.simulert
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttesteringAvslagBeregning
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttesteringAvslagVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttesteringInnvilget
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjentAvslagBeregning
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjentAvslagVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjentInnvilget
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkårsvurdertAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingshendelse
import no.nav.su.se.bakover.test.nåtidForSimuleringStub
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simuleringOpphørt
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class SøknadsbehandlingBeregnTest {

    @Test
    fun `kan beregne for vilkårsvurdert innvilget`() {
        val vilkårsvurdertInnvilget = vilkårsvurdertInnvilget
        vilkårsvurdertInnvilget.beregn(
            begrunnelse = "123",
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
            uteståendeAvkortingPåSak = null,
        ).getOrFail().let {
            it shouldBe beOfType<BeregnetSøknadsbehandling.Innvilget>()
            it.saksbehandler shouldBe saksbehandler
            it.beregning.getBegrunnelse() shouldBe "123"
            it.søknadsbehandlingsHistorikk shouldBe vilkårsvurdertInnvilget.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.Beregnet),
                ),
            )
        }
    }

    @Test
    fun `kan beregne på nytt for beregnet innvilget`() {
        val beregnetInnvilget = beregnetInnvilget
        beregnetInnvilget.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
            uteståendeAvkortingPåSak = null,
        ).getOrFail() shouldBe beregnetInnvilget.copy(
            søknadsbehandlingsHistorikk = beregnetInnvilget.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.Beregnet),
                ),
            ),
        )
    }

    @Test
    fun `kan beregne på nytt for beregnet avslag`() {
        val beregnetAvslag = beregnetAvslag
        beregnetAvslag.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
            uteståendeAvkortingPåSak = null,
        ).getOrFail() shouldBe beregnetAvslag.copy(
            søknadsbehandlingsHistorikk = beregnetAvslag.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.Beregnet),
                ),
            ),
        )
    }

    @Test
    fun `kan beregne på nytt for simulert`() {
        val simulert = simulert
        simulert.beregn(
            begrunnelse = "123",
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
            uteståendeAvkortingPåSak = null,
        ).getOrFail().let {
            it shouldBe beOfType<BeregnetSøknadsbehandling.Innvilget>()
            it.saksbehandler shouldBe saksbehandler
            it.beregning.getBegrunnelse() shouldBe "123"
            it.fritekstTilBrev shouldBe ""
            it.attesteringer shouldBe Attesteringshistorikk.empty()
            it.søknadsbehandlingsHistorikk shouldBe simulert.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.Beregnet),
                ),
            )
        }
    }

    @Test
    fun `kan beregne på nytt underkjent avslag med beregning`() {
        val underkjentAvslagBeregning = underkjentAvslagBeregning
        underkjentAvslagBeregning.beregn(
            begrunnelse = null,
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
            uteståendeAvkortingPåSak = null,
        ).getOrFail().let {
            it shouldBe beOfType<BeregnetSøknadsbehandling.Avslag>()
            it.saksbehandler shouldBe saksbehandler
            it.fritekstTilBrev shouldBe FRITEKST_TIL_BREV
            it.attesteringer shouldBe Attesteringshistorikk.create(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering()))
            it.søknadsbehandlingsHistorikk shouldBe underkjentAvslagBeregning.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.Beregnet),
                ),
            )
        }
    }

    @Test
    fun `ulovlige statusoverganger`() {
        listOf(
            opprettet,
            vilkårsvurdertAvslag,
            tilAttesteringAvslagBeregning,
            tilAttesteringAvslagVilkår,
            tilAttesteringInnvilget,
            underkjentAvslagVilkår,
            iverksattAvslagBeregning,
            iverksattAvslagVilkår,
            iverksattInnvilget,
            lukketSøknadsbehandling,
        ).forEach {
            it.shouldNotBeTypeOf<KanBeregnes>()
        }
    }

    @Test
    fun `kan beregne på nytt underkjent innvilgelse med beregning`() {
        val underkjentInnvilget = underkjentInnvilget
        underkjentInnvilget.beregn(
            begrunnelse = "123",
            clock = fixedClock,
            satsFactory = satsFactoryTestPåDato(),
            nySaksbehandler = saksbehandler,
            uteståendeAvkortingPåSak = null,
        ).getOrFail().let {
            it shouldBe beOfType<BeregnetSøknadsbehandling.Innvilget>()
            it.saksbehandler shouldBe saksbehandler
            it.beregning.getBegrunnelse() shouldBe "123"
            it.fritekstTilBrev shouldBe underkjentInnvilget.fritekstTilBrev
            it.attesteringer shouldBe Attesteringshistorikk.create(listOf(underkjentAvslagBeregning.attesteringer.hentSisteAttestering()))
            it.søknadsbehandlingsHistorikk shouldBe underkjentInnvilget.søknadsbehandlingsHistorikk.leggTilNyeHendelser(
                nonEmptyListOf(
                    nySøknadsbehandlingshendelse(handling = SøknadsbehandlingsHandling.Beregnet),
                ),
            )
        }
    }

    @Test
    fun `beregner uten avkorting`() {
        søknadsbehandlingVilkårsvurdertInnvilget().let { (_, førBeregning) ->
            førBeregning.beregn(
                begrunnelse = "kakota",
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
                nySaksbehandler = saksbehandler,
                uteståendeAvkortingPåSak = null,
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
            vilkårsvurdert.oppdaterFradragsgrunnlag(
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
                saksbehandler = saksbehandler,
                clock = fixedClock,
            )
        }.getOrFail().let { førBeregning ->
            førBeregning.beregn(
                begrunnelse = "kakota",
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
                nySaksbehandler = saksbehandler,
                uteståendeAvkortingPåSak = null,
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
            vilkårsvurdert.oppdaterFradragsgrunnlag(
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
                saksbehandler = saksbehandler,
                clock = fixedClock,
            )
        }.getOrFail().let { førBeregning ->
            førBeregning.beregn(
                begrunnelse = "kakota",
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
                nySaksbehandler = saksbehandler,
                uteståendeAvkortingPåSak = null,
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
        val clock = TikkendeKlokke()
        val antallMånederMedFeilutbetaling = 3L
        val eksisterendeUtbetalinger = Utbetalinger(oversendtUtbetalingMedKvittering(clock = clock))
        val expectedAvkortingBeløp =
            eksisterendeUtbetalinger.utbetalingslinjer.sumOf { it.beløp } * antallMånederMedFeilutbetaling.toDouble()

        søknadsbehandlingVilkårsvurdertInnvilget(
            stønadsperiode = stønadsperiode2021,
            clock = clock,
        ).let { (_, førBeregning) ->
            førBeregning.beregn(
                begrunnelse = "kakota",
                clock = clock,
                satsFactory = satsFactoryTestPåDato(),
                nySaksbehandler = saksbehandler,
                uteståendeAvkortingPåSak = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                    id = UUID.randomUUID(),
                    sakId = førBeregning.id,
                    revurderingId = UUID.randomUUID(),
                    opprettet = førBeregning.opprettet,
                    simulering = simuleringOpphørt(
                        clock = clock,
                        opphørsperiode = Periode.create(
                            fraOgMed = LocalDate.now(nåtidForSimuleringStub).startOfMonth()
                                .minusMonths(antallMånederMedFeilutbetaling),
                            tilOgMed = stønadsperiode2021.periode.tilOgMed,
                        ),
                        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                        fnr = førBeregning.fnr,
                        sakId = førBeregning.sakId,
                        saksnummer = førBeregning.saksnummer,
                    ),
                ).skalAvkortes(),
            ).getOrFail().let { etterBeregning ->
                etterBeregning.beregning.getFradrag() shouldHaveSize 4
                etterBeregning.beregning.getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold } shouldHaveSize 3
                etterBeregning.beregning.getSumFradrag() shouldBe expectedAvkortingBeløp.plusOrMinus(0.5)
                etterBeregning.beregning.getSumYtelse() shouldBe førBeregning.periode.måneder()
                    .sumOf { satsFactoryTestPåDato().høyUføre(it).satsForMånedAvrundet } - expectedAvkortingBeløp
                etterBeregning.grunnlagsdata.fradragsgrunnlag.filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe expectedAvkortingBeløp.plusOrMinus(0.5)
            }
        }
    }

    @Test
    fun `fjerner evt gammelt grunnlag for avkorting dersom avkorting skal beregnes på nytt`() {
        val clock = TikkendeKlokke()
        val antallMånederMedFeilutbetaling = 3L
        val eksisterendeUtbetalinger = Utbetalinger(oversendtUtbetalingMedKvittering(clock = clock))
        val expectedAvkortingBeløp =
            eksisterendeUtbetalinger.utbetalingslinjer.sumOf { it.beløp } * antallMånederMedFeilutbetaling.toDouble()

        søknadsbehandlingVilkårsvurdertInnvilget(
            stønadsperiode = stønadsperiode2021,
            clock = clock,
        ).let { (_, vilkårsvurdert) ->
            vilkårsvurdert.oppdaterFradragsgrunnlag(
                fradragsgrunnlag = listOf(
                    Grunnlag.Fradragsgrunnlag.create(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                            månedsbeløp = 5000.0,
                            periode = vilkårsvurdert.periode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                ),
                saksbehandler = saksbehandler,
                clock = clock,
            ).getOrFail().beregn(
                begrunnelse = "kakota",
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
                nySaksbehandler = saksbehandler,
                uteståendeAvkortingPåSak = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                    id = UUID.randomUUID(),
                    sakId = vilkårsvurdert.id,
                    revurderingId = UUID.randomUUID(),
                    opprettet = vilkårsvurdert.opprettet,
                    simulering = simuleringOpphørt(
                        opphørsperiode = Periode.create(
                            fraOgMed = LocalDate.now(nåtidForSimuleringStub).startOfMonth()
                                .minusMonths(antallMånederMedFeilutbetaling),
                            tilOgMed = stønadsperiode2021.periode.tilOgMed,
                        ),
                        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
                        fnr = vilkårsvurdert.fnr,
                        sakId = vilkårsvurdert.sakId,
                        saksnummer = vilkårsvurdert.saksnummer,
                        clock = clock,
                    ),
                ).skalAvkortes(),
            ).getOrFail().let { etterBeregning ->
                etterBeregning.beregning.getFradrag() shouldHaveSize 4
                etterBeregning.beregning.getFradrag()
                    .filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold } shouldHaveSize 3
                etterBeregning.beregning.getSumFradrag() shouldBe expectedAvkortingBeløp.plusOrMinus(0.5)
                etterBeregning.beregning.getSumYtelse() shouldBe vilkårsvurdert.periode.måneder()
                    .sumOf { satsFactoryTestPåDato().høyUføre(it).satsForMånedAvrundet } - expectedAvkortingBeløp
                etterBeregning.grunnlagsdata.fradragsgrunnlag.filter { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
                    .sumOf { it.månedsbeløp } shouldBe expectedAvkortingBeløp.plusOrMinus(0.5)
            }
        }
    }
}
