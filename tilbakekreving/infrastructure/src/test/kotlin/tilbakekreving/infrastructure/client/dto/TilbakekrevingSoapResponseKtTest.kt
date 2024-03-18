package tilbakekreving.infrastructure.client.dto

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test

class TilbakekrevingSoapResponseKtTest {

    @Test
    fun `kan deserialisere respons fra preprod`() {
        responseFraPreprod.deserializeTilbakekrevingsvedtakResponse("soapRequest")
            .getOrFail() shouldBe Tilbakekrevingsresponse(
            mmel = Mmel(
                systemId = "231-OPPD",
                kodeMelding = "B782008I",
                alvorlighetsgrad = "00",
                beskrMelding = "Oppdatering foretatt.",
                sqlKode = "",
                sqlState = "",
                sqlMelding = "",
                mqCompletionKode = "",
                mqReasonKode = "",
                programId = "K231B782",
                sectionNavn = "",
            ),
            tilbakekrevingsvedtak = Tilbakekrevingsvedtak(
                kodeAksjon = "8",
                vedtakId = "679606",
                kodeHjemmel = "SUL_13",
                enhetAnsvarlig = "8020",
                kontrollfelt = "2024-03-13-19.53.14.202880",
                saksbehId = "Z993156",
            ),
        )
    }
}

//language=XML
val responseFraPreprod = """
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <SOAP-ENV:Body>
    <tilbakekrevingsvedtakResponse xmlns="http://okonomi.nav.no/tilbakekrevingService/">
      <mmel xmlns="">
        <systemId xmlns="urn:no:nav:tilbakekreving:typer:v1">231-OPPD</systemId>
        <kodeMelding xmlns="urn:no:nav:tilbakekreving:typer:v1">B782008I</kodeMelding>
        <alvorlighetsgrad xmlns="urn:no:nav:tilbakekreving:typer:v1">00</alvorlighetsgrad>
        <beskrMelding xmlns="urn:no:nav:tilbakekreving:typer:v1">Oppdatering foretatt.</beskrMelding>
        <sqlKode xmlns="urn:no:nav:tilbakekreving:typer:v1" />
        <sqlState xmlns="urn:no:nav:tilbakekreving:typer:v1" />
        <sqlMelding xmlns="urn:no:nav:tilbakekreving:typer:v1" />
        <mqCompletionKode xmlns="urn:no:nav:tilbakekreving:typer:v1" />
        <mqReasonKode xmlns="urn:no:nav:tilbakekreving:typer:v1" />
        <programId xmlns="urn:no:nav:tilbakekreving:typer:v1">K231B782</programId>
        <sectionNavn xmlns="urn:no:nav:tilbakekreving:typer:v1" />
      </mmel>
      <tilbakekrevingsvedtak xmlns="">
        <kodeAksjon xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">8</kodeAksjon>
        <vedtakId xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">679606</vedtakId>
        <kodeHjemmel xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">SUL_13</kodeHjemmel>
        <enhetAnsvarlig xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">8020</enhetAnsvarlig>
        <kontrollfelt xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">2024-03-13-19.53.14.202880</kontrollfelt>
        <saksbehId xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">Z993156</saksbehId>
      </tilbakekrevingsvedtak>
    </tilbakekrevingsvedtakResponse>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
""".trimIndent()
