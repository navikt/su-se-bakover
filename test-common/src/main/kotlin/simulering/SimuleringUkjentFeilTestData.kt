@file:Suppress("HttpUrlsUsage")

package no.nav.su.se.bakover.test.simulering

/**
 * Observert i preprod når vi satt henvisning på feil sted.
 */
fun simuleringSoapResponseUkjentFeil(): String {
    return """
<SOAP-ENV:Envelope
    xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
    <SOAP-ENV:Body>
        <SOAP-ENV:Fault
            xmlns="">
            <faultcode>SOAP-ENV:Client</faultcode>
            <faultstring>Conversion from SOAP failed</faultstring>
            <detail>
                <CICSFault
                    xmlns="http://www.ibm.com/software/htp/cics/WSFault">DFHPI1007 07/02/2024 11:12:12 CICSQ1OS OSW8 75930 XML to data transformation failed because of incorrect input (UNDEFINED_ELEMENT henvisning) for WEBSERVICE simulerFpServiceWSBinding.
                </CICSFault>
            </detail>
        </SOAP-ENV:Fault>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
    """.trimIndent()
}
