package no.nav.su.se.bakover.statistikk.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.forNavDigitalSøknad
import no.nav.su.se.bakover.domain.forNavPapirsøknad
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.nySakMedNySøknad
import no.nav.su.se.bakover.test.søknadinnhold
import org.junit.jupiter.api.Test

internal class MottattDatoMapperTest {

    @Test
    fun `mottatt dato settes til dato for mottak av papirsøknad`() {
        val fnr = no.nav.su.se.bakover.test.fnr
        val søknad = nySakMedNySøknad(
            clock = fixedClockAt(2.januar(2021)),
            søknadInnhold = søknadinnhold(
                fnr = fnr,
                forNav = forNavPapirsøknad(
                    mottaksdatoForSøknad = 1.januar(2021),
                ),
            ),
        ).second
        søknad.mottattDato() shouldBe 1.januar(2021)
    }

    @Test
    fun `mottatt dato settes til dato for opprettelse av behandling ved digital søknad`() {
        val fnr = no.nav.su.se.bakover.test.fnr
        val søknad = nySakMedNySøknad(
            clock = fixedClockAt(1.januar(2021)),
            søknadInnhold = søknadinnhold(
                fnr = fnr,
                forNav = forNavDigitalSøknad(),
            ),
        ).second
        søknad.mottattDato() shouldBe 1.januar(2021)
    }
}
