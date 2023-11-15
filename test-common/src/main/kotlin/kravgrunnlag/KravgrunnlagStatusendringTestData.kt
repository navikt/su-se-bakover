package no.nav.su.se.bakover.test.kravgrunnlag

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.hendelse.jmsHendelseMetadata
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlagHendelse
import java.time.Clock

fun kravgrunnlagStatusendringSomRåttKravgrunnlagHendelse(
    eksternVedtakId: String = "436206",
    saksnummer: String = "2463",
    fnr: String = "18108619852",
    status: String = "SPER",
    hendelseId: HendelseId = HendelseId.generer(),
    clock: Clock = fixedClock,
    hendelsestidspunkt: Tidspunkt = Tidspunkt.now(clock),
    correlationId: CorrelationId = CorrelationId.generate(),
    metadata: JMSHendelseMetadata = jmsHendelseMetadata(correlationId = correlationId),
): RåttKravgrunnlagHendelse {
    return RåttKravgrunnlagHendelse(
        hendelseId = hendelseId,
        hendelsestidspunkt = hendelsestidspunkt,
        meta = metadata,
        råttKravgrunnlag = kravgrunnlagStatusendringSomRåttKravgrunnlag(
            vedtakId = eksternVedtakId,
            saksnummer = saksnummer,
            fnr = fnr,
            status = status,
        ),
    )
}

fun kravgrunnlagStatusendringSomRåttKravgrunnlag(
    vedtakId: String = "436206",
    saksnummer: String = "2463",
    fnr: String = "18108619852",
    status: String = "SPER",
): RåttKravgrunnlag {
    return RåttKravgrunnlag(
        kravgrunnlagStatusendringXml(
            vedtakId = vedtakId,
            saksnummer = saksnummer,
            fnr = fnr,
            status = status,
        ),
    )
}

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
