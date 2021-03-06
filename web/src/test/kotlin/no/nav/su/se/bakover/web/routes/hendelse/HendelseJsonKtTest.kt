package no.nav.su.se.bakover.web.routes.hendelse

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.behandling.UnderkjentAttestering
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class HendelseJsonKtTest() {
    @Test
    fun `test Json`() {
        val tidspunkt = Tidspunkt.now()
        val expected =
            """
            {
                "overskrift" : "Attestering underkjent",
                "underoverskrift": "$tidspunkt - attestant",
                "tidspunkt": "$tidspunkt",
                "melding": "begrunnelse"
            }
            """.trimIndent()

        JSONAssert.assertEquals(
            expected.trimIndent(),
            serialize(UnderkjentAttestering("attestant", "begrunnelse", tidspunkt).toJson()),
            true
        )
    }
}
