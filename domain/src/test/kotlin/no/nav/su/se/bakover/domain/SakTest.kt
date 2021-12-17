package no.nav.su.se.bakover.domain

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.periodeJanuar2021
import no.nav.su.se.bakover.test.periodeMars2021
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vedtakIverksattGjenopptakAvYtelseFraIverksattStans
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class SakTest {

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
                søknadsbehandlinger = listOf(),
                utbetalinger = listOf(),
                revurderinger = listOf(),
                vedtakListe = listOf(),
            ).hentPerioderMedLøpendeYtelse() shouldBe emptyList()
        }

        @Test
        fun `henter en stønadsperiode`() {
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()

            sak.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                Periode.create(1.januar(2021), 31.desember(2021)),
            )
        }

        @Test
        fun `henter stønadsperioder og justerer varigheten dersom de er delvis opphørt`() {
            val (sak, _) = vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
                sakOgVedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(),
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
            )

            sak.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                Periode.create(1.januar(2021), 30.april(2021)),
            )
        }

        @Test
        fun `henter stønadsperioder for tidligere opphørt periode som er innvilget og revurdert igjen`() {
            val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
                clock = fixedClock
            )

            val (sakEtterOpphør, opphør) = vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sak to vedtak,
                clock = fixedClock.plus(1, ChronoUnit.SECONDS),
            )

            sakEtterOpphør.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                Periode.create(1.januar(2021), 30.april(2021)),
            )

            val (sakEtterNyPeriode, nyPeriode) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periode = Periode.create(1.juni(2021), 31.desember(2021)),
                    begrunnelse = "beg",
                ),
                clock = fixedClock.plus(2, ChronoUnit.SECONDS),
            )

            val sakEtterOpphørOgNyPeriode = sakEtterOpphør.copy(
                revurderinger = sakEtterOpphør.revurderinger + sakEtterNyPeriode.revurderinger,
                søknadsbehandlinger = sakEtterOpphør.søknadsbehandlinger + sakEtterNyPeriode.søknadsbehandlinger,
                vedtakListe = sakEtterOpphør.vedtakListe + sakEtterNyPeriode.vedtakListe,
                utbetalinger = sakEtterOpphør.utbetalinger + sakEtterNyPeriode.utbetalinger,
            )

            val (sakEtterRevurdering, revurdering) = vedtakRevurderingIverksattInnvilget(
                revurderingsperiode = Periode.create(1.november(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakEtterOpphørOgNyPeriode to nyPeriode,
                clock = fixedClock.plus(3, ChronoUnit.SECONDS),
            )

            sakEtterRevurdering.let {
                it.hentPerioderMedLøpendeYtelse() shouldBe listOf(
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
                stønadsperiode = Stønadsperiode.create(
                    periode = Periode.create(1.januar(2023), 31.desember(2023)),
                    begrunnelse = "ny periode da vett",
                ),
            )

            sak.copy(
                søknadsbehandlinger = sak.søknadsbehandlinger + stønadsperiode2.behandling,
                vedtakListe = sak.vedtakListe + stønadsperiode2,
            ).let {
                it.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                    Periode.create(1.januar(2021), 31.desember(2021)),
                    Periode.create(1.januar(2023), 31.desember(2023)),
                )
                it.vedtakListe shouldContainAll listOf(
                    stønadsperiode1,
                    stønadsperiode2,
                )
            }
        }

        @Test
        fun `henter stønadsperioder med revurdering og med opphold mellom`() {
            val (sakStønadsperiode1, stønadsperiode1) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode2021,
            )

            val (sakRevurdering1, revurderingPeriode1) = vedtakRevurderingIverksattInnvilget(
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakStønadsperiode1 to stønadsperiode1,
            )

            val stønadsperiode2023 = Stønadsperiode.create(
                periode = Periode.create(1.januar(2023), 31.desember(2023)),
                begrunnelse = "ny periode da vett",
            )

            val (sakStønadsperiode2, stønadsperiode2) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode2023,
            )

            val (sakRevurdering2, revurderingPeriode2) = vedtakRevurderingIverksattInnvilget(
                stønadsperiode = stønadsperiode2023,
                revurderingsperiode = Periode.create(1.november(2023), 31.desember(2023)),
                sakOgVedtakSomKanRevurderes = sakStønadsperiode2 to stønadsperiode2,
            )

            sakStønadsperiode1.copy(
                søknadsbehandlinger = sakRevurdering1.søknadsbehandlinger + sakRevurdering2.søknadsbehandlinger,
                revurderinger = sakRevurdering1.revurderinger + sakRevurdering2.revurderinger,
                vedtakListe = sakRevurdering1.vedtakListe + sakRevurdering2.vedtakListe,
                utbetalinger = sakRevurdering1.utbetalinger + sakRevurdering2.utbetalinger,
            ).let {
                it.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                    Periode.create(1.januar(2021), 31.desember(2021)),
                    Periode.create(1.januar(2023), 31.desember(2023)),
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
            val (sakFørRevurdering, søknadsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
                clock = fixedClock
            )

            sakFørRevurdering.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                Periode.create(1.januar(2021), 31.desember(2021)),
            )

            val (sakEtterStans, stans) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
                periode = Periode.create(1.februar(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakFørRevurdering to søknadsvedtak,
                clock = fixedClock.plus(1, ChronoUnit.SECONDS)
            )

            sakEtterStans.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                Periode.create(1.januar(2021), 31.desember(2021)),
            )

            val (sakEtterGjenopptak, gjenopptak) = vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
                periode = Periode.create(1.februar(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakEtterStans to stans,
                // Funksjonen vil plusse på 2 selv, slik at vi ikke trenger
                clock = fixedClock,
            )

            sakEtterGjenopptak.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                Periode.create(1.januar(2021), 31.desember(2021)),
            )

            val (sakEtterRevurdering, revurdering) = vedtakRevurderingIverksattInnvilget(
                revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakEtterGjenopptak to gjenopptak,
                clock = fixedClock.plus(3, ChronoUnit.SECONDS),
            )

            sakEtterRevurdering.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                Periode.create(1.januar(2021), 31.desember(2021)),
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
            val (sakStønadsperiode1, stønadsperiode1) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode2021,
            )

            val stønadsperiode2022 = Stønadsperiode.create(
                periode = Periode.create(1.januar(2022), 31.desember(2022)),
                begrunnelse = "ny periode da vett",
            )

            val (sakStønadsperiode2, stønadsperiode2) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode2022,
            )

            sakStønadsperiode1.copy(
                søknadsbehandlinger = sakStønadsperiode1.søknadsbehandlinger + sakStønadsperiode2.søknadsbehandlinger,
                revurderinger = sakStønadsperiode1.revurderinger + sakStønadsperiode2.revurderinger,
                vedtakListe = sakStønadsperiode1.vedtakListe + sakStønadsperiode2.vedtakListe,
                utbetalinger = sakStønadsperiode1.utbetalinger + sakStønadsperiode2.utbetalinger,
            ).let {
                it.hentPerioderMedLøpendeYtelse() shouldBe listOf(
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
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periode = periodeJanuar2021,
                ),
            )

            val (sak2, _) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periode = periodeMars2021,
                ),
            )

            sak.copy(
                utbetalinger = sak.utbetalinger + sak2.utbetalinger
            ).let {
                it.kanUtbetalingerStansesEllerGjenopptas(fixedClock) shouldBe KanStansesEllerGjenopptas.INGEN
            }
        }

        @Test
        fun `utbetalinger kan stanses dersom det er et historisk hull i stønadsperiodene`() {
            val juni2021 = Clock.fixed(1.juni(2021).atTime(0, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periode = periodeJanuar2021,
                ),
            )

            val (sak2, _) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periode = periodeMars2021,
                ),
            )

            sak.copy(
                utbetalinger = sak.utbetalinger + sak2.utbetalinger
            ).let {
                it.kanUtbetalingerStansesEllerGjenopptas(juni2021) shouldBe KanStansesEllerGjenopptas.STANS
            }
        }
    }
}
