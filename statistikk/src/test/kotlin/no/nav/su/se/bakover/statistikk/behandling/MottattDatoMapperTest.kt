package no.nav.su.se.bakover.statistikk.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.søknad.forNavDigitalSøknad
import no.nav.su.se.bakover.test.søknad.forNavPapirsøknad
import no.nav.su.se.bakover.test.søknad.nySakMedNySøknad
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import org.junit.jupiter.api.Test

internal class MottattDatoMapperTest {

    @Test
    fun `mottatt dato settes til dato for mottak av papirsøknad`() {
        val søknad = nySakMedNySøknad(
            clock = fixedClockAt(2.januar(2021)),
            søknadInnhold = søknadinnholdUføre(
                personopplysninger = Personopplysninger(fnr),
                forNav = forNavPapirsøknad(
                    mottaksdatoForSøknad = 1.januar(2021),
                ),
            ),
        ).second
        søknad.mottattDato() shouldBe 1.januar(2021)
    }

    @Test
    fun `mottatt dato settes til dato for opprettelse av behandling ved digital søknad`() {
        val søknad = nySakMedNySøknad(
            clock = fixedClockAt(1.januar(2021)),
            søknadInnhold = søknadinnholdUføre(
                personopplysninger = Personopplysninger(fnr),
                forNav = forNavDigitalSøknad(),
            ),
        ).second
        søknad.mottattDato() shouldBe 1.januar(2021)
    }
}
