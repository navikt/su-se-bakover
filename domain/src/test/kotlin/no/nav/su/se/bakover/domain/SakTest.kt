package no.nav.su.se.bakover.domain

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.sak.nySøknadsbehandling
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.vedtakIverksattGjenopptakAvYtelseFraIverksattStans
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class SakTest {

    @Test
    fun `henter åpne søknadsbehandlinger`() {
        val sakMedÅpenBehandling = søknadsbehandlingVilkårsvurdertUavklart().first
        sakMedÅpenBehandling.hentÅpneSøknadsbehandlinger().shouldNotBeEmpty()
        sakMedÅpenBehandling.harÅpenSøknadsbehandling() shouldBe true

        val sakUtenÅpenBehandling = iverksattSøknadsbehandlingUføre().first
        sakUtenÅpenBehandling.hentÅpneSøknadsbehandlinger().shouldBeEmpty()
        sakUtenÅpenBehandling.harÅpenSøknadsbehandling() shouldBe false
    }

    @Nested
    inner class HentPerioderMedLøpendeYtelse {
        @Test
        fun `henter tom liste dersom ingen eksisterer`() {
            Sak(
                id = UUID.randomUUID(),
                saksnummer = saksnummer,
                opprettet = fixedTidspunkt,
                fnr = Fnr.generer(),
                søknader = listOf(),
                utbetalinger = Utbetalinger(),
                vedtakListe = listOf(),
                type = Sakstype.UFØRE,
                uteståendeAvkorting = Avkortingsvarsel.Ingen,
                versjon = Hendelsesversjon(1),
            ).hentIkkeOpphørtePerioder() shouldBe emptyList()
        }

        @Test
        fun `henter en stønadsperiode`() {
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()

            sak.hentIkkeOpphørtePerioder() shouldBe listOf(
                år(2021),
            )
        }

        @Test
        fun `henter stønadsperioder og justerer varigheten dersom de er delvis opphørt`() {
            val clock = TikkendeKlokke(fixedClock)
            val (sak, _) = vedtakRevurdering(
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(clock = clock),
                vilkårOverrides = listOf(
                    avslåttUførevilkårUtenGrunnlag(
                        periode = Periode.create(
                            1.mai(2021),
                            31.desember(2021),
                        ),
                    ),
                ),
                clock = clock,
            )

            sak.hentIkkeOpphørtePerioder() shouldBe listOf(
                Periode.create(1.januar(2021), 30.april(2021)),
            )
        }

        @Test
        fun `henter stønadsperioder for tidligere opphørt periode som er innvilget og revurdert igjen`() {
            val tikkendeKlokke = TikkendeKlokke(fixedClock)
            val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
                clock = tikkendeKlokke,
            )

            val (sakEtterOpphør, opphør) = vedtakRevurdering(
                clock = tikkendeKlokke,
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sak to vedtak,
                vilkårOverrides = listOf(
                    avslåttUførevilkårUtenGrunnlag(
                        periode = Periode.create(
                            1.mai(2021),
                            31.desember(2021),
                        ),
                    ),
                ),
            )

            sakEtterOpphør.hentIkkeOpphørtePerioder() shouldBe listOf(
                Periode.create(1.januar(2021), 30.april(2021)),
            )

            val (sakEtterNyPeriode, _, nyPeriode) = iverksattSøknadsbehandling(
                sakOgSøknad = sakEtterOpphør to nySøknadJournalførtMedOppgave(
                    sakId = sakEtterOpphør.id,
                    søknadInnhold = søknadinnholdUføre(personopplysninger = Personopplysninger(sakEtterOpphør.fnr)),
                ),
                stønadsperiode = Stønadsperiode.create(periode = Periode.create(1.juni(2021), 31.desember(2021))),
                clock = tikkendeKlokke,
            )

            val (sakEtterRevurdering, revurdering) = vedtakRevurderingIverksattInnvilget(
                revurderingsperiode = Periode.create(1.november(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakEtterNyPeriode to nyPeriode as VedtakInnvilgetSøknadsbehandling,
                clock = tikkendeKlokke,
            )

            sakEtterRevurdering.let {
                it.hentIkkeOpphørtePerioder() shouldBe listOf(
                    Periode.create(1.januar(2021), 30.april(2021)),
                    Periode.create(1.juni(2021), 31.desember(2021)),
                )
                it.vedtakListe shouldContainAll listOf(
                    vedtak,
                    opphør,
                    nyPeriode,
                    revurdering,
                )
            }
        }

        @Test
        fun `henter stønadsperioder med opphold mellom`() {
            val (sak, stønadsperiode1) = vedtakSøknadsbehandlingIverksattInnvilget()

            val (_, stønadsperiode2) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(periode = år(2023)),
            )

            sak.nySøknadsbehandling(stønadsperiode2.behandling)
                .copy(
                    vedtakListe = sak.vedtakListe + stønadsperiode2,
                ).let {
                    it.hentIkkeOpphørtePerioder() shouldBe listOf(
                        år(2021),
                        år(2023),
                    )
                    it.vedtakListe shouldContainAll listOf(
                        stønadsperiode1,
                        stønadsperiode2,
                    )
                }
        }

        @Test
        fun `henter stønadsperioder med revurdering og med opphold mellom`() {
            val clock = TikkendeKlokke()
            val (sakStønadsperiode1, stønadsperiode1) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode2021,
                clock = clock,
            )

            val (sakRevurdering1, revurderingPeriode1) = vedtakRevurderingIverksattInnvilget(
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakStønadsperiode1 to stønadsperiode1,
                clock = clock,
            )

            val stønadsperiode2023 = Stønadsperiode.create(periode = år(2023))

            val (sakStønadsperiode2, stønadsperiode2) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode2023,
                clock = clock,
                sakOgSøknad = sakRevurdering1 to nySøknadJournalførtMedOppgave(
                    sakId = sakStønadsperiode1.id,
                    søknadInnhold = søknadinnholdUføre(),
                ),
            )

            val (sakRevurdering2, revurderingPeriode2) = vedtakRevurderingIverksattInnvilget(
                stønadsperiode = stønadsperiode2023,
                revurderingsperiode = Periode.create(1.november(2023), 31.desember(2023)),
                sakOgVedtakSomKanRevurderes = sakStønadsperiode2 to stønadsperiode2,
                clock = clock,
            )

            sakRevurdering2.let {
                it.hentIkkeOpphørtePerioder() shouldBe listOf(
                    år(2021),
                    år(2023),
                )
                it.vedtakListe shouldContainAll listOf(
                    stønadsperiode1,
                    revurderingPeriode1,
                    stønadsperiode2,
                    revurderingPeriode2,
                )
            }
        }

        @Test
        fun `henter stønadsperioder som har blitt revurdert`() {
            val clock = tikkendeFixedClock()
            val (sakFørRevurdering, søknadsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
                clock = clock,
            )

            sakFørRevurdering.hentIkkeOpphørtePerioder() shouldBe listOf(
                år(2021),
            )

            val (sakEtterStans, stans) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
                periode = Periode.create(1.februar(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakFørRevurdering to søknadsvedtak,
                clock = clock,
            )

            sakEtterStans.hentIkkeOpphørtePerioder() shouldBe listOf(
                år(2021),
            )

            val (sakEtterGjenopptak, gjenopptak) = vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
                periode = Periode.create(1.februar(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakEtterStans to stans,
                clock = clock,
            )

            sakEtterGjenopptak.hentIkkeOpphørtePerioder() shouldBe listOf(
                år(2021),
            )

            val (sakEtterRevurdering, revurdering) = vedtakRevurderingIverksattInnvilget(
                revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakEtterGjenopptak to gjenopptak,
                clock = clock,
            )

            sakEtterRevurdering.hentIkkeOpphørtePerioder() shouldBe listOf(
                år(2021),
            )
            sakEtterRevurdering.vedtakListe shouldContainAll listOf(
                søknadsvedtak,
                stans,
                gjenopptak,
                revurdering,
            )
        }

        @Test
        fun `slår sammen stønadsperioder som kommer etter hverandre`() {
            val clock = TikkendeKlokke()
            val (sakStønadsperiode1, stønadsperiode1) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode2021,
                clock = clock,
            )

            val stønadsperiode2022 = Stønadsperiode.create(periode = år(2022))

            val (sakStønadsperiode2, stønadsperiode2) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode2022,
                clock = clock,
                sakOgSøknad = sakStønadsperiode1 to nySøknadJournalførtMedOppgave(
                    sakId = sakStønadsperiode1.id,
                    søknadInnhold = søknadinnholdUføre(),
                ),
            )

            sakStønadsperiode2.let {
                it.hentIkkeOpphørtePerioder() shouldBe listOf(
                    Periode.create(1.januar(2021), 31.desember(2022)),
                )
                it.vedtakListe shouldContainAll listOf(
                    stønadsperiode1,
                    stønadsperiode2,
                )
            }
        }
    }

    @Nested
    inner class KanUtbetalingerStansesEllerGjenopptas {
        @Test
        fun `utbetalinger kan stanses på en sak med en standard stønadsperiode`() {
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()

            sak.kanUtbetalingerStansesEllerGjenopptas(fixedClock) shouldBe KanStansesEllerGjenopptas.STANS
        }

        @Test
        fun `utbetalinger kan ikke stanses dersom det er fremtidig hull i stønadsperiodene`() {
            val clock = TikkendeKlokke()
            val (førHull, _) = iverksattSøknadsbehandlingUføre(
                stønadsperiode = Stønadsperiode.create(
                    periode = januar(2021),
                ),
                clock = clock,
            )

            val (etterHull, _) = iverksattSøknadsbehandlingUføre(
                stønadsperiode = Stønadsperiode.create(
                    periode = mars(2021),
                ),
                sakOgSøknad = førHull to nySøknadJournalførtMedOppgave(
                    clock = fixedClock,
                    sakId = førHull.id,
                    søknadInnhold = søknadinnholdUføre(
                        personopplysninger = Personopplysninger(førHull.fnr),
                    ),
                ),
                clock = clock,
            )

            etterHull.kanUtbetalingerStansesEllerGjenopptas(fixedClock) shouldBe KanStansesEllerGjenopptas.INGEN
        }

        @Test
        fun `utbetalinger kan stanses dersom det er et historisk hull i stønadsperiodene`() {
            val clock = TikkendeKlokke()
            val juni2021 = Clock.fixed(1.juni(2021).atTime(0, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
            val (førHull, _) = iverksattSøknadsbehandlingUføre(
                stønadsperiode = Stønadsperiode.create(
                    periode = januar(2021),
                ),
                clock = clock,
            )

            val (etterHull, _) = iverksattSøknadsbehandlingUføre(
                stønadsperiode = Stønadsperiode.create(
                    periode = mars(2021),
                ),
                sakOgSøknad = førHull to nySøknadJournalførtMedOppgave(
                    clock = fixedClock,
                    sakId = førHull.id,
                    søknadInnhold = søknadinnholdUføre(
                        personopplysninger = Personopplysninger(førHull.fnr),
                    ),
                ),
                clock = clock,
            )

            etterHull.kanUtbetalingerStansesEllerGjenopptas(juni2021) shouldBe KanStansesEllerGjenopptas.STANS
        }

        @Test
        fun `harGjeldendeEllerFremtidigStønadsperiode skal returnere true om det man er i en stønadsperiode`() {
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periode = januar(2021),
                ),
            )
            sak.harGjeldendeEllerFremtidigStønadsperiode(fixedClock) shouldBe true
        }

        @Test
        fun `harGjeldendeEllerFremtidigStønadsperiode skal returnere true om det er en stønadsperiode i fremtiden`() {
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periode = mars(2021),
                ),
            )
            sak.harGjeldendeEllerFremtidigStønadsperiode(fixedClock) shouldBe true
        }

        @Test
        fun `harGjeldendeEllerFremtidigStønadsperiode skal returnere false om det ikke er noen fremtidige stønadsperioder`() {
            val juni2021 = Clock.fixed(1.juni(2021).atTime(0, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periode = januar(2021),
                ),
            )
            sak.harGjeldendeEllerFremtidigStønadsperiode(juni2021) shouldBe false
        }
    }

    @Nested
    inner class YtelseUtløperVedUtløpAv {

        @Test
        fun `utløp av ytelse`() {
            val stønadsperiode = Stønadsperiode.create(år(2021))
            val revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021))

            vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode,
            ).let { (sak, _) ->
                sak.ytelseUtløperVedUtløpAv(januar(2021)) shouldBe false
                sak.ytelseUtløperVedUtløpAv(desember(2021)) shouldBe true
                sak.ytelseUtløperVedUtløpAv(år(2021)) shouldBe true
                sak.ytelseUtløperVedUtløpAv(Periode.create(1.januar(2021), 31.desember(2025))) shouldBe false
            }

            // opphørt ytelse oppfattes ikke som utløpende
            vedtakRevurdering(
                stønadsperiode = stønadsperiode,
                revurderingsperiode = revurderingsperiode,
                vilkårOverrides = listOf(
                    avslåttUførevilkårUtenGrunnlag(
                        periode = revurderingsperiode,
                    ),
                ),
            ).let { (sak, vedtak) ->
                vedtak shouldBe beOfType<VedtakOpphørMedUtbetaling>()

                stønadsperiode.periode.måneder().none {
                    sak.ytelseUtløperVedUtløpAv(it)
                } shouldBe true

                revurderingsperiode.måneder().none {
                    sak.ytelseUtløperVedUtløpAv(it)
                } shouldBe true
            }
        }
    }

    @Nested
    inner class HentGjeldendeMånedsberegninger {
        @Test
        fun `henter gjeldende månedsberegninger for enkelt vedtak`() {
            iverksattSøknadsbehandlingUføre().also { (sak, _, vedtak) ->
                sak.hentGjeldendeMånedsberegninger(
                    periode = mai(2021)..juli(2021),
                    clock = fixedClock,
                ).also { gjeldendeMånedsberegninger ->
                    vedtak.shouldBeType<VedtakInnvilgetSøknadsbehandling>().also {
                        gjeldendeMånedsberegninger shouldBe listOf(
                            it.beregning.getMånedsberegninger()[4],
                            it.beregning.getMånedsberegninger()[5],
                            it.beregning.getMånedsberegninger()[6],
                        )
                    }
                }
            }
        }

        @Test
        fun `henter gjeldende månedsberegninger med hull mellom vedtak`() {
            val tikkendeKlokke = TikkendeKlokke(fixedClock)
            iverksattSøknadsbehandlingUføre(
                clock = tikkendeKlokke,
                stønadsperiode = Stønadsperiode.create(januar(2021)..november(2021)),
            ).also { (sak1, _, vedtak1) ->
                iverksattSøknadsbehandlingUføre(
                    clock = tikkendeKlokke,
                    stønadsperiode = Stønadsperiode.create(januar(2022)..november(2022)),
                    sakOgSøknad = sak1 to nySøknadJournalførtMedOppgave(
                        clock = tikkendeKlokke,
                        sakId = sak1.id,
                        søknadInnhold = søknadinnholdUføre(
                            personopplysninger = Personopplysninger(sak1.fnr),
                        ),
                    ),
                ).also { (sak2, _, vedtak2) ->
                    sak2.hentGjeldendeMånedsberegninger(
                        periode = november(2021)..januar(2022),
                        clock = fixedClock,
                    ).also { gjeldendeMånedsberegninger ->
                        vedtak2.shouldBeType<VedtakInnvilgetSøknadsbehandling>().also {
                            gjeldendeMånedsberegninger shouldBe listOf(
                                (vedtak1 as VedtakInnvilgetSøknadsbehandling).beregning.getMånedsberegninger()[10],
                                it.beregning.getMånedsberegninger()[0],
                            )
                        }
                    }
                }
            }
        }

        @Test
        fun `henter gjeldende månedsberegninger fra tidligere vedtak hvis ytelse er stanset`() {
            val clock = tikkendeFixedClock()
            iverksattSøknadsbehandlingUføre(
                clock = clock,
            ).also { (sak, _, vedtakSøknadsbehandling) ->
                vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
                    sakOgVedtakSomKanRevurderes = sak to vedtakSøknadsbehandling as VedtakSomKanRevurderes,
                    clock = clock,
                ).also { (sak2, vedtakStans) ->
                    sak2.hentGjeldendeMånedsberegninger(
                        periode = mai(2021)..juli(2021),
                        clock = fixedClock,
                    ).also { gjeldendeMånedsberegninger ->
                        vedtakSøknadsbehandling.shouldBeType<VedtakInnvilgetSøknadsbehandling>()
                            .also { søknadsbehandling ->
                                gjeldendeMånedsberegninger shouldBe listOf(
                                    søknadsbehandling.beregning.getMånedsberegninger()[4].also {
                                        sak2.hentGjeldendeVedtaksdata(it.måned, fixedClock)
                                            .getOrFail().gjeldendeVedtakPåDato(it.måned.fraOgMed) shouldBe vedtakStans
                                    },
                                    søknadsbehandling.beregning.getMånedsberegninger()[5].also {
                                        sak2.hentGjeldendeVedtaksdata(it.måned, fixedClock)
                                            .getOrFail().gjeldendeVedtakPåDato(it.måned.fraOgMed) shouldBe vedtakStans
                                    },
                                    søknadsbehandling.beregning.getMånedsberegninger()[6].also {
                                        sak2.hentGjeldendeVedtaksdata(it.måned, fixedClock)
                                            .getOrFail().gjeldendeVedtakPåDato(it.måned.fraOgMed) shouldBe vedtakStans
                                    },
                                )
                            }
                        vedtakStans.shouldBeType<VedtakStansAvYtelse>()
                    }
                }
            }
        }
    }

    @Nested
    inner class HistoriskVedtaksdataForVedtaksperiode {
        @Test
        fun `henter tidligere informasjon for overlappende vedtak`() {
            val clock = TikkendeKlokke(fixedClock)

            val sakOgVedtak1 = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(år(2021)),
                clock = clock,
            )
            val sakOgVedtak2 = vedtakRevurderingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(år(2021)),
                sakOgVedtakSomKanRevurderes = sakOgVedtak1,
                clock = clock,
            )
            val (sak, _) = vedtakRevurderingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(år(2021)),
                sakOgVedtakSomKanRevurderes = sakOgVedtak2,
                clock = clock,
            )

            sak.historiskGrunnlagForVedtaketsPeriode(
                vedtakId = sakOgVedtak2.second.id,
                clock = clock,
            ) shouldBe GjeldendeVedtaksdata(
                periode = år(2021),
                vedtakListe = nonEmptyListOf(sakOgVedtak1.second),
                clock = clock,
            ).right()
        }

        @Test
        fun `henter tidligere informasjon for vedtak - ingen tidligere vedtak for periode`() {
            val clock = TikkendeKlokke(fixedClock)

            val (sak1, _, vedtak1) = iverksattSøknadsbehandlingUføre(
                clock = clock,
                stønadsperiode = Stønadsperiode.create(år(2021)),
            )

            sak1.historiskGrunnlagForVedtaketsPeriode(
                vedtakId = vedtak1.id,
                clock = fixedClock,
            ) shouldBe Sak.KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.IngenTidligereVedtak.left()
        }
    }
}
