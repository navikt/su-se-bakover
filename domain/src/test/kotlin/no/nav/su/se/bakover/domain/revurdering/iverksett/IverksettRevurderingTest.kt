package no.nav.su.se.bakover.domain.revurdering.iverksett

import arrow.core.left
import arrow.core.right
import dokument.domain.KunneIkkeLageDokument
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.oppdrag.simulering.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.dokumentUtenMetadataVedtak
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.ikkeSendBrev
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.simulering.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simulering.simulertMånedFeilutbetalingVedOpphør
import org.junit.jupiter.api.Test
import økonomi.domain.utbetaling.Utbetaling

class IverksettRevurderingTest {

    @Test
    fun `kan ikke iverksette hvis attestants simulering er forskjellig`() {
        val clock = TikkendeKlokke()
        val mai = mai(2021)
        val revurderingsperiode = april(2021)..mai
        val (sak, revurdering) = revurderingTilAttestering(
            clock = clock,
            revurderingsperiode = revurderingsperiode,
            grunnlagsdataOverrides = listOf(
                // Tvinger fram et opphør.
                fradragsgrunnlagArbeidsinntekt(periode = revurderingsperiode, arbeidsinntekt = 100000.0),
            ),
            utbetalingerKjørtTilOgMed = { 1.juni(2021) },
        )
        val simulering = revurdering.simulering!!
        sak.iverksettRevurdering(
            revurderingId = revurdering.id,
            attestant = attestant,
            clock = clock,
            simuler = { utbetalingForSimulering: Utbetaling.UtbetalingForSimulering ->
                // Denne vil kjøres 2 ganger, en for april-mai og en for april-desember.
                val aprilFraFørsteSimulering = simulering.måneder.first()
                Utbetaling.SimulertUtbetaling(
                    simulering = simuleringFeilutbetaling(
                        gjelderId = sak.fnr,
                        simulertePerioder = listOf(
                            // April er uendet.
                            aprilFraFørsteSimulering,
                            simulertMånedFeilutbetalingVedOpphør(
                                måned = mai,
                                // Øker beløpet med 1000 siden saksbehandlers simulering for å emulere enn større feilutbetaling.
                                tidligereBeløp = 21946,
                            ),
                        ),
                    ),
                    utbetalingForSimulering = utbetalingForSimulering,
                ).right()
            },
            genererPdf = { dokumentUtenMetadataVedtak().right() },
            satsFactory = satsFactoryTestPåDato(),
            fritekst = "",
        ) shouldBe KunneIkkeIverksetteRevurdering.Saksfeil.KontrollsimuleringFeilet(
            no.nav.su.se.bakover.domain.oppdrag.simulering.KontrollsimuleringFeilet.Forskjeller(
                KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikFeilutbetaling,
            ),
        ).left()
    }

    @Test
    fun `genererer dokument når brev skal sendes`() {
        val clock = TikkendeKlokke()
        val (sak, revurdering) = revurderingTilAttestering(clock = clock)
        revurdering.shouldBeTypeOf<RevurderingTilAttestering.Innvilget>()

        var genererPdfKall = 0
        val response = sak.iverksettRevurdering(
            revurderingId = revurdering.id,
            attestant = attestant,
            clock = clock,
            simuler = { utbetalingForSimulering ->
                simulerUtbetaling(
                    utbetalingerPåSak = sak.utbetalinger,
                    utbetalingForSimulering = utbetalingForSimulering,
                    clock = clock,
                )
            },
            genererPdf = {
                genererPdfKall++
                dokumentUtenMetadataVedtak().right()
            },
            satsFactory = satsFactoryTestPåDato(),
            fritekst = "",
        ).getOrFail()

        genererPdfKall shouldBe 1
        response.dokument.shouldNotBeNull()
    }

    @Test
    fun `genererer ikke dokument når brev ikke skal sendes`() {
        val clock = TikkendeKlokke()
        val (sak, revurdering) = revurderingTilAttestering(
            clock = clock,
            brevvalg = ikkeSendBrev(),
        )
        revurdering.shouldBeTypeOf<RevurderingTilAttestering.Innvilget>()

        var genererPdfKall = 0
        val response = sak.iverksettRevurdering(
            revurderingId = revurdering.id,
            attestant = attestant,
            clock = clock,
            simuler = { utbetalingForSimulering ->
                simulerUtbetaling(
                    utbetalingerPåSak = sak.utbetalinger,
                    utbetalingForSimulering = utbetalingForSimulering,
                    clock = clock,
                )
            },
            genererPdf = {
                genererPdfKall++
                dokumentUtenMetadataVedtak().right()
            },
            satsFactory = satsFactoryTestPåDato(),
            fritekst = "",
        ).getOrFail()

        genererPdfKall shouldBe 0
        response.dokument.shouldBeNull()
    }

    @Test
    fun `mapper feil fra dokumentgenerering til saksfeil`() {
        val clock = TikkendeKlokke()
        val revurderingsperiode = april(2021)..mai(2021)
        val (sak, revurdering) = revurderingTilAttestering(
            clock = clock,
            revurderingsperiode = revurderingsperiode,
            grunnlagsdataOverrides = listOf(
                // Tvinger fram et opphør.
                fradragsgrunnlagArbeidsinntekt(periode = revurderingsperiode, arbeidsinntekt = 100000.0),
            ),
            utbetalingerKjørtTilOgMed = { 1.juni(2021) },
        )
        revurdering.shouldBeTypeOf<RevurderingTilAttestering.Opphørt>()

        sak.iverksettRevurdering(
            revurderingId = revurdering.id,
            attestant = attestant,
            clock = clock,
            simuler = { utbetalingForSimulering ->
                simulerUtbetaling(
                    utbetalingerPåSak = sak.utbetalinger,
                    utbetalingForSimulering = utbetalingForSimulering,
                    clock = clock,
                )
            },
            genererPdf = { KunneIkkeLageDokument.FeilVedGenereringAvPdf.left() },
            satsFactory = satsFactoryTestPåDato(),
            fritekst = "",
        ) shouldBe KunneIkkeIverksetteRevurdering.Saksfeil.KunneIkkeGenerereDokument(
            KunneIkkeLageDokument.FeilVedGenereringAvPdf,
        ).left()
    }
}
