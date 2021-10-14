package no.nav.su.se.bakover.domain.behandling.avslag

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.fixedClock
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingLukket
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import org.junit.jupiter.api.Test

internal class AvslagManglendeDokumentasjonTest {
    @Test
    fun `kan ikke avslå på grunn av manglende dokumentasjon hvis feil tilstand`() {
        listOf(
            søknadsbehandlingTilAttesteringAvslagUtenBeregning(),
            søknadsbehandlingIverksattAvslagMedBeregning(),
            søknadsbehandlingLukket(),
            søknadsbehandlingTilAttesteringInnvilget(),
            søknadsbehandlingIverksattInnvilget(),
        ).forEach {
            AvslagManglendeDokumentasjon.tryCreate(
                søknadsbehandling = it.second,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "nei",
                clock = fixedClock,
            ) shouldBe AvslagManglendeDokumentasjon.SøknadsbehandlingErIUgyldigTilstand.left()
        }
    }
}
