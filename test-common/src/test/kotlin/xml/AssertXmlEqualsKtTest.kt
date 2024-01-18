package xml

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.xml.shouldBeSimilarXmlTo
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class AssertXmlEqualsKtTest {

    @Test
    fun `gir ut en forståelig feilmelding, dersom daten i XML'en ikke er lik`() {
        val xml1 = """
            <person>
                <navn>Per</navn>
                <alder>30</alder>
            </person>
        """.trimIndent()
        val xml2 = """
            <person>
                <navn>Per</navn>
                <alder>31</alder>
            </person>
        """.trimIndent()

        assertThrows(AssertionError::class.java) {
            xml2 shouldBeSimilarXmlTo xml1
        }.message shouldBe """
            Expected XMLs to be similar, but found differences:
            Expected text value '31' but was '30' - comparing <alder ...>31</alder> at /person[1]/alder[1]/text()[1] to <alder ...>30</alder> at /person[1]/alder[1]/text()[1] (DIFFERENT)
        """.trimIndent()
    }

    @Test
    fun `gir ut en forståelig feilmelding, dersom strukturen i XML'en ikke er lik`() {
        val xml1 = """
            <person>
                <navn>Per</navn>
                <alder>30</alder>
            </person>
        """.trimIndent()
        val xml2 = """
            <person>
                <alder>30</alder>
                <navn>Per</navn>
            </person>
        """.trimIndent()

        assertThrows(AssertionError::class.java) {
            xml2 shouldBeSimilarXmlTo xml1
        }.message shouldBe """
            Expected XMLs to be similar, but found differences:
            Expected element tag name 'alder' but was 'navn' - comparing <alder...> at /person[1]/alder[1] to <navn...> at /person[1]/navn[1] (DIFFERENT)
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
