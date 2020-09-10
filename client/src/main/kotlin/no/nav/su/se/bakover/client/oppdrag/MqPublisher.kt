package no.nav.su.se.bakover.client.oppdrag

import arrow.core.Either

interface MqPublisher {
    fun publish(message: String): Either<CouldNotPublish, Unit>
    fun publish(messages: List<String>, commit: Boolean): Either<CouldNotPublish, Unit>

    data class MqPublisherConfig(
        val sendQueue: String,
        val replyTo: String
    )

    object CouldNotPublish
}
