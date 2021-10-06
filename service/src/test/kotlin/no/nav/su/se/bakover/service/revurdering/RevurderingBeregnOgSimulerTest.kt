package no.nav.su.se.bakover.service.revurdering

import InkrementerendeKlokke
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.right
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.toMerknadMånedsberegning
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
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
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelse
import org.junit.jupiter.api.Test
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
    fun `beregner uten utgangspunkt dersom det ikke eksisterer gjeldende beregning for måneden forut for revurderingsperioden`() {
        val (sak, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger(
                periode = periode2021,
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = periode2021,
                        arbeidsinntekt = 3500.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurdering
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any(), any()) } doReturn simulertUtbetaling().right()
            on { hentUtbetalinger(any()) } doReturn sak.utbetalinger
        }

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForSakId(any()) } doReturn sak.vedtakListe
        }

        RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
        ).getOrFail().let {
            it.feilmeldinger shouldBe emptyList()
            it.revurdering shouldBe beOfType<SimulertRevurdering.Innvilget>()
            (it.revurdering as SimulertRevurdering.Innvilget).beregning.let { beregning ->
                beregning.merknader()[0] shouldBe Merknad.NyYtelse(
                    benyttetBeregning = beregning.getMånedsberegninger()[0].toMerknadMånedsberegning(),
                )
            }
        }
    }

    @Test
    fun `tar utgangspunkt i eksisterende månedsberegning hvis gjeldende beregning eksisterer for måneden forut for revurderingsperioden`() {
        val revurderingsperiode = Periode.create(1.februar(2021), 31.desember(2021))
        val (sak, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            revurderingsperiode = revurderingsperiode,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger(
                periode = revurderingsperiode,
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        periode = revurderingsperiode,
                        arbeidsinntekt = 3500.0,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            ),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurdering
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any(), any(), any()) } doReturn simulertUtbetaling().right()
            on { hentUtbetalinger(any()) } doReturn sak.utbetalinger
        }

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForSakId(any()) } doReturn sak.vedtakListe
        }

        RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
        ).getOrFail().let {
            it.feilmeldinger shouldBe emptyList()
            it.revurdering shouldBe beOfType<SimulertRevurdering.Innvilget>()
            (it.revurdering as SimulertRevurdering.Innvilget).beregning.let { beregning ->
                beregning.merknader()[0] shouldBe Merknad.RedusertYtelse(
                    benyttetBeregning = Merknad.MerknadMånedsberegning(
                        periode = revurderingsperiode.førsteMåned(),
                        sats = Sats.HØY,
                        grunnbeløp = 101351,
                        beløp = 20946,
                        fradrag = listOf(
                            Merknad.MerknadFradrag(
                                periode = revurderingsperiode.førsteMåned(),
                                fradragstype = Fradragstype.ForventetInntekt,
                                månedsbeløp = 0.0,
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                        satsbeløp = Sats.HØY.månedsbeløp(revurderingsperiode.fraOgMed),
                        fribeløpForEps = 0.0,

                    ),
                    forkastetBeregning = Merknad.MerknadMånedsberegning(
                        periode = revurderingsperiode.førsteMåned(),
                        sats = Sats.HØY,
                        grunnbeløp = 101351,
                        beløp = 17446,
                        fradrag = listOf(
                            Merknad.MerknadFradrag(
                                periode = revurderingsperiode.førsteMåned(),
                                fradragstype = Fradragstype.Arbeidsinntekt,
                                månedsbeløp = 3500.0,
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                        satsbeløp = Sats.HØY.månedsbeløp(revurderingsperiode.fraOgMed),
                        fribeløpForEps = 0.0,
                    ),
                )
            }
        }
    }

    @Test
    fun `en stanset ytelse kan revurderes som vanlig, selv om det ikke er endring i beløpet`() {
        val inkrementerendeKlokke = InkrementerendeKlokke(fixedClock)

        val (sakFørRevurdering, stans) = vedtakIverksattStansAvYtelse(
            periode = periode2021,
            clock = inkrementerendeKlokke,
        )

        val (sakMedRevurdering, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            sakOgVedtakSomKanRevurderes = sakFørRevurdering to stans,
            grunnlagsdataOgVilkårsvurderinger = sakFørRevurdering.hentGjeldendeVilkårOgGrunnlag(
                periode = periode2021,
                clock = inkrementerendeKlokke,
            ),
        )

        revurdering.beregn(
            eksisterendeUtbetalinger = sakMedRevurdering.utbetalinger,
            utgangspunkt = sakMedRevurdering.hentGjeldendeMånedsberegningForEnkeltmåned(revurdering.periode.månedenFør())
                .getOrElse { null },
        ).getOrFail().let {
            it shouldBe beOfType<BeregnetRevurdering.Innvilget>()
        }
    }
}
