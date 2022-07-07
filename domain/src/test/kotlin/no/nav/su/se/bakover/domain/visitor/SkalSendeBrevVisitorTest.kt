package no.nav.su.se.bakover.domain.visitor

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakRevurdering
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class SkalSendeBrevVisitorTest {
    @Test
    fun `vedtak for innvilget revurdering med årsak g-regulering skal ikke sende brev`() {
        val vedtak = VedtakSomKanRevurderes.from(
            revurdering = iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(),
                    begrunnelse = "må regge",
                ),
            ).second,
            utbetalingId = UUID30.randomUUID(),
            clock = fixedClock,
        )

        SkalSendeBrevVisitor().let {
            vedtak.accept(it)
            it.sendBrev
        } shouldBe false
        vedtak.skalSendeBrev() shouldBe false
    }

    @Test
    fun `vedtak for revurderinger sender brev som default`() {
        val vedtak = VedtakSomKanRevurderes.from(
            revurdering = iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second,
            utbetalingId = UUID30.randomUUID(),
            clock = fixedClock,
        )

        SkalSendeBrevVisitor().let {
            vedtak.accept(it)
            it.sendBrev
        } shouldBe true
        vedtak.skalSendeBrev() shouldBe true
    }

    @Test
    fun `vedtak for avslag med beregning sender brev`() {
        val vedtak: Avslagsvedtak.AvslagBeregning = Avslagsvedtak.fromSøknadsbehandlingMedBeregning(
            avslag = søknadsbehandlingIverksattAvslagMedBeregning().second,
            clock = fixedClock,
        )

        SkalSendeBrevVisitor().let {
            vedtak.accept(it)
            it.sendBrev
        } shouldBe true
        vedtak.skalSendeBrev() shouldBe true
    }

    @Test
    fun `vedtak for avslag uten beregning sender brev`() {
        val vedtak: Avslagsvedtak.AvslagVilkår = Avslagsvedtak.fromSøknadsbehandlingUtenBeregning(
            avslag = søknadsbehandlingIverksattAvslagUtenBeregning().second,
            clock = fixedClock,
        )

        SkalSendeBrevVisitor().let {
            vedtak.accept(it)
            it.sendBrev
        } shouldBe true
        vedtak.skalSendeBrev() shouldBe true
    }

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `vedtak for revurdering uten endringer sender brev hvis det er valgt`() {
        val skalSendeBrev = VedtakSomKanRevurderes.from(
            revurdering = iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
                skalFøreTilBrevutsending = true,
            ).second,
            clock = fixedClock,
        )

        SkalSendeBrevVisitor().let {
            skalSendeBrev.accept(it)
            it.sendBrev
        } shouldBe true
        skalSendeBrev.skalSendeBrev() shouldBe true
    }

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `vedtak for revurdering uten endringer sender ikke brev hvis det er valgt`() {
        val skalIkkeSendeBrev = VedtakSomKanRevurderes.from(
            revurdering = iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
                skalFøreTilBrevutsending = false,
            ).second,
            clock = fixedClock,
        )

        SkalSendeBrevVisitor().let {
            skalIkkeSendeBrev.accept(it)
            it.sendBrev
        } shouldBe false
        skalIkkeSendeBrev.skalSendeBrev() shouldBe false
    }

    @Test
    fun `vedtak for innvilget søknadsbehandling skal sende brev`() {
        val vedtak = VedtakSomKanRevurderes.fromSøknadsbehandling(
            søknadsbehandling = søknadsbehandlingIverksattInnvilget().second,
            utbetalingId = UUID30.randomUUID(),
            fixedClock,
        )

        SkalSendeBrevVisitor().let {
            vedtak.accept(it)
            it.sendBrev
        } shouldBe true
        vedtak.skalSendeBrev() shouldBe true
    }

    @Test
    fun `vedtak med vurdert tilbakekrevingsbehandling sender ikke brev`() {
        val vedtak = vedtakRevurdering(
            revurderingsperiode = mai(2021)..desember(2021),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(periode = mai(2021)..desember(2021), arbeidsinntekt = 5000.0),
            ),
        ).second.shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>().also {
            it.behandling.tilbakekrevingsbehandling.skalTilbakekreve().isRight() shouldBe true
        }

        SkalSendeBrevVisitor().let {
            vedtak.accept(it)
            it.sendBrev
        } shouldBe false
        vedtak.skalSendeBrev() shouldBe false
    }
}
