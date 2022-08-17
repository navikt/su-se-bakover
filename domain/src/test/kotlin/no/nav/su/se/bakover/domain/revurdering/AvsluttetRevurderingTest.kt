package no.nav.su.se.bakover.domain.revurdering

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.tilAttesteringRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import org.junit.jupiter.api.Disabled
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
            underliggendeRevurdering = simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = fixedTidspunkt,
        ).shouldBeRight()
    }

    @Test
    fun `kan legge til fritekst dersom underliggende revurdering er forhåndsvarslet`() {
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().second.copy(
                forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
            ),
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.MedFritekst("en god, og fri tekst"),
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
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `får feil dersom man prøver å lage en avsluttet revurdering med iverksatt som underliggende`() {
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = iverksattRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak().second,
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = null,
            tidspunktAvsluttet = fixedTidspunkt,
        ) shouldBe KunneIkkeLageAvsluttetRevurdering.RevurderingenErIverksatt.left()
    }

    @Test
    fun `får feil dersom man prøver å lage en avsluttet revurdering med avsluttet som underliggende`() {
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().second.avslutt(
                begrunnelse = "begrunnelse",
                brevvalg = null,
                tidspunktAvsluttet = fixedTidspunkt,
            ).getOrFail(),
            begrunnelse = "prøver å avslutte en revurdering som er i avsluttet tilstand",
            brevvalg = null,
            tidspunktAvsluttet = fixedTidspunkt,
        ) shouldBe KunneIkkeLageAvsluttetRevurdering.RevurderingErAlleredeAvsluttet.left()
    }

    @Test
    fun `får feil dersom underliggende revurdering ikke er forhåndsvarslet (null), men fritekst er fylt ut`() {
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().second.also {
                assert(it.forhåndsvarsel == null)
            },
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.MedFritekst("forhåndsvarsel er null, men jeg er fyllt ut. dette skal ikke være lov"),
            tidspunktAvsluttet = fixedTidspunkt,
        ) shouldBe KunneIkkeLageAvsluttetRevurdering.BrevvalgUtenForhåndsvarsel.left()
    }

    @Test
    fun `får feil dersom underliggende revurdering ikke er forhåndsvarslet (ingen forhåndsvarsel), men fritekst er fylt ut`() {
        AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
                forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            ).second.also {
                assert(it.forhåndsvarsel == Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles)
            },
            begrunnelse = "Begrunnelse for hvorfor denne har blitt avsluttet",
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.MedFritekst("forhåndsvarsel er ingen forhåndsvarsel, men jeg er fyllt ut. dette skal ikke være lov"),
            tidspunktAvsluttet = fixedTidspunkt,
        ) shouldBe KunneIkkeLageAvsluttetRevurdering.BrevvalgUtenForhåndsvarsel.left()
    }
}
