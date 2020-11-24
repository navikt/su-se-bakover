package no.nav.su.se.bakover.domain.brev.søknad.lukk

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.brev.BrevConfig
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import org.junit.jupiter.api.Test

internal class AvvistSøknadBrevRequestTest {

    private val personaliaMock = mock<BrevInnhold.Personalia>()
    private val søknadMock = mock<Søknad>() {
        on { søknadInnhold } doReturn SøknadInnholdTestdataBuilder.build()
    }

    @Test
    fun `lager vedtaks-brevdata`() {
        AvvistSøknadBrevRequest(søknadMock, BrevConfig.Vedtak(null)).lagBrevInnhold(
            personaliaMock
        ) shouldBe AvvistSøknadVedtakBrevInnhold(personaliaMock, null)
    }

    @Test
    fun `lager vedtaks-brevdata med fritekst`() {
        AvvistSøknadBrevRequest(søknadMock, BrevConfig.Vedtak("jeg er fritekst")).lagBrevInnhold(
            personaliaMock
        ) shouldBe AvvistSøknadVedtakBrevInnhold(personaliaMock, "jeg er fritekst")
    }

    @Test
    fun `lager fritekst-brevdata`() {
        AvvistSøknadBrevRequest(
            søknadMock,
            BrevConfig.Fritekst(
                "jeg er fritekst"
            )
        ).lagBrevInnhold(
            personaliaMock
        ) shouldBe AvvistSøknadFritekstBrevInnhold(
            personalia = personaliaMock,
            fritekst = "jeg er fritekst"
        )
    }
}
