package no.nav.su.se.bakover.web.services.tilbakekreving

import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Clock
import java.util.UUID
import javax.jms.JMSContext
import javax.jms.Session

/**
 * Har ansvar for:
 * 1. Integrasjonen mot meldingskøen.
 * 2. Konsumering av meldinger
 * 3. Deserialisere til String.
 * 4. Lagre
 *
 * For selve prosessering av kravgrunnlagene, se [TilbakekrevingJob]
 */
class TilbakekrevingIbmMqConsumer(
    queueName: String,
    globalJmsContext: JMSContext,
    private val tilbakekrevingService: TilbakekrevingService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jmsContext = globalJmsContext.createContext(Session.AUTO_ACKNOWLEDGE)
    private val consumer = jmsContext.createConsumer(jmsContext.createQueue(queueName))

    init {
        consumer.setMessageListener { message ->
            try {
                // Ktor legger på X-Correlation-ID for web-requests, men vi har ikke noe tilsvarende automagi for meldingskøen.
                MDC.put("X-Correlation-ID", UUID.randomUUID().toString())
                log.info("Mottok kravgrunnlag fra køen: $queueName. Se sikkerlogg for meldingsinnhold.")
                message.getBody(String::class.java).let {
                    sikkerLogg.info("Kravgrunnlag lest fra $queueName, innhold: $it")
                    tilbakekrevingService.lagreKravgrunnlag(RåttKravgrunnlag.ny(it, clock))
                }
            } catch (ex: Exception) {
                log.error("Feil ved prossessering av melding fra: $queueName", ex)
                throw ex
            }
        }
        jmsContext.setExceptionListener { exception -> log.error("Feil mot $queueName", exception) }

        jmsContext.start()
    }
}
