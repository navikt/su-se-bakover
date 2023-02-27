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
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes.EndringIYtelse
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
    fun `vedtak for innvilget revurdering sender brev som default`() {
        (iverksattRevurdering().fourth as EndringIYtelse.InnvilgetRevurdering).let {
            it.dokumenttilstand shouldBe Dokumenttilstand.IKKE_GENERERT_ENDA
            it.skalGenerereDokumentVedFerdigstillelse() shouldBe true
        }
    }

    @Test
    fun `vedtak for opphørt revurdering sender brev som default`() {
        (
            iverksattRevurdering(
                grunnlagsdataOverrides = listOf(
                    fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 500000.0),
                ),
            ).fourth as EndringIYtelse.OpphørtRevurdering
            ).let {
            it.dokumenttilstand shouldBe Dokumenttilstand.IKKE_GENERERT_ENDA
            it.skalGenerereDokumentVedFerdigstillelse() shouldBe true
        }
    }

    @Test
    fun `vedtak for avslag med beregning sender brev`() {
        søknadsbehandlingIverksattAvslagMedBeregning().third.let {
            // Søknadsbehandling avslag genererer brev synkront.
            it.dokumenttilstand shouldBe Dokumenttilstand.GENERERT
            it.skalGenerereDokumentVedFerdigstillelse() shouldBe false
        }
    }

    @Test
    fun `vedtak for avslag uten beregning sender brev`() {
        søknadsbehandlingIverksattAvslagUtenBeregning().third.let {
            it.skalGenerereDokumentVedFerdigstillelse() shouldBe false
            it.dokumenttilstand shouldBe Dokumenttilstand.GENERERT
        }
    }

    @Test
    fun `vedtak for innvilget søknadsbehandling skal sende brev`() {
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
        ).mapSecond { it as EndringIYtelse.InnvilgetRevurdering }.also {
            it.second.behandling.tilbakekrevingsbehandling.skalTilbakekreve().shouldBeRight()
            it.first.avventerKravgrunnlag().shouldBeTrue()
            it.second.dokumenttilstand shouldBe Dokumenttilstand.IKKE_GENERERT_ENDA
            // Denne skal være false inntil vi ikke lenger venter på kravgrunnlaget.
            it.second.skalGenerereDokumentVedFerdigstillelse() shouldBe false
            // TODO jah: Lag en test legger til kravgrunnlaget på saken og sjekk at denne da er true.
        }
    }
}
