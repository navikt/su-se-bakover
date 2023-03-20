package no.nav.su.se.bakover.domain.vedtak

import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mapSecond
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakRevurdering
import org.junit.jupiter.api.Test

class StønadsvedtakTest {

    @Test
    fun `vedtak for innvilget revurdering med årsak g-regulering skal sende brev`() {
        // Tidligere skulle vi ikke sende brev ved revurdering med årsak REGULER_GRUNNBELØP, men nå gir vi heller saksbehandler valget.
        VedtakSomKanRevurderes.from(
            revurdering = iverksattRevurdering(
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(),
                    begrunnelse = "må regge",
                ),
            ).second.shouldBeType<IverksattRevurdering.Innvilget>(),
            utbetalingId = UUID30.randomUUID(),
            clock = fixedClock,
        ).let {
            it.dokumenttilstand shouldBe Dokumenttilstand.IKKE_GENERERT_ENDA
            it.skalGenerereDokumentVedFerdigstillelse() shouldBe true
        }
    }

    @Test
    fun `vedtak for innvilget revurdering med brev`() {
        (iverksattRevurdering().fourth as VedtakInnvilgetRevurdering).let {
            it.dokumenttilstand shouldBe Dokumenttilstand.IKKE_GENERERT_ENDA
            it.skalGenerereDokumentVedFerdigstillelse() shouldBe true
        }
    }

    @Test
    fun `vedtak for innvilget revurdering uten brev`() {
        (
            iverksattRevurdering(
                brevvalg = BrevvalgRevurdering.Valgt.IkkeSendBrev(
                    begrunnelse = "test-begrunnelse",
                    bestemtAv = BrevvalgRevurdering.BestemtAv.Behandler("test-ident"),
                ),
            ).fourth as VedtakInnvilgetRevurdering
            ).let {
            it.dokumenttilstand shouldBe Dokumenttilstand.SKAL_IKKE_GENERERE
            it.skalGenerereDokumentVedFerdigstillelse() shouldBe false
        }
    }

    @Test
    fun `vedtak for opphørt revurdering med brev`() {
        (
            iverksattRevurdering(
                grunnlagsdataOverrides = listOf(
                    fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 500000.0),
                ),
            ).fourth as VedtakOpphørMedUtbetaling
            ).let {
            it.dokumenttilstand shouldBe Dokumenttilstand.IKKE_GENERERT_ENDA
            it.skalGenerereDokumentVedFerdigstillelse() shouldBe true
        }
    }

    @Test
    fun `vedtak for opphørt revurdering uten brev`() {
        (
            iverksattRevurdering(
                grunnlagsdataOverrides = listOf(
                    fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 500000.0),
                ),
                brevvalg = BrevvalgRevurdering.Valgt.IkkeSendBrev(
                    begrunnelse = "test-begrunnelse",
                    bestemtAv = BrevvalgRevurdering.BestemtAv.Behandler("test-ident"),
                ),
            ).fourth as VedtakOpphørMedUtbetaling
            ).let {
            it.dokumenttilstand shouldBe Dokumenttilstand.SKAL_IKKE_GENERERE
            it.skalGenerereDokumentVedFerdigstillelse() shouldBe false
        }
    }

    @Test
    fun `vedtak for avslag med beregning med brev`() {
        // Denne finnes kun med brev.
        søknadsbehandlingIverksattAvslagMedBeregning().third.let {
            // Søknadsbehandling avslag genererer brev synkront.
            it.dokumenttilstand shouldBe Dokumenttilstand.GENERERT
            it.skalGenerereDokumentVedFerdigstillelse() shouldBe false
        }
    }

    @Test
    fun `vedtak for avslag uten beregning sender brev`() {
        // Denne finnes kun med brev.
        søknadsbehandlingIverksattAvslagUtenBeregning().third.let {
            it.skalGenerereDokumentVedFerdigstillelse() shouldBe false
            it.dokumenttilstand shouldBe Dokumenttilstand.GENERERT
        }
    }

    @Test
    fun `vedtak for innvilget søknadsbehandling skal sende brev`() {
        // Denne finnes kun med brev.
        søknadsbehandlingIverksattInnvilget().third.let {
            it.dokumenttilstand shouldBe Dokumenttilstand.IKKE_GENERERT_ENDA
            it.skalGenerereDokumentVedFerdigstillelse() shouldBe true
        }
    }

    @Test
    fun `vedtak med vurdert tilbakekrevingsbehandling sender ikke brev`() {
        vedtakRevurdering(
            clock = TikkendeKlokke(1.august(2021).fixedClock()),
            revurderingsperiode = mai(2021)..desember(2021),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(periode = mai(2021)..desember(2021), arbeidsinntekt = 5000.0),
            ),
            utbetalingerKjørtTilOgMed = 1.juli(2021),
        ).mapSecond { it as VedtakInnvilgetRevurdering }.also {
            it.second.behandling.tilbakekrevingsbehandling.skalTilbakekreve().shouldBeRight()
            it.first.avventerKravgrunnlag().shouldBeTrue()
            it.second.dokumenttilstand shouldBe Dokumenttilstand.IKKE_GENERERT_ENDA
            // Denne skal være false inntil vi ikke lenger venter på kravgrunnlaget.
            it.second.skalGenerereDokumentVedFerdigstillelse() shouldBe false
            // TODO jah: Lag en test som legger på kravgrunnlaget på saken og sjekker at denne går fra false til true (krever flytting av en del til domenet)
            //  Her er det også 2 caser - om saksbehandler har valgt brev eller ikke.
        }
    }
}
