package no.nav.su.se.bakover.domain

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.vedtakIverksattGjenopptakAvYtelseFraIverksattStans
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SakTest {

    @Test
    fun `henter tom liste dersom ingen eksisterer`() {
        Sak(
            id = UUID.randomUUID(),
            saksnummer = saksnummer,
            opprettet = no.nav.su.se.bakover.test.fixedTidspunkt,
            fnr = Fnr.generer(),
            søknader = listOf(),
            søknadsbehandlinger = listOf(),
            utbetalinger = listOf(),
            revurderinger = listOf(),
            vedtakListe = listOf(),
        ).hentAktiveStønadsperioder() shouldBe emptyList()
    }

    @Test
    fun `henter en stønadsperiode`() {
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()

        sak.hentAktiveStønadsperioder() shouldBe listOf(
            Periode.create(1.januar(2021), 31.desember(2021)),
        )
    }

    @Test
    fun `henter stønadsperioder og justerer varigheten dersom de er delvis opphørt`() {
        val (sak, _) = vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
            sakOgVedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(),
            revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
        )

        sak.hentAktiveStønadsperioder() shouldBe listOf(
            Periode.create(1.januar(2021), 30.april(2021)),
        )
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
            it.hentAktiveStønadsperioder() shouldBe listOf(
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
    fun `henter stønadsperioder som har blitt revurdert`() {
        val (sakFørRevurdering, søknadsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget()

        sakFørRevurdering.hentAktiveStønadsperioder() shouldBe listOf(
            Periode.create(1.januar(2021), 31.desember(2021)),
        )

        val (sakEtterStans, stans) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = Periode.create(1.februar(2021), 31.desember(2021)),
            sakOgVedtakSomKanRevurderes = sakFørRevurdering to søknadsvedtak,
        )

        sakEtterStans.hentAktiveStønadsperioder() shouldBe listOf(
            Periode.create(1.januar(2021), 31.desember(2021)),
        )

        val (sakEtterGjenopptak, gjenopptak) = vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
            periode = Periode.create(1.februar(2021), 31.desember(2021)),
            sakOgVedtakSomKanRevurderes = sakEtterStans to stans,
        )

        sakEtterGjenopptak.hentAktiveStønadsperioder() shouldBe listOf(
            Periode.create(1.januar(2021), 31.desember(2021)),
        )

        val (sakEtterRevurdering, revurdering) = vedtakRevurderingIverksattInnvilget(
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
            sakOgVedtakSomKanRevurderes = sakEtterGjenopptak to gjenopptak,
        )

        sakEtterRevurdering.hentAktiveStønadsperioder() shouldBe listOf(
            Periode.create(1.januar(2021), 31.desember(2021)),
        )
        sakEtterRevurdering.vedtakListe shouldContainAll listOf(
            søknadsvedtak,
            stans,
            gjenopptak,
            revurdering,
        )
    }
}
