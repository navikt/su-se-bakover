package no.nav.su.se.bakover.domain.søknad

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.søknadinnhold.ForNav
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.søknad.søknadinnhold
import no.nav.su.se.bakover.test.veileder
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadTest {

    @Test
    fun `mottatt tidspunkt for digital søknad er opprettet`() {
        val opprettet = fixedTidspunkt
        val søknad = Søknad.Ny(
            sakId = UUID.randomUUID(),
            id = UUID.randomUUID(),
            opprettet = opprettet,
            søknadInnhold = søknadinnhold(
                forNav = ForNav.DigitalSøknad(),
            ),
            innsendtAv = veileder,
        )
        søknad.mottaksdato shouldBe fixedLocalDate
    }

    @Test
    fun `mottatt tidspunkt for papirsøknad er opprettet`() {
        val dato = fixedLocalDate
        val søknad = Søknad.Ny(
            sakId = UUID.randomUUID(),
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            søknadInnhold = søknadinnhold(
                forNav = ForNav.Papirsøknad(
                    mottaksdatoForSøknad = dato,
                    grunnForPapirinnsending = ForNav.Papirsøknad.GrunnForPapirinnsending.Annet,
                    annenGrunn = null,
                ),
            ),
            innsendtAv = veileder,
        )
        søknad.mottaksdato shouldBe dato
    }
}
