package no.nav.su.se.bakover.web.services.klage

import arrow.core.Either
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.service.klage.KlagevedtakService
import no.nav.su.se.bakover.service.toggles.ToggleService
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import kotlin.concurrent.timer

internal class FattetKlagevedtakConsumer(
    private val consumer: Consumer<String, String>,
    private val klagevedtakService: KlagevedtakService,
    periode: Long = Duration.ofSeconds(600).toMillis(),
    initialDelay: Long = 0,
    topicName: String = "klage.vedtak-fattet.v1",
    private val pollTimeoutDuration: Duration = Duration.ofMillis(5000),
    private val maxBatchSize: Int? = null,
    private val clock: Clock,
    private val toggleService: ToggleService,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        log.info("Fattet klagevedtak: Setter opp Kafka-Consumer som lytter på $topicName fra Kabal/Klageinstans")
        consumer.subscribe(listOf(topicName))

        timer(
            name = "FattetKlagevedtakConsumer",
            daemon = true,
            period = periode,
            initialDelay = initialDelay,
        ) {
            if (toggleService.isEnabled("supstonad.ufore.klage-kafka")) {
                Either.catch {
                    do {
                        val messages = consumer.poll(pollTimeoutDuration)
                        if (!messages.isEmpty) {
                            consume(messages)
                        }
                    } while (messages.count() == maxBatchSize)
                }.mapLeft {
                    // Dette vil føre til en timeout, siden vi ikke gjør noen commit. Da vil vi ikke få noen meldinger i mellomtiden.
                    log.error("Fattet klagevedtak: Ukjent feil ved konsumering av klagevedtak.", it)
                }
            } else {
                log.debug("Fattet klagevedtak: Konsumerer ikke klage sin kafka-topic. Kan slåes på med Unleash sitt felt 'klage-kafka'")
            }
        }
    }

    private fun consume(messages: ConsumerRecords<String, String>) {
        val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        log.debug(
            "Fattet klagevedtak: ${messages.count()} nye meldinger fra Kabal/Klagevedtak.",
        )

        run breakable@{
            messages.forEach { message ->
                KlagevedtakMapper.map(message, clock).tap {
                    klagevedtakService.lagre(it)
                    offsets[TopicPartition(message.topic(), message.partition())] =
                        OffsetAndMetadata(message.offset() + 1)
                }.tapLeft {
                    when (it) {
                        KunneIkkeMappeKlagevedtak.FantIkkeEventId,
                        KunneIkkeMappeKlagevedtak.FantIkkeKilde,
                        -> {
                            log.error("Fattet klagevedtak: $it. Key: ${message.key()}, partition: ${message.partition()}, offset: ${message.offset()}")
                            sikkerLogg.error("Fattet klagevedtak: $it. Key: ${message.key()}, Value: ${message.value()}, partition: ${message.partition()}, offset: ${message.offset()} ")
                            consumer.commitSync(offsets)
                            // Kafka tar ikke hensyn til offsetten vi comitter før det skjer en Rebalance.
                            // Vi kan tvinge en rebalance, dersom vi ikke ønsker neste event (som kan føre til at vi comitter lengre frem enn vi faktisk er)
                            // Hvis dette skjer vil Kafka prøve å sende meldingen på nytt mens den blokkerer nyere meldinger.
                            // Dersom dette oppstår kan vi vurdere om vi heller bare skal forkaste meldingen eller lagre den (da flytter vi kompleksiten inn i domenet.)
                            consumer.enforceRebalance()
                            return@breakable
                        }
                        is KunneIkkeMappeKlagevedtak.IkkeAktuellOpplysningstype -> {
                            log.debug("Fattet klagevedtak: Forkastet hendelse med uaktuell kilde: ${it.kilde}, key: ${message.key()}, partition: ${message.partition()}, offset: ${message.offset()}\"")
                            sikkerLogg.debug("Fattet klagevedtak: Forkastet hendelse med uaktuell kilde: ${it.kilde}, key: ${message.key()}, value: ${message.value()} partition: ${message.partition()}, offset: ${message.offset()}\"")
                            offsets[TopicPartition(message.topic(), message.partition())] =
                                OffsetAndMetadata(message.offset() + 1)
                        }
                    }
                }
            }
            consumer.commitSync(offsets)
        }
        log.debug("Fattet klagevedtak: Prosessert ferdig meldingene.")
    }
}
