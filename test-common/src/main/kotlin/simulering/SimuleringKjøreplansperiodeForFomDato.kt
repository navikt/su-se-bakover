package no.nav.su.se.bakover.test.simulering

/**
 * Observert i preprod 2024-06-07. Søknadsbehandlingsperiode juli 24 - juni 25 gikk OK. Deretter valgte vi revurderingsperiode okt 24 - des 24.
 */
fun simuleringSoapResponseFinnerIkkeKjøreplansperiodeForFomDato(): String {
    return """
<SOAP-ENV:Envelope
	xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
	xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
	<SOAP-ENV:Body>
		<SOAP-ENV:Fault
			xmlns="">
			<faultcode>SOAP-ENV:Client</faultcode>
			<faultstring>simulerBeregningFeilUnderBehandling </faultstring>
			<detail>
				<sf:simulerBeregningFeilUnderBehandling
					xmlns:sf="http://nav.no/system/os/tjenester/oppdragService">
					<errorMessage>Finner ikke kjøreplansperiode for fom-dato.</errorMessage>
					<errorSource>K231BB50 section: CA10-KON</errorSource>
					<rootCause>Kode BB50044F - SQL - MQ</rootCause>
					<dateTimeStamp>2024-06-07T09:45:47</dateTimeStamp>
				</sf:simulerBeregningFeilUnderBehandling>
			</detail>
		</SOAP-ENV:Fault>
	</SOAP-ENV:Body>
</SOAP-ENV:Envelope>
    """.trimIndent()
}
