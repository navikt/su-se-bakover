package no.nav.su.se.bakover.web.routes.søknad.kontrakt

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.søknad.søknadsinnholdAlder
import no.nav.su.se.bakover.web.Frontendkontrakt
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdAlderJson.Companion.toSøknadsinnholdAlderJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.alderssøknadsinnholdJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

@Frontendkontrakt
internal class AlderssøknadJsonTest {
    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(alderssøknadsinnholdJson, serialize(søknadsinnholdAlder().toSøknadsinnholdAlderJson()), true)
    }
}
