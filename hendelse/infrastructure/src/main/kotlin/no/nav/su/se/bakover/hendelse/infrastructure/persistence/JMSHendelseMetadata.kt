package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata

private data class JMSHendelseMetadataJson(
    val jmsCorrelationId: String?,
    val jmsDeliveryMode: Int?,
    val jmsDeliveryTime: Long?,
    val jmsDestination: String?,
    val jmsExpiration: Long?,
    val jmsMessageId: String?,
    val jmsPriority: Int?,
    val jmsRedelivered: Boolean?,
    val jmsReplyTo: String?,
    val jmsTimestamp: Long?,
    val jmsType: String?,
    val correlationId: String,
) {
    fun toDomain(): JMSHendelseMetadata {
        return JMSHendelseMetadata(
            jmsCorrelationId = jmsCorrelationId,
            jmsDeliveryMode = jmsDeliveryMode,
            jmsDeliveryTime = jmsDeliveryTime,
            jmsDestination = jmsDestination,
            jmsExpiration = jmsExpiration,
            jmsMessageId = jmsMessageId,
            jmsPriority = jmsPriority,
            jmsRedelivered = jmsRedelivered,
            jmsReplyTo = jmsReplyTo,
            jmsTimestamp = jmsTimestamp,
            jmsType = jmsType,
            correlationId = CorrelationId(correlationId),
        )
    }
}

fun JMSHendelseMetadata.toJson(): String {
    return JMSHendelseMetadataJson(
        jmsCorrelationId = jmsCorrelationId,
        jmsDeliveryMode = jmsDeliveryMode,
        jmsDeliveryTime = jmsDeliveryTime,
        jmsDestination = jmsDestination,
        jmsExpiration = jmsExpiration,
        jmsMessageId = jmsMessageId,
        jmsPriority = jmsPriority,
        jmsRedelivered = jmsRedelivered,
        jmsReplyTo = jmsReplyTo,
        jmsTimestamp = jmsTimestamp,
        jmsType = jmsType,
        correlationId = correlationId.value,
    ).let { serialize(it) }
}
