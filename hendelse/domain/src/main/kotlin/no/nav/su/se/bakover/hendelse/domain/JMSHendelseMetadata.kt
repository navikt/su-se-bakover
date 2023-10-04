package no.nav.su.se.bakover.hendelse.domain

import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker

/**
 * @param jmsCorrelationId er IBM sin correlation-id
 * @param correlationId er vår correlation-id
 */
data class JMSHendelseMetadata(
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
    override val correlationId: CorrelationId,
) : HendelseMetadata {
    override val ident: NavIdentBruker? = null
    override val brukerroller: List<Brukerrolle> = emptyList()

    companion object {
        fun fromCorrelationId(correlationId: CorrelationId): JMSHendelseMetadata =
            JMSHendelseMetadata(
                jmsCorrelationId = null,
                jmsDeliveryMode = null,
                jmsDeliveryTime = null,
                jmsDestination = null,
                jmsExpiration = null,
                jmsMessageId = null,
                jmsPriority = null,
                jmsRedelivered = null,
                jmsReplyTo = null,
                jmsTimestamp = null,
                jmsType = null,
                correlationId = correlationId,
            )
    }
}
