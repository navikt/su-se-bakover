package xml

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.xml.shouldBeSimilarXmlTo
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class AssertXmlEqualsKtTest {

    @Test
    fun `gir ut en forståelig feilmelding, dersom daten i XML'en ikke er lik`() {
        val expected = """
            <person>
                <navn>Per</navn>
                <alder>30</alder>
            </person>
        """.trimIndent()
        val actual = """
            <person>
                <navn>Per</navn>
                <alder>31</alder>
            </person>
        """.trimIndent()

        assertThrows(AssertionError::class.java) {
            actual shouldBeSimilarXmlTo expected
        }.message shouldBe """
            Expected XMLs to be similar, but found differences:
            Expected text value '30' but was '31' - comparing <alder ...>30</alder> at /person[1]/alder[1]/text()[1] to <alder ...>31</alder> at /person[1]/alder[1]/text()[1] (DIFFERENT)
        """.trimIndent()
    }

    @Test
    fun `gir ut en forståelig feilmelding, dersom strukturen i XML'en ikke er lik`() {
        val expected = """
            <person>
                <navn>Per</navn>
                <alder>30</alder>
            </person>
        """.trimIndent()
        val actual = """
            <person>
                <alder>30</alder>
                <navn>Per</navn>
            </person>
        """.trimIndent()

        assertThrows(AssertionError::class.java) {
            actual shouldBeSimilarXmlTo expected
        }.message shouldBe """
            Expected XMLs to be similar, but found differences:
            Expected element tag name 'navn' but was 'alder' - comparing <navn...> at /person[1]/navn[1] to <alder...> at /person[1]/alder[1] (DIFFERENT)
        """.trimIndent()
    }

    @Test
    fun `Gir ikke ut noen melding dersom XML dataen & strukturen er riktig`() {
        val xml1 = """
            <person>
                <navn>Per</navn>
                <alder>30</alder>
            </person>
        """.trimIndent()
        val xml2 = """
            <person>
                <navn>Per</navn>
                <alder>30</alder>
            </person>
        """.trimIndent()
        assertDoesNotThrow { xml2 shouldBeSimilarXmlTo xml1 }
    }
}
