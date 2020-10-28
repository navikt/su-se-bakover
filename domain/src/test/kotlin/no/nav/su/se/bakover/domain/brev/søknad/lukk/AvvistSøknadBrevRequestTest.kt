package no.nav.su.se.bakover.domain.brev.søknad.lukk

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.brev.BrevConfig
import no.nav.su.se.bakover.domain.brev.Brevdata
import org.junit.jupiter.api.Test

internal class AvvistSøknadBrevRequestTest {

    private val personaliaMock = mock<Brevdata.Personalia>()
    private val søknadMock = mock<Søknad>() {
        on { søknadInnhold } doReturn SøknadInnholdTestdataBuilder.build()
    }

    @Test
    fun `lager vedtaks-brevdata`() {
        AvvistSøknadBrevRequest(søknadMock, BrevConfig.Vedtak).lagBrevdata(
            personaliaMock
        ) shouldBe AvvistSøknadVedtakBrevdata(personaliaMock)
    }

    @Test
    fun `lager fritekst-brevdata`() {
        AvvistSøknadBrevRequest(
            søknadMock,
            BrevConfig.Fritekst(
                "jeg er fritekst"
            )
        ).lagBrevdata(
            personaliaMock
        ) shouldBe AvvistSøknadFritekstBrevdata(
            personalia = personaliaMock,
            tittel = "Info om avvist søknad",
            fritekst = "jeg er fritekst"
        )
    }
}
