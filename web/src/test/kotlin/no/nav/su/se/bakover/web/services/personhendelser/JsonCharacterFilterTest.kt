package no.nav.su.se.bakover.web.services.personhendelser

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class JsonCharacterFilterTest {
    @Test
    fun `Fjerner u0000 - null character`() {
        "\u000012345678911".removeUnwantedJsonCharacters() shouldBe "12345678911"
    }

    @Test
    fun `Fjerner u0008 - backspace`() {
        "\u000812345678911".removeUnwantedJsonCharacters() shouldBe "12345678911"
    }

    @Test
    fun `Fjerner u0009 - horizontal tab`() {
        "\u000912345678911".removeUnwantedJsonCharacters() shouldBe "12345678911"
    }

    @Test
    fun `Fjerner u000A - line feed`() {
        "\u000A12345678911".removeUnwantedJsonCharacters() shouldBe "12345678911"
    }

    @Test
    fun `Fjerner u000B - vertical tab`() {
        "\u000B12345678911".removeUnwantedJsonCharacters() shouldBe "12345678911"
    }

    @Test
    fun `Fjerner u000C - form feed`() {
        "\u000C12345678911".removeUnwantedJsonCharacters() shouldBe "12345678911"
    }

    @Test
    fun `Fjerner u000D - carriage return`() {
        "\u000D12345678911".removeUnwantedJsonCharacters() shouldBe "12345678911"
    }

    @Test
    fun `Fjerner u001a - substitute character`() {
        "\u001a12345678911".removeUnwantedJsonCharacters() shouldBe "12345678911"
    }

    @Test
    fun `Fjerner u2028 - substitute character`() {
        "\u202812345678911".removeUnwantedJsonCharacters() shouldBe "12345678911"
    }

    @Test
    fun `Fjerner u2029 - substitute character`() {
        "\u202912345678911".removeUnwantedJsonCharacters() shouldBe "12345678911"
    }

    @Test
    fun `Fjerner quotes - substitute character`() {
        "\"1234\"5678911\"".removeUnwantedJsonCharacters() shouldBe "12345678911"
    }

    @Test
    fun `Fjerner backslash - substitute character`() {
        "\\1234\\5678911\\".removeUnwantedJsonCharacters() shouldBe "12345678911"
    }

    @Test
    fun `gyldige ascii karakter i json`() {
        (32..126).joinToString("") { "\\u" + it.toString(16).padStart(4, '0') }.let {
            it.removeUnwantedJsonCharacters() == it
        }
    }
}
