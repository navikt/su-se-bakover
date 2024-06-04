package no.nav.su.se.bakover.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder
import no.nav.su.se.bakover.common.domain.tid.periode.IkkeOverlappendePerioder
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.vedtakIverksattGjenopptakAvYtelseFraIverksattStans
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattOpphør
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test

internal class Sak_hentInnvilgetStønadsperioderTest {

    @Test
    fun `enkel innvilget periode`() {
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()
        sak.hentInnvilgetStønadsperioder() shouldBe IkkeOverlappendePerioder.Companion.create(listOf(år(2021)))
    }

    @Test
    fun `sammenhengende dobbel innvilget periode`() {
        val clock = TikkendeKlokke()
        val (sakFørste, _) = vedtakSøknadsbehandlingIverksattInnvilget(clock = clock)
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(år(2022)),
            sakOgSøknad = sakFørste to nySøknadJournalførtMedOppgave(
                sakId = sakFørste.id,
                søknadInnhold = søknadinnholdUføre(personopplysninger = Personopplysninger(sakFørste.fnr)),
                clock = clock,
            ),
        )
        sak.hentInnvilgetStønadsperioder() shouldBe IkkeOverlappendePerioder.Companion.create(
            listOf(
                år(2021),
                år(2022),
            ),
        )
    }

    @Test
    fun `to stønadsperioder med hull`() {
        val clock = TikkendeKlokke()
        val (sakFørste, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(januar(2021)),
        )
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(mars(2021)),
            sakOgSøknad = sakFørste to nySøknadJournalførtMedOppgave(
                sakId = sakFørste.id,
                søknadInnhold = søknadinnholdUføre(personopplysninger = Personopplysninger(sakFørste.fnr)),
                clock = clock,
            ),
        )
        sak.hentInnvilgetStønadsperioder() shouldBe IkkeOverlappendePerioder.Companion.create(
            listOf(
                januar(2021),
                mars(2021),
            ),
        )
    }

    @Test
    fun `fullstendig opphør gir EmptyPeriode`() {
        val clock = TikkendeKlokke()
        val (sakFørste, søknadsbehandling) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(januar(2021)),
        )
        val (sak, _) = vedtakRevurderingIverksattOpphør(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(januar(2021)),
            revurderingsperiode = januar(2021),
            sakOgVedtakSomKanRevurderes = sakFørste to søknadsbehandling,
        )
        sak.hentInnvilgetStønadsperioder() shouldBe EmptyPerioder
    }

    @Test
    fun `fullstendig innvilgelse reverserer fullstendig opphør`() {
        val clock = TikkendeKlokke()
        val (sakFørste, søknadsbehandling) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(januar(2021)),
        )
        val (sakEtterRevurdering, _) = vedtakRevurderingIverksattOpphør(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(januar(2021)),
            revurderingsperiode = januar(2021),
            sakOgVedtakSomKanRevurderes = sakFørste to søknadsbehandling,
        )
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
            sakOgSøknad = sakEtterRevurdering to nySøknadJournalførtMedOppgave(
                sakId = sakFørste.id,
                søknadInnhold = søknadinnholdUføre(personopplysninger = Personopplysninger(sakFørste.fnr)),
                clock = clock,
            ),
            stønadsperiode = Stønadsperiode.create(januar(2021)),
        )
        sak.hentInnvilgetStønadsperioder() shouldBe IkkeOverlappendePerioder.Companion.create(
            listOf(januar(2021)),
        )
    }

    @Test
    fun `tar ikke med stans`() {
        val clock = TikkendeKlokke()
        val (sakFørste, søknadsbehandling) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(januar(2021)),
        )
        val (sak, _) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = clock,
            periode = januar(2021),
            sakOgVedtakSomKanRevurderes = sakFørste to søknadsbehandling,
        )
        sak.hentInnvilgetStønadsperioder() shouldBe EmptyPerioder
    }

    @Test
    fun `tar ikke med stans deler av perioden`() {
        val clock = TikkendeKlokke(fixedClockAt(1.mars(2021)))
        val (sakFørste, søknadsbehandling) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(januar(2021)..mars(2021)),
        )
        val (sak, _) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = clock,
            periode = mars(2021),
            sakOgVedtakSomKanRevurderes = sakFørste to søknadsbehandling,
        )
        sak.hentInnvilgetStønadsperioder() shouldBe IkkeOverlappendePerioder.Companion.create(
            listOf(januar(2021)..februar(2021)),
        )
    }

    @Test
    fun `tar med gjenoppta`() {
        val clock = TikkendeKlokke(fixedClockAt(1.mars(2021)))
        val (sakFørste, innvilgetSøknadsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
            stønadsperiode = Stønadsperiode.create(januar(2021)..mars(2021)),
        )
        val (stansetSak, stansetVedtak) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = clock,
            periode = mars(2021),
            sakOgVedtakSomKanRevurderes = sakFørste to innvilgetSøknadsvedtak,
        )
        val (sak, _) = vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
            clock = clock,
            periode = mars(2021),
            sakOgVedtakSomKanRevurderes = stansetSak to stansetVedtak,
        )
        sak.hentInnvilgetStønadsperioder() shouldBe IkkeOverlappendePerioder.Companion.create(
            listOf(januar(2021)..februar(2021), mars(2021)),
        )
    }
}
