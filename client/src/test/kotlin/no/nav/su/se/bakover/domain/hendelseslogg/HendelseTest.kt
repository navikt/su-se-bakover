package no.nav.su.se.bakover.domain.hendelseslogg

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.Hendelse
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.HendelseListReader
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.HendelseListWriter
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.behandling.UnderkjentAttestering
import org.junit.jupiter.api.Test

internal class HendelseTest {

    private val tidspunkt = Tidspunkt.now()
    private val underkjentAttestering = UnderkjentAttestering(
        attestant = "attestant",
        begrunnelse = "Dette er feil vurdering",
        tidspunkt = tidspunkt
    )

    @Test
    fun `serialisering og deserialisering av hendelser`() {
        val hendelser: List<Hendelse> = listOf(underkjentAttestering)
        val ser = HendelseListWriter.writeValueAsString(hendelser)
        ser shouldBe """
            [{"type":"UnderkjentAttestering","attestant":"attestant","begrunnelse":"Dette er feil vurdering","tidspunkt":"$tidspunkt"}]
        """.trimIndent()
        val des: List<Hendelse> = HendelseListReader.readValue(ser)
        des.first() shouldBe underkjentAttestering
    }
}
