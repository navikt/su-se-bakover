package no.nav.su.se.bakover.client.oppdrag

import arrow.core.Either

interface MqPublisher {
    fun publish(message: String): Either<CouldNotPublish, Unit>

    data class MqConfig(
        val username: String,
        val password: String,
        val queueManager: String,
        val port: Int,
        val hostname: String,
        val channel: String,
        val sendQueue: String,
        val replyTo: String
    ) {
        override fun toString(): String {
            return "MqConfig(username='$username', password='***', queueManager='$queueManager', port=$port, hostname='$hostname', channel='$channel', sendQueue='$sendQueue', replyTo='$replyTo')"
        }
    }

    object CouldNotPublish
}
