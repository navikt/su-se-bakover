package no.nav.su.se.bakover.domain.visitor

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
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

internal class SkalSendeBrevVisitorTest {
    @Test
    fun `vedtak for innvilget revurdering med årsak g-regulering skal ikke sende brev`() {
        val vedtak = VedtakSomKanRevurderes.from(
            revurdering = iverksattRevurdering(
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(),
                    begrunnelse = "må regge",
                ),
            ).second.shouldBeType<IverksattRevurdering.Innvilget>(),
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
            revurdering = iverksattRevurdering().second.shouldBeType<IverksattRevurdering.Innvilget>(),
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
            clock = TikkendeKlokke(1.august(2021).fixedClock()),
            revurderingsperiode = mai(2021)..desember(2021),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(periode = mai(2021)..desember(2021), arbeidsinntekt = 5000.0),
            ),
            utbetalingerKjørtTilOgMed = 1.juli(2021),
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
