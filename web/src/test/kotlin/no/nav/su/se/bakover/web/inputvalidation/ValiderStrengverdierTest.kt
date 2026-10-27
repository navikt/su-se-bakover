package no.nav.su.se.bakover.web.inputvalidation

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.jsonNode
import org.junit.jupiter.api.Test

internal class ValiderStrengverdierTest {

    private val ulovligTegn = "<"
    private val utenforTegnsett = "inneholder tegn utenfor tillatt tegnsett"

    @Test
    fun `navngir nøstede felt og listeelementer med sti og hopper over tall, boolske verdier og null`() {
        val json = jsonNode(
            """
            {
              "a": "x$ulovligTegn",
              "b": { "c": ["ok", "y$ulovligTegn"], "d": { "e": "z$ulovligTegn" } },
              "tall": 1,
              "sann": true,
              "tom": null
            }
            """.trimIndent(),
        )

        InputValidator.validerStrengverdier(json, emptyMap()) shouldBe listOf(
            UgyldigInput("a", utenforTegnsett, ulovligTegn),
            UgyldigInput("b.c[1]", utenforTegnsett, ulovligTegn),
            UgyldigInput("b.d.e", utenforTegnsett, ulovligTegn),
        )
    }

    @Test
    fun `slår opp maks lengde på sti uten listeindekser`() {
        val maks = 2
        val forLang = "abc"
        val json = jsonNode("""{ "liste": ["ab", "$forLang"] }""")

        InputValidator.validerStrengverdier(json, mapOf("liste" to maks)) shouldBe listOf(
            UgyldigInput("liste[1]", "for lang verdi, antall tegn ${forLang.length}, maks $maks"),
        )
    }

    @Test
    fun `bruker maks fritekstlengde for felt uten egen lengde`() {
        val maks = InputFelter.FRITEKST.maksLengde
        val gyldig = jsonNode("""{ "felt": "${"a".repeat(maks)}" }""")
        val forLang = jsonNode("""{ "felt": "${"a".repeat(maks + 1)}" }""")

        InputValidator.validerStrengverdier(gyldig, emptyMap()).shouldBeEmpty()
        InputValidator.validerStrengverdier(forLang, emptyMap()) shouldBe listOf(
            UgyldigInput("felt", "for lang verdi, antall tegn ${maks + 1}, maks $maks"),
        )
    }
}
