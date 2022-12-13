package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test

internal class AvsluttetRevurderingTest {

    @Test
    fun `lager en avsluttet revurdering med opprettet som underliggende`() {
        val clock = tikkendeFixedClock()
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
                clock = clock,
            ).second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = Tidspunkt.now(clock),
        ).shouldBeRight()
    }

    @Test
    fun `lager en avsluttet revurdering med beregnet som underliggende`() {
        val clock = tikkendeFixedClock()
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
                clock = clock,
            ).second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = Tidspunkt.now(clock),
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
        ).shouldBeRight()
    }

    @Test
    fun `får feil dersom man prøver å lage en avsluttet revurdering med til attestering som underliggende`() {
        val clock = tikkendeFixedClock()
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
                clock = clock,
            ).second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = Tidspunkt.now(clock),
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
            ).getOrFail(),
            begrunnelse = "prøver å avslutte en revurdering som er i avsluttet tilstand",
            brevvalg = null,
            tidspunktAvsluttet = Tidspunkt.now(clock),
        ) shouldBe KunneIkkeLageAvsluttetRevurdering.RevurderingErAlleredeAvsluttet.left()
    }
}
