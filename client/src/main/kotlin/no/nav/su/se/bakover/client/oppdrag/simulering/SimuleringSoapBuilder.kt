@file:Suppress("HttpUrlsUsage")

package no.nav.su.se.bakover.client.oppdrag.simulering

internal fun buildXmlRequestSoapEnvelope(
    action: String,
    messageId: String,
    serviceUrl: String,
    assertion: String,
    body: String,
): String {
    return DEFAULT_SOAP_ENVELOPE
        .replace("{{action}}", action)
        .replace("{{messageId}}", messageId)
        .replace("{{serviceUrl}}", serviceUrl)
        .replace("{{assertion}}", assertion)
        .replace("{{body}}", body)
}

private const val DEFAULT_SOAP_ENVELOPE = """<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
    <soap:Header>
        <Action xmlns="http://www.w3.org/2005/08/addressing">{{action}}</Action>
        <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:{{messageId}}</MessageID>
        <To xmlns="http://www.w3.org/2005/08/addressing">{{serviceUrl}}</To>
        <ReplyTo xmlns="http://www.w3.org/2005/08/addressing">
            <Address>http://www.w3.org/2005/08/addressing/anonymous</Address>
        </ReplyTo>
        <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
                       xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
                       soap:mustUnderstand="1">
            {{assertion}}
        </wsse:Security>
    </soap:Header>
    <soap:Body>
        {{body}}
    </soap:Body>
</soap:Envelope>"""
