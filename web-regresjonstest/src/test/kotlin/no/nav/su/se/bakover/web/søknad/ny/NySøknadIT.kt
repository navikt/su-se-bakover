package no.nav.su.se.bakover.web.søknad.ny

import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.SharedRegressionTestData.withTestApplicationAndEmbeddedDb
import no.nav.su.se.bakover.web.sak.assertSakJson
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.søknad.digitalUføreSøknadJson
import no.nav.su.se.bakover.web.søknad.papirsøknadJson
import org.junit.jupiter.api.Test

/**
 * Skal simulere at en veileder sender inn en søknad for en person som ikke har en sak fra før.
 *
 * TODO jah: Sjekk opp om det er noen praktisk forskjell rundt dette, eller om det er personen som styrer dette.
 */
internal class NySøknadIT {

    @Test
    fun `ny digital søknad`() {
        val fnr = SharedRegressionTestData.fnr
        withTestApplicationAndEmbeddedDb {
            val actualResponseJson = nyDigitalSøknadOgVerifiser(
                fnr = fnr,
                expectedSaksnummerInResponse = 2021, // Første saksnummer er alltid 2021 i en ny-migrert database.
            )
            val sakId = NySøknadJson.Response.hentSakId(actualResponseJson)
            val actualSakJson = hentSak(
                sakId = sakId,
            )
            assertSakJson(
                actualSakJson = actualSakJson,
                expectedSaksnummer = 2021,
                expectedSøknader = "[${digitalUføreSøknadJson(fnr)}]",
            )
        }
    }

    @Test
    fun `ny papirsøknad`() {
        val fnr = SharedRegressionTestData.fnr
        withTestApplicationAndEmbeddedDb {
            val actualResponseJson = nyPapirsøknadOgVerifiser(
                fnr = fnr,
                expectedSaksnummerInResponse = 2021, // Første saksnummer er alltid 2021 i en ny-migrert database.
                mottaksdato = fixedLocalDate.toString(),
            )
            val sakId = NySøknadJson.Response.hentSakId(actualResponseJson)
            val actualSakJson = hentSak(
                sakId = sakId,
            )
            assertSakJson(
                actualSakJson = actualSakJson,
                expectedSaksnummer = 2021,
                expectedSøknader = "[${papirsøknadJson(fnr)}]",
            )
        }
    }
}
