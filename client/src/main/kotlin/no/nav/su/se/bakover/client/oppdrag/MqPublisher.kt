package no.nav.su.se.bakover.client.oppdrag

import arrow.core.Either

interface MqPublisher {
    fun publish(vararg messages: String): Either<CouldNotPublish, Unit>

    data class MqPublisherConfig(
        val sendQueue: String,
        val replyTo: String? = null,
    )

    object CouldNotPublish
}
