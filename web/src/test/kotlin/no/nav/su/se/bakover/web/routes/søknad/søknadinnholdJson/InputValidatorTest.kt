package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * BVA-tester (Boundary Value Analysis) for InputValidator.validerTekst.
 *
 * Grenser som testes:
 *  - Lengdegrense (maksLengde - 1, maksLengde, maksLengde + 1)
 *  - ASCII-kontrolltegn (0x00–0x1F og 0x7F), med eksplisitte unntak for \n, \r, \t
 *  - Tillatte tegnkategorier: bokstaver, tall, norske tegn, skilletegn, valuta, spesialtegn
 *  - Ulovlige tegn fra Unicode utenfor tillatt sett
 *  - Mistenkelige mønstre (XSS)
 *  - Null-input (skal ikke gi feil)
 *  - Tom streng (skal ikke gi feil)
 */
internal class InputValidatorTest {

    private fun valider(verdi: String?, maksLengde: Int = 200) =
        InputValidator.validerTekst(felt = "felt", verdi = verdi, maksLengde = maksLengde)

    // -------------------------------------------------------------------------
    // Null og tom streng
    // -------------------------------------------------------------------------

    @Nested
    inner class NullOgTomStreng {
        @Test
        fun `null gir ingen feil`() {
            valider(null) shouldBe null
        }

        @Test
        fun `tom streng gir ingen feil`() {
            valider("") shouldBe null
        }
    }

    // -------------------------------------------------------------------------
    // Lengdegrense – BVA på maksLengde
    // -------------------------------------------------------------------------

    @Nested
    inner class Lengde {
        @Test
        fun `streng med lengde lik maksLengde er gyldig`() {
            valider("a".repeat(10), maksLengde = 10) shouldBe null
        }

        @Test
        fun `streng med lengde en under maksLengde er gyldig`() {
            valider("a".repeat(9), maksLengde = 10) shouldBe null
        }

        @Test
        fun `streng med lengde en over maksLengde er ugyldig`() {
            val feil = valider("a".repeat(11), maksLengde = 10)
            feil?.begrunnelse shouldBe "for lang verdi"
        }
    }

    // -------------------------------------------------------------------------
    // Tillatte tegn – gyldige grenseverdier
    // -------------------------------------------------------------------------

    @Nested
    inner class TillatteKategorier {
        @Test
        fun `store og små latinske bokstaver er tillatt`() {
            valider("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ") shouldBe null
        }

        @Test
        fun `sifre 0-9 er tillatt`() {
            valider("0123456789") shouldBe null
        }

        @Test
        fun `norske bokstaver æøåÆØÅ er tillatt`() {
            valider("æøåÆØÅ") shouldBe null
        }

        @Test
        fun `newline, carriage return og tab er tillatt`() {
            valider("linje1\nlinje2\r\ttabell") shouldBe null
        }

        @Test
        fun `alle tillatte skilletegn er gyldige`() {
            valider(" .,?!()*:%;&_/+§=»«•…@") shouldBe null
        }

        @Test
        fun `vanlig bindestrek er tillatt`() {
            valider("ord-ord") shouldBe null
        }

        @Test
        fun `halvlangt tankestrek er tillatt`() {
            valider("2020–2021") shouldBe null
        }

        @Test
        fun `langt tankestrek er tillatt`() {
            valider("tekst — tekst") shouldBe null
        }

        @Test
        fun `non-breaking hyphen U+2011 er tillatt`() {
            valider("ord\u2011ord") shouldBe null
        }

        @Test
        fun `enkle og doble anforselstegn er tillatt`() {
            valider("'sitat' \"sitat\"") shouldBe null
        }

        @Test
        fun `typografiske anforselstegn U+2018 U+2019 U+201C U+201D er tillatt`() {
            valider("\u2018sitat\u2019 \u201Csitat\u201D") shouldBe null
        }

        @Test
        fun `valutategn dollar euro pund er tillatt`() {
            valider("$ € £") shouldBe null
        }

        @Test
        fun `aksenter e-akutt er tillatt`() {
            valider("café résumé") shouldBe null
        }
    }

    // -------------------------------------------------------------------------
    // Forbudte kontrolltegn – BVA rundt ASCII 0–31 og 127
    // -------------------------------------------------------------------------

    @Nested
    inner class Kontrolltegn {
        @Test
        fun `NUL (0x00) er forbudt`() {
            valider("hei\u0000") shouldBe UgyldigInput("felt", "inneholder kontrolltegn", "\u0000")
        }

        @Test
        fun `siste kontrolltegn for unntak (0x1F) er forbudt`() {
            // 0x1F = Unit Separator, høyeste forbudte kontrolltegn (under \n=10, \r=13, \t=9)
            valider("hei\u001F") shouldBe UgyldigInput("felt", "inneholder kontrolltegn", "\u001F")
        }

        @Test
        fun `DEL (0x7F) er forbudt`() {
            valider("hei\u007F") shouldBe UgyldigInput("felt", "inneholder kontrolltegn", "\u007F")
        }

        @Test
        fun `mellomrom (0x20) er tillatt, er første tegn etter kontrolltegnene`() {
            valider(" ") shouldBe null
        }

        @Test
        fun `0x1E (Record Separator) er forbudt`() {
            valider("hei\u001E") shouldBe UgyldigInput("felt", "inneholder kontrolltegn", "\u001E")
        }

        @Test
        fun `0x20 er tillatt men 0x1F er forbudt – off-by-one`() {
            valider("\u001F") shouldBe UgyldigInput("felt", "inneholder kontrolltegn", "\u001F")
            valider("\u0020") shouldBe null
        }
    }

    // -------------------------------------------------------------------------
    // Ulovlige Unicode-tegn utenfor tillatt sett
    // -------------------------------------------------------------------------

    @Nested
    inner class UlovligeTegn {
        @Test
        fun `interrobang er ugyldig`() {
            val feil = valider("hei\u2048")
            feil?.begrunnelse shouldBe "inneholder tegn utenfor tillatt tegnsett"
        }

        @Test
        fun `kinesiske tegn er ugyldig`() {
            val feil = valider("你好")
            feil?.begrunnelse shouldBe "inneholder tegn utenfor tillatt tegnsett"
        }

        @Test
        fun `emoji er ugyldig`() {
            val feil = valider("hei \uD83D\uDE00")
            feil?.begrunnelse shouldBe "inneholder tegn utenfor tillatt tegnsett"
        }

        @Test
        fun `feilmeldingen inneholder de ulovlige tegnene`() {
            val feil = valider("abc⸘xyz")
            feil?.tegn shouldContain "⸘"
        }
    }

    // -------------------------------------------------------------------------
    // Mistenkelige mønstre (XSS)
    // -------------------------------------------------------------------------

    @Nested
    inner class MistenkeligeMonster {
        @Test
        fun `script-tag er ugyldig som tegn utenfor tillatt tegnsett`() {
            val feil = valider("<script>alert(1)</script>")
            feil?.begrunnelse shouldBe "inneholder tegn utenfor tillatt tegnsett"
        }

        @Test
        fun `javascript-protokoll er ugyldig som mistenkelig innhold`() {
            val feil = valider("javascript:alert(1)")
            feil?.begrunnelse shouldBe "inneholder mistenkelig innhold"
        }

        @Test
        fun `iframe er ugyldig som tegn utenfor tillatt tegnsett`() {
            val feil = valider("<iframe src='x'>")
            feil?.begrunnelse shouldBe "inneholder tegn utenfor tillatt tegnsett"
        }

        @Test
        fun `onerror-attributt er ugyldig som mistenkelig innhold`() {
            val feil = valider("onerror=alert(1)")
            feil?.begrunnelse shouldBe "inneholder mistenkelig innhold"
        }

        @Test
        fun `onload-attributt er ugyldig som mistenkelig innhold`() {
            val feil = valider("onload=foo()")
            feil?.begrunnelse shouldBe "inneholder mistenkelig innhold"
        }
    }

    // -------------------------------------------------------------------------
    // Prioritering – kontrolltegn sjekkes før tegnsett, tegnsett før XSS
    // -------------------------------------------------------------------------

    @Nested
    inner class Prioritering {
        @Test
        fun `kontrolltegn rapporteres før ulovlig tegnsett`() {
            val feil = valider("hei\u0000⸘")
            feil?.begrunnelse shouldBe "inneholder kontrolltegn"
        }

        @Test
        fun `ulovlig tegnsett rapporteres før mistenkelig mønster`() {
            val feil = valider("⸘<script>")
            feil?.begrunnelse shouldBe "inneholder tegn utenfor tillatt tegnsett"
        }
    }
}
