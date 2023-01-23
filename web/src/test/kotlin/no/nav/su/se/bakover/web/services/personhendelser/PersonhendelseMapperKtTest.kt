package no.nav.su.se.bakover.web.services.personhendelser

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class PersonhendelseMapperKtTest {
    @Test
    fun `Fjerner unicode code point`() {
        "\u000012345678911".removeUnicodeNullcharacter() shouldBe "12345678911"
        "\\u000012345678911".removeUnicodeNullcharacter() shouldBe "12345678911"
    }
}
