package no.nav.su.se.bakover.test.kravgrunnlag

fun kravgrunnlagStatusendringXml(
    vedtakId: String = "436206",
    saksnummer: String = "2463",
    fnr: String = "18108619852",
    status: String = "SPER",
): String {
    //language=xml
    return """
        <?xml version="1.0" encoding="utf-8"?>
        <urn:endringKravOgVedtakstatus xmlns:urn="urn:no:nav:tilbakekreving:status:v1">
            <urn:kravOgVedtakstatus>
                <urn:vedtakId>$vedtakId</urn:vedtakId>
                <urn:kodeStatusKrav>$status</urn:kodeStatusKrav>
                <urn:kodeFagomraade>SUUFORE</urn:kodeFagomraade>
                <urn:fagsystemId>$saksnummer</urn:fagsystemId>
                <urn:vedtakGjelderId>$fnr</urn:vedtakGjelderId>
                <urn:typeGjelderId>PERSON</urn:typeGjelderId>
            </urn:kravOgVedtakstatus>
        </urn:endringKravOgVedtakstatus>
    """.trimIndent()
}
