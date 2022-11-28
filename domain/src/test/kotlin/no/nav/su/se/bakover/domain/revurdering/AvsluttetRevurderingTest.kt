package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test

internal class AvsluttetRevurderingTest {

    @Test
    fun `lager en avsluttet revurdering med opprettet som underliggende`() {
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = fixedTidspunkt,
        ).shouldBeRight()
    }

    @Test
    fun `lager en avsluttet revurdering med beregnet som underliggende`() {
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = fixedTidspunkt,
        ).shouldBeRight()
    }

    @Test
    fun `lager en avsluttet revurdering med simulert som underliggende`() {
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulertRevurdering(vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag())).second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = fixedTidspunkt,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til fritekst dersom underliggende revurdering er forhåndsvarslet`() {
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulertRevurdering(
                vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
            ).second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("en god, og fri tekst"),
            tidspunktAvsluttet = fixedTidspunkt,
        ).shouldBeRight()
    }

    @Test
    fun `får feil dersom man prøver å lage en avsluttet revurdering med til attestering som underliggende`() {
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = fixedTidspunkt,
        ) shouldBe KunneIkkeLageAvsluttetRevurdering.RevurderingenErTilAttestering.left()
    }

    @Test
    fun `får feil dersom man prøver å lage en avsluttet revurdering med avsluttet som underliggende`() {
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulertRevurdering(vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag())).second.avslutt(
                begrunnelse = "begrunnelse",
                brevvalg = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrFail(),
            begrunnelse = "prøver å avslutte en revurdering som er i avsluttet tilstand",
            brevvalg = null,
            tidspunktAvsluttet = fixedTidspunkt,
        ) shouldBe KunneIkkeLageAvsluttetRevurdering.RevurderingErAlleredeAvsluttet.left()
    }
}
