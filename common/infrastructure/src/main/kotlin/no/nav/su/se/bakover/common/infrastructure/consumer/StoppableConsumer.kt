package no.nav.su.se.bakover.common.infrastructure.consumer

interface StoppableConsumer {
    /**
     * Unikt navn for consumer. Kan være samme som topic navn.
     */
    val consumerName: String

    /**
     * Idempotent. Forventer bare at ingen nye jobber blir startet. Pågående kjører ferdig.
     */
    fun stop()
}
