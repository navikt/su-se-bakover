package no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.fritekstTilBrev
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttesteringAvslagBeregning
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttesteringAvslagVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttesteringInnvilget
import no.nav.su.se.bakover.test.attesteringUnderkjent
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test

internal class UnderkjennTest {
    @Test
    fun `til attestering avslag vilkår til underkjent avslag vilkår`() {
        val attestering = attesteringUnderkjent(clock = fixedClock)
        val søknadsbehandling = tilAttesteringAvslagVilkår
        søknadsbehandling.tilUnderkjent(
            attestering = attestering,
        ).getOrFail().let {
            it shouldBe beOfType<UnderkjentSøknadsbehandling.Avslag.UtenBeregning>()
            it.saksbehandler shouldBe saksbehandler
            it.beregning shouldBe null
            it.fritekstTilBrev shouldBe fritekstTilBrev
            it.attesteringer shouldBe Attesteringshistorikk.create(listOf(attestering))
            it.søknadsbehandlingsHistorikk shouldBe søknadsbehandling.søknadsbehandlingsHistorikk
        }
    }

    @Test
    fun `til attestering avslag beregning til underkjent avslag beregning`() {
        val attestering = attesteringUnderkjent(clock = fixedClock)
        val søknadsbehandling = tilAttesteringAvslagBeregning
        søknadsbehandling.tilUnderkjent(
            attestering = attestering,
        ).getOrFail().let {
            it shouldBe beOfType<UnderkjentSøknadsbehandling.Avslag.MedBeregning>()
            it.saksbehandler shouldBe saksbehandler
            it.beregning shouldNotBe null
            it.fritekstTilBrev shouldBe fritekstTilBrev
            it.attesteringer shouldBe Attesteringshistorikk.create(listOf(attestering))
            it.søknadsbehandlingsHistorikk shouldBe søknadsbehandling.søknadsbehandlingsHistorikk
        }
    }

    @Test
    fun `til attestering innvilget til underkjent innvilging`() {
        val søknadsbehandling = tilAttesteringInnvilget
        val attestering = attesteringUnderkjent(clock = fixedClock)
        søknadsbehandling.tilUnderkjent(
            attestering = attestering,
        ).getOrFail().let {
            it shouldBe beOfType<UnderkjentSøknadsbehandling.Innvilget>()
            it.saksbehandler shouldBe saksbehandler
            it.beregning shouldBe tilAttesteringInnvilget.beregning
            it.fritekstTilBrev shouldBe fritekstTilBrev
            it.attesteringer shouldBe Attesteringshistorikk.create(listOf(attestering))
            it.søknadsbehandlingsHistorikk shouldBe tilAttesteringInnvilget.søknadsbehandlingsHistorikk
        }
    }

    @Test
    fun `til attestering avslag vilkår kan ikke underkjenne sitt eget verk`() {
        val søknadsbehandling = tilAttesteringAvslagVilkår.copy(saksbehandler = NavIdentBruker.Saksbehandler("sneaky"))
        søknadsbehandling.tilUnderkjent(
            attestering = Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant("sneaky"),
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "",
                opprettet = fixedTidspunkt,
            ),
        ) shouldBe KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
    }

    @Test
    fun `til attestering avslag beregning kan ikke underkjenne sitt eget verk`() {
        val søknadsbehandling =
            tilAttesteringAvslagBeregning.copy(saksbehandler = NavIdentBruker.Saksbehandler("sneaky"))
        søknadsbehandling.tilUnderkjent(
            attestering = Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant("sneaky"),
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "",
                opprettet = fixedTidspunkt,
            ),
        ) shouldBe KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
    }

    @Test
    fun `til attestering innvilget kan ikke underkjenne sitt eget verk`() {
        val søknadsbehandling = tilAttesteringInnvilget.copy(saksbehandler = NavIdentBruker.Saksbehandler("sneaky"))
        søknadsbehandling.tilUnderkjent(
            attestering = Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant("sneaky"),
                grunn = Attestering.Underkjent.Grunn.ANDRE_FORHOLD,
                kommentar = "",
                opprettet = fixedTidspunkt,
            ),
        ) shouldBe KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
    }
}
