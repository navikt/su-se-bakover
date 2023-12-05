package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata

private data class JMSHendelseMetadataDbJson(
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
)

fun toJMSHendelseMetaData(data: String): JMSHendelseMetadata {
    return deserialize<JMSHendelseMetadataDbJson>(data).let {
        JMSHendelseMetadata(
            jmsCorrelationId = it.jmsCorrelationId,
            jmsDeliveryMode = it.jmsDeliveryMode,
            jmsDeliveryTime = it.jmsDeliveryTime,
            jmsDestination = it.jmsDestination,
            jmsExpiration = it.jmsExpiration,
            jmsMessageId = it.jmsMessageId,
            jmsPriority = it.jmsPriority,
            jmsRedelivered = it.jmsRedelivered,
            jmsReplyTo = it.jmsReplyTo,
            jmsTimestamp = it.jmsTimestamp,
            jmsType = it.jmsType,
            correlationId = CorrelationId(it.correlationId),
        )
    }
}

fun JMSHendelseMetadata.toDbJson(): String {
    return JMSHendelseMetadataDbJson(
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
