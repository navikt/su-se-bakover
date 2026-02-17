package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import dokument.domain.brev.Brevvalg
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.revurdering.brev.lagDokumentKommando
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test

internal class AvsluttetRevurderingTest {

    @Test
    fun `lager en avsluttet revurdering med opprettet som underliggende`() {
        val clock = tikkendeFixedClock()
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = opprettetRevurdering(
                clock = clock,
            ).second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = Tidspunkt.now(clock),
            avsluttetAv = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `lager en avsluttet revurdering med beregnet som underliggende`() {
        val clock = tikkendeFixedClock()
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = beregnetRevurdering(
                clock = clock,
            ).second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = Tidspunkt.now(clock),
            avsluttetAv = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `lager en avsluttet revurdering med simulert som underliggende`() {
        val clock = tikkendeFixedClock()
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulertRevurdering(
                clock = clock,
                vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
            ).second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = Tidspunkt.now(clock),
            avsluttetAv = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til fritekst dersom underliggende revurdering er forhåndsvarslet`() {
        val clock = tikkendeFixedClock()
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulertRevurdering(
                clock = clock,
                vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
            ).second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("en god, og fri tekst"),
            tidspunktAvsluttet = Tidspunkt.now(clock),
            avsluttetAv = saksbehandler,
        ).shouldBeRight()
    }

    @Test
    fun `får feil dersom man prøver å lage en avsluttet revurdering med til attestering som underliggende`() {
        val clock = tikkendeFixedClock()
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = revurderingTilAttestering(
                clock = clock,
            ).second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = Tidspunkt.now(clock),
            avsluttetAv = saksbehandler,
        ) shouldBe KunneIkkeLageAvsluttetRevurdering.RevurderingenErTilAttestering.left()
    }

    @Test
    fun `får feil dersom man prøver å lage en avsluttet revurdering med avsluttet som underliggende`() {
        val clock = tikkendeFixedClock()
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulertRevurdering(
                clock = clock,
                vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
            ).second.avslutt(
                begrunnelse = "begrunnelse",
                brevvalg = null,
                tidspunktAvsluttet = Tidspunkt.now(clock),
                avsluttetAv = saksbehandler,
            ).getOrFail(),
            begrunnelse = "prøver å avslutte en revurdering som er i avsluttet tilstand",
            brevvalg = null,
            tidspunktAvsluttet = Tidspunkt.now(clock),
            avsluttetAv = saksbehandler,
        ) shouldBe KunneIkkeLageAvsluttetRevurdering.RevurderingErAlleredeAvsluttet.left()
    }

    @Test
    fun `AvsluttetRevurdering med avsluttetAv som er null`() {
        val clock = tikkendeFixedClock()
        val underliggende = simulertRevurdering(clock = clock).second
        val avsluttet = AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = underliggende,
            begrunnelse = "Avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = Tidspunkt.now(clock),
            avsluttetAv = null,
        ).getOrNull()!!
        val satsFactory = satsFactoryTestPåDato()
        val fritekst = "Dette er en fritekst"
        try {
            avsluttet.lagDokumentKommando(
                satsFactory = satsFactory,
                clock = clock,
                fritekst = fritekst,
            )
            assert(false) { "Forventet exception" }
        } catch (e: IllegalStateException) {
            e.message shouldBe "AvsluttetRevurdering.avsluttetAv må være NavIdentBruker.Saksbehandler, men var null"
        }
    }

    @Test
    fun `AvsluttetRevurdering med avsluttetAv som ikke er saksbehandler`() {
        val clock = tikkendeFixedClock()
        val underliggende = simulertRevurdering(clock = clock).second
        val feilAvsluttetAv = NavIdentBruker.Veileder("Z999999")
        val avsluttet = AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = underliggende,
            begrunnelse = "Avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = Tidspunkt.now(clock),
            avsluttetAv = feilAvsluttetAv,
        ).getOrNull()!!
        val satsFactory = satsFactoryTestPåDato()
        val fritekst = "Dette er en fritekst"
        try {
            avsluttet.lagDokumentKommando(
                satsFactory = satsFactory,
                clock = clock,
                fritekst = fritekst,
            )
            assert(false) { "Forventet exception" }
        } catch (e: IllegalStateException) {
            e.message shouldBe "AvsluttetRevurdering.avsluttetAv må være NavIdentBruker.Saksbehandler, men var Veileder"
        }
    }
}
