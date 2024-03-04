@file:Suppress("HttpUrlsUsage")

package no.nav.su.se.bakover.test.simulering

/**
 * Observert i preprod etter at oppdrag tømte Q1. Vi prøvde opphøre (ENDR/OPPH).
 */
fun simuleringSoapResponseOppdragetFinnesIkkeFraFør(): String {
    return """
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <SOAP-ENV:Body>
    <SOAP-ENV:Fault>
      <faultcode>SOAP-ENV:Client</faultcode>
      <faultstring>simulerBeregningFeilUnderBehandling</faultstring>
      <detail>
        <sf:simulerBeregningFeilUnderBehandling xmlns:sf="http://nav.no/system/os/tjenester/oppdragService">
          <errorMessage>OPPDRAGET/FAGSYSTEM-ID finnes ikke fra før</errorMessage>
          <errorSource>K231BB10 section: CA35-KON</errorSource>
          <rootCause>Kode B110011F - SQL - MQ</rootCause>
          <dateTimeStamp>2024-03-04T12:34:14</dateTimeStamp>
        </sf:simulerBeregningFeilUnderBehandling>
      </detail>
    </SOAP-ENV:Fault>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
    """.trimIndent()
}
