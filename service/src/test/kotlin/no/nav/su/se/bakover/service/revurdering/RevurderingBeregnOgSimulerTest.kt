package no.nav.su.se.bakover.service.revurdering

import TikkendeKlokke
import arrow.core.getOrHandle
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class RevurderingBeregnOgSimulerTest {

    @Test
    fun `legger ved feilmeldinger for tilfeller som ikke støttes`() {
        val (sak, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger(
                periode = periode2021,
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periode2021,
                        arbeidsinntekt = 150500.0,
                    ),
                ),
                uføreVilkår = avslåttUførevilkårUtenGrunnlag(
                    periode = periode2021,
                ),
            ),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurdering
        }
        val simulertUtbetaling = mock<Utbetaling.SimulertUtbetaling> {
            on { simulering } doReturn mock()
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerOpphør(any(), any(), any()) } doReturn simulertUtbetaling.right()
            on { hentUtbetalinger(any()) } doReturn sak.utbetalinger
        }
        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForSakId(any()) } doReturn sak.vedtakListe
        }

        val response = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
        ).getOrHandle { fail("Skulle returnert en instans av ${RevurderingOgFeilmeldingerResponse::class}") }

        response.feilmeldinger shouldBe listOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
        )
    }

    @Test
    fun `kaster exception dersom vi ikke finner gjeldende månedsberegning for første måned i revurderingen`() {
        val (sak, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak()

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurdering
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { hentUtbetalinger(any()) } doReturn sak.utbetalinger
        }

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForSakId(any()) } doReturn emptyList()
        }

        assertThrows<IllegalStateException> {
            val response = RevurderingTestUtils.createRevurderingService(
                revurderingRepo = revurderingRepoMock,
                utbetalingService = utbetalingServiceMock,
                vedtakRepo = vedtakRepoMock,
            ).beregnOgSimuler(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
            ).getOrHandle { fail("Skulle returnert en instans av ${RevurderingOgFeilmeldingerResponse::class}") }

            response.feilmeldinger shouldBe listOf(
                RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
            )
        }
    }

    @Test
    fun `en stanset ytelse kan revurderes som vanlig, selv om det ikke er endring i beløpet`() {
        val tikkendeKlokke = TikkendeKlokke(fixedClock)

        val (sakFørRevurdering, stans) = vedtakIverksattStansAvYtelse(
            periode = periode2021,
            clock = tikkendeKlokke,
        )

        val (sakMedRevurdering, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            sakOgVedtakSomKanRevurderes = sakFørRevurdering to stans,
            grunnlagsdataOgVilkårsvurderinger = sakFørRevurdering.hentGjeldendeVilkårOgGrunnlag(
                periode = periode2021,
                clock = tikkendeKlokke,
            ),
        )

        revurdering.beregn(
            eksisterendeUtbetalinger = sakMedRevurdering.utbetalinger,
            månedsberegning = sakMedRevurdering.hentGjeldendeMånedsberegningForEnkeltmåned(revurdering.periode.førsteMåned())
                .getOrFail(),
        ).getOrFail().let {
            it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
        }
    }
}
