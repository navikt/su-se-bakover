package no.nav.su.se.bakover.domain.søknadinnhold

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class FormueTest {

    @Test
    fun `borIBolig må være utfylt dersom eierBolig er true`() {
        Formue.tryCreate(
            eierBolig = true, borIBolig = true, verdiPåBolig = null,
            boligBrukesTil = null, depositumsBeløp = null, verdiPåEiendom = null,
            eiendomBrukesTil = null, kjøretøy = listOf(), innskuddsBeløp = null,
            verdipapirBeløp = null, skylderNoenMegPengerBeløp = null, kontanterBeløp = null,
        ).shouldBeRight()

        Formue.tryCreate(
            eierBolig = true, borIBolig = null, verdiPåBolig = null,
            boligBrukesTil = null, depositumsBeløp = null, verdiPåEiendom = null,
            eiendomBrukesTil = null, kjøretøy = listOf(), innskuddsBeløp = null,
            verdipapirBeløp = null, skylderNoenMegPengerBeløp = null, kontanterBeløp = null,
        ) shouldBe FeilVedOpprettelseAvFormue.BorIBoligErIkkeUtfylt.left()
    }

    @Test
    fun `informasjon om bolig må være utfylt dersom søker ikke bor i boligen`() {
        Formue.tryCreate(
            eierBolig = true, borIBolig = false, verdiPåBolig = 12,
            boligBrukesTil = "jeg beskrives hva boligen brukes til", depositumsBeløp = null, verdiPåEiendom = null,
            eiendomBrukesTil = null, kjøretøy = listOf(), innskuddsBeløp = null,
            verdipapirBeløp = null, skylderNoenMegPengerBeløp = null, kontanterBeløp = null,
        ).shouldBeRight()

        Formue.tryCreate(
            eierBolig = true, borIBolig = false, verdiPåBolig = 12,
            boligBrukesTil = null, depositumsBeløp = null, verdiPåEiendom = null,
            eiendomBrukesTil = null, kjøretøy = listOf(), innskuddsBeløp = null,
            verdipapirBeløp = null, skylderNoenMegPengerBeløp = null, kontanterBeløp = null,
        ) shouldBe FeilVedOpprettelseAvFormue.BoligensVerdiEllerBeskrivelseErIkkeUtfylt.left()

        Formue.tryCreate(
            eierBolig = true, borIBolig = false, verdiPåBolig = null,
            boligBrukesTil = "beskrivelse", depositumsBeløp = null, verdiPåEiendom = null,
            eiendomBrukesTil = null, kjøretøy = listOf(), innskuddsBeløp = null,
            verdipapirBeløp = null, skylderNoenMegPengerBeløp = null, kontanterBeløp = null,
        ) shouldBe FeilVedOpprettelseAvFormue.BoligensVerdiEllerBeskrivelseErIkkeUtfylt.left()
    }
}
