package tilbakekreving.infrastructure.client

internal val expectedAnnulleringRequestXml = """
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
        </SOAP-ENV:Envelope>, Request: <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
            <soap:Header>
                <Action xmlns="http://www.w3.org/2005/08/addressing">http://okonomi.nav.no/tilbakekrevingService/TilbakekrevingPortType/tilbakekrevingsvedtakRequest</Action>
                <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:8f013b77-b5e4-4c80-8afe-93470067ed90</MessageID>
                <To xmlns="http://www.w3.org/2005/08/addressing">http://localhost:52794/c</To>
                <ReplyTo xmlns="http://www.w3.org/2005/08/addressing">
                    <Address>http://www.w3.org/2005/08/addressing/anonymous</Address>
                </ReplyTo>
                <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
                               xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
                               soap:mustUnderstand="1">
                    fake-saml-token
                </wsse:Security>
            </soap:Header>
            <soap:Body>
                <ns4:tilbakekrevingsvedtakRequest xmlns:ns2="urn:no:nav:tilbakekreving:typer:v1"
                                          xmlns:ns4="http://okonomi.nav.no/tilbakekrevingService/"
                                          xmlns:ns3="urn:no:nav:tilbakekreving:tilbakekrevingsvedtak:vedtak:v1">
          <tilbakekrevingsvedtak>
            <ns3:kodeAksjon>A</ns3:kodeAksjon>
            <ns3:vedtakId>789-101</ns3:vedtakId>
            <ns3:saksbehId>saksbehandler</ns3:saksbehId>
          </tilbakekrevingsvedtak>
        </ns4:tilbakekrevingsvedtakRequest>
            </soap:Body>
        </soap:Envelope>
""".trimIndent()
