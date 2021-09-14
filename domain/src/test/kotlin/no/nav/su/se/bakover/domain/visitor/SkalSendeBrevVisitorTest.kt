package no.nav.su.se.bakover.domain.visitor

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.iverksattRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test

internal class SkalSendeBrevVisitorTest {
    @Test
    fun `vedtak for innvilget revurdering med årsak g-regulering skal ikke sende brev`() {
        val vedtak = Vedtak.from(
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
        val vedtak = Vedtak.from(
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
        val vedtak = Vedtak.Avslag.fromSøknadsbehandlingMedBeregning(
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
        val vedtak = Vedtak.Avslag.fromSøknadsbehandlingUtenBeregning(
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
    fun `vedtak for revurdering uten endringer sender brev hvis det er valgt`() {
        val skalSendeBrev = Vedtak.from(
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
    fun `vedtak for revurdering uten endringer sender ikke brev hvis det er valgt`() {
        val skalIkkeSendeBrev = Vedtak.from(
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
        val vedtak = Vedtak.fromSøknadsbehandling(
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
}
