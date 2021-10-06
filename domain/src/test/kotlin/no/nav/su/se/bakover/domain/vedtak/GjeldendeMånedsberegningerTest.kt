package no.nav.su.se.bakover.domain.vedtak

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vedtakIngenEndringFraInnvilgetSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakIverksattGjenopptakAvYtelse
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelse
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class GjeldendeMånedsberegningerTest {

    @Test
    fun `finner månedesberegninger fra både søknadsvedtak og revurdering`() {
        val søknadsbehandling = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = stønadsperiode2021,
        )

        val revurdering = vedtakRevurderingIverksattInnvilget(
            revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
            sakOgVedtakSomKanRevurderes = søknadsbehandling,
            clock = fixedClock.plus(1, ChronoUnit.DAYS),
        )

        GjeldendeMånedsberegninger(
            periode = periode2021,
            vedtakListe = listOf(
                søknadsbehandling.second,
                revurdering.second,
            ),
        ).let {
            it.månedsberegninger shouldHaveSize 12
            it.månedsberegninger[0] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[0]
            it.månedsberegninger[4] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[4]
            it.månedsberegninger[5] shouldBe revurdering.second.beregning.getMånedsberegninger()[1]
            it.månedsberegninger[11] shouldBe revurdering.second.beregning.getMånedsberegninger()[7]
        }
    }

    @Test
    fun `finner månedesberegninger fra både søknadsvedtak og revurdering hvis periode er revurdert flere ganger`() {
        val søknadsbehandling = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = stønadsperiode2021,
        )

        val revurdering = vedtakRevurderingIverksattInnvilget(
            revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
            sakOgVedtakSomKanRevurderes = søknadsbehandling,
            clock = fixedClock.plus(1, ChronoUnit.DAYS),
        )

        val revurdering2 = vedtakRevurderingIverksattInnvilget(
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
            sakOgVedtakSomKanRevurderes = søknadsbehandling,
            clock = fixedClock.plus(1, ChronoUnit.DAYS),
        )

        GjeldendeMånedsberegninger(
            periode = periode2021,
            vedtakListe = listOf(
                søknadsbehandling.second,
                revurdering.second,
                revurdering2.second,
            ),
        ).let {
            it.månedsberegninger shouldHaveSize 12
            it.månedsberegninger[0] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[0]
            it.månedsberegninger[4] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[4]
            it.månedsberegninger[5] shouldBe revurdering.second.beregning.getMånedsberegninger()[1]
            it.månedsberegninger[11] shouldBe revurdering.second.beregning.getMånedsberegninger()[7]
        }
    }

    @Test
    fun `finner månedsberegninger fra originalt søknadsvedtak selv om det er midlertidig stanset`() {
        val søknadsbehandling = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = stønadsperiode2021,
        )

        val stans = vedtakIverksattStansAvYtelse(
            periode = Periode.create(
                fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
                tilOgMed = periode2021.tilOgMed,
            ),
        )

        GjeldendeMånedsberegninger(
            periode = periode2021,
            vedtakListe = listOf(
                søknadsbehandling.second,
                stans.second,
            ),
        ).let {
            it.månedsberegninger shouldHaveSize 12
            it.månedsberegninger[0] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[0]
            it.månedsberegninger[4] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[4]
            it.månedsberegninger[5] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[5]
            it.månedsberegninger[11] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[11]
        }
    }

    @Test
    fun `finner månedsberegninger fra originalt søknadsvedtak selv om det er midlertidig stanset og gjenopptatt`() {
        val søknadsbehandling = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = stønadsperiode2021,
        )

        val stans = vedtakIverksattStansAvYtelse(
            periode = Periode.create(
                fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
                tilOgMed = periode2021.tilOgMed,
            ),
        )

        val gjenopptak = vedtakIverksattGjenopptakAvYtelse(
            periode = Periode.create(
                fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
                tilOgMed = periode2021.tilOgMed,
            ),
        )

        GjeldendeMånedsberegninger(
            periode = periode2021,
            vedtakListe = listOf(
                søknadsbehandling.second,
                stans.second,
                gjenopptak.second,
            ),
        ).let {
            it.månedsberegninger shouldHaveSize 12
            it.månedsberegninger[0] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[0]
            it.månedsberegninger[4] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[4]
            it.månedsberegninger[5] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[5]
            it.månedsberegninger[11] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[11]
        }
    }

    @Test
    fun `finner månedesberegninger fra både søknadsvedtak og revurdering selv om de er stanset`() {
        val søknadsbehandling = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = stønadsperiode2021,
        )

        val revurdering = vedtakRevurderingIverksattInnvilget(
            revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
            sakOgVedtakSomKanRevurderes = søknadsbehandling,
            clock = fixedClock.plus(1, ChronoUnit.DAYS),
        )

        val stans = vedtakIverksattStansAvYtelse(
            periode = Periode.create(
                fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
                tilOgMed = periode2021.tilOgMed,
            ),
        )

        GjeldendeMånedsberegninger(
            periode = periode2021,
            vedtakListe = listOf(
                søknadsbehandling.second,
                revurdering.second,
                stans.second,
            ),
        ).let {
            it.månedsberegninger shouldHaveSize 12
            it.månedsberegninger[0] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[0]
            it.månedsberegninger[4] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[4]
            it.månedsberegninger[5] shouldBe revurdering.second.beregning.getMånedsberegninger()[1]
            it.månedsberegninger[11] shouldBe revurdering.second.beregning.getMånedsberegninger()[7]
        }
    }

    @Test
    fun `finner månedsberegninger fra originalt søknadsvedtak selv om man har mange senere vedtak uten endringer`() {
        val søknadsbehandling = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = stønadsperiode2021,
        )

        val ingenEndring1 = vedtakIngenEndringFraInnvilgetSøknadsbehandlingsvedtak()
        val ingenEndring2 = vedtakIngenEndringFraInnvilgetSøknadsbehandlingsvedtak()

        GjeldendeMånedsberegninger(
            periode = periode2021,
            vedtakListe = listOf(
                søknadsbehandling.second,
                ingenEndring1.second,
                ingenEndring2.second,
            ),
        ).let {
            it.månedsberegninger shouldHaveSize 12
            it.månedsberegninger[0] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[0]
            it.månedsberegninger[4] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[4]
            it.månedsberegninger[5] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[5]
            it.månedsberegninger[11] shouldBe søknadsbehandling.second.beregning.getMånedsberegninger()[11]
        }
    }
}
