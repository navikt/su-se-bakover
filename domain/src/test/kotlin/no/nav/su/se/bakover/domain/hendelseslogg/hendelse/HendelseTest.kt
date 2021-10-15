package no.nav.su.se.bakover.domain.hendelseslogg.hendelse

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.behandling.UnderkjentAttestering
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test

internal class HendelseTest {

    private val underkjentAttestering = UnderkjentAttestering(
        attestant = "attestant",
        begrunnelse = "Dette er feil vurdering",
        tidspunkt = fixedTidspunkt
    )

    @Test
    fun `serialisering og deserialisering av hendelser`() {
        val hendelser: List<Hendelse> = listOf(underkjentAttestering)
        val ser = HendelseListWriter.writeValueAsString(hendelser)
        ser shouldBe """
            [{"type":"UnderkjentAttestering","attestant":"attestant","begrunnelse":"Dette er feil vurdering","tidspunkt":"$fixedTidspunkt"}]
        """.trimIndent()
        val des: List<Hendelse> = HendelseListReader.readValue(ser)
        des.first() shouldBe underkjentAttestering
    }
}
