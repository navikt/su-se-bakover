package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import arrow.core.Either
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata

fun javax.jms.Message.toJMSHendelseMetadata(correlationId: CorrelationId): JMSHendelseMetadata {
    return JMSHendelseMetadata(
        // Denne er generert av meldingskøservern. Evt. av producern. Vi kan vurdere å bruke denne som vår correlationId, men vi må først verifisere at denne kommer i preprod.
        jmsCorrelationId = Either.catch { this.jmsCorrelationID }.getOrNull(),
        jmsDeliveryMode = Either.catch { this.jmsDeliveryMode }.getOrNull(),
        jmsDeliveryTime = Either.catch { this.jmsDeliveryTime }.getOrNull(),
        jmsDestination = Either.catch { this.jmsDestination.toString() }.getOrNull(),
        jmsExpiration = Either.catch { this.jmsExpiration }.getOrNull(),
        jmsMessageId = Either.catch { this.jmsMessageID }.getOrNull(),
        jmsPriority = Either.catch { this.jmsPriority }.getOrNull(),
        jmsRedelivered = Either.catch { this.jmsRedelivered }.getOrNull(),
        jmsReplyTo = Either.catch { this.jmsReplyTo.toString() }.getOrNull(),
        jmsTimestamp = Either.catch { this.jmsTimestamp }.getOrNull(),
        jmsType = Either.catch { this.jmsType }.getOrNull(),
        // Denne er generert av vårt system.
        correlationId = correlationId,
    )
}
