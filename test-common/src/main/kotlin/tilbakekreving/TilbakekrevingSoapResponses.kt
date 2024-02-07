package no.nav.su.se.bakover.test.tilbakekreving

/**
 * Mottatt i preprod ved å sende en request til tilbakekreving med en ugyldig request (decimal på feil format)
 * Status 500.
 */
fun tilbakekrevingSoapResponseConversionError() = """
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <SOAP-ENV:Body>
    <SOAP-ENV:Fault>
      <faultcode>SOAP-ENV:Server</faultcode>
      <faultstring>Conversion from SOAP failed</faultstring>
      <detail>
        <CICSFault xmlns="http://www.ibm.com/software/htp/cics/WSFault">DFHPI1009 08/02/2024 10:04:56 CICSQ1OS OSW8 85071 XML to data transformation failed. A conversion error (INVALID_CHARACTER) occurred when converting field belopTilbakekreves for WEBSERVICE tilbakekreving-v1-tjenestespesif.</CICSFault>
      </detail>
    </SOAP-ENV:Fault>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
""".trimIndent()

/**
 * Mottatt i preprod ved at vi allerede har sendt en request med samme vedtakId som har blitt godtatt.
 * Status 200.
 */
fun tilbakekrevingSoapResponseVedtakIdFinnesIkke() = """
    <?xml version="1.0" encoding="UTF-8"?>
    <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
       <SOAP-ENV:Body>
          <tilbakekrevingsvedtakResponse xmlns="http://okonomi.nav.no/tilbakekrevingService/">
             <mmel xmlns="">
                <systemId xmlns="urn:no:nav:tilbakekreving:typer:v1">231-OPPD</systemId>
                <kodeMelding xmlns="urn:no:nav:tilbakekreving:typer:v1">B441012F</kodeMelding>
                <alvorlighetsgrad xmlns="urn:no:nav:tilbakekreving:typer:v1">08</alvorlighetsgrad>
                <beskrMelding xmlns="urn:no:nav:tilbakekreving:typer:v1">Oppgitt vedtak-id finnes ikke/har feil status: 0000625279</beskrMelding>
                <sqlKode xmlns="urn:no:nav:tilbakekreving:typer:v1" />
                <sqlState xmlns="urn:no:nav:tilbakekreving:typer:v1" />
                <sqlMelding xmlns="urn:no:nav:tilbakekreving:typer:v1" />
                <mqCompletionKode xmlns="urn:no:nav:tilbakekreving:typer:v1" />
                <mqReasonKode xmlns="urn:no:nav:tilbakekreving:typer:v1" />
                <programId xmlns="urn:no:nav:tilbakekreving:typer:v1">K231B441</programId>
                <sectionNavn xmlns="urn:no:nav:tilbakekreving:typer:v1">CA10-VALIDER-INPUT</sectionNavn>
             </mmel>
             <tilbakekrevingsvedtak xmlns="">
                <kodeAksjon xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1" />
                <vedtakId xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">0</vedtakId>
                <kodeHjemmel xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1" />
                <enhetAnsvarlig xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1" />
                <kontrollfelt xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1" />
                <saksbehId xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1" />
             </tilbakekrevingsvedtak>
          </tilbakekrevingsvedtakResponse>
       </SOAP-ENV:Body>
    </SOAP-ENV:Envelope>
""".trimIndent()

fun tilbakekrevingSoapResponseOk() = """
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
        <vedtakId xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">679609</vedtakId>
        <kodeHjemmel xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">SUL_13</kodeHjemmel>
        <enhetAnsvarlig xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">8020</enhetAnsvarlig>
        <kontrollfelt xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">2024-03-13-19.53.14.419626</kontrollfelt>
        <saksbehId xmlns="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">Z990297</saksbehId>
      </tilbakekrevingsvedtak>
    </tilbakekrevingsvedtakResponse>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
""".trimIndent()
