package no.nav.su.se.bakover.client.kabal

import no.nav.su.se.bakover.common.serialize
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class KabalRequestTest {
    val request = KabalRequestTestData.request
    val fnr = KabalRequestTestData.fnr

    @Test
    fun `serialisering av requesten`() {
        val actual = serialize(request)

        JSONAssert.assertEquals(KabalRequestTestData.expected, actual, true)
    }
}
