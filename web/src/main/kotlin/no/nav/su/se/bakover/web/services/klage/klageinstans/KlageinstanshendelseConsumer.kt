package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.infrastructure.consumer.StoppableConsumer
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationIdSuspend
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseService
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class KlageinstanshendelseConsumer(
    private val consumer: Consumer<String, String>,
    private val klageinstanshendelseService: KlageinstanshendelseService,
    private val topicName: String = "klage.behandling-events.v1",
    private val pollTimeoutDuration: Duration = Duration.ofMillis(500),
    private val clock: Clock,
    private val log: Logger = LoggerFactory.getLogger(KlageinstanshendelseConsumer::class.java),
    private val sikkerLogg: Logger = no.nav.su.se.bakover.common.sikkerLogg,
) : StoppableConsumer {

    override val consumerName = topicName
    private val job: Job

    init {
        log.info("$topicName: Setter opp Kafka consumer.")
        consumer.subscribe(listOf(topicName))

        job = CoroutineScope(Dispatchers.IO).launch {

            while (this.isActive) {
                Either.catch {
                    withCorrelationIdSuspend {
                        val messages = consumer.poll(pollTimeoutDuration)
                        if (!messages.isEmpty) {
                            consume(messages)
                        }
                    }
                }.mapLeft {
                    // Dette vil føre til en timeout, siden vi ikke gjør noen commit. Da vil vi ikke få noen meldinger i mellomtiden.
                    log.error("$topicName: Ukjent feil ved konsumering.", it)
                    delay(5.seconds)
                }
            }
            log.info("$topicName: Stopper Kafka-consumer.")
            Either.catch {
                consumer.close()
            }.onLeft {
                log.error("$topicName: Feil ved lukking av consumer.", it)
            }.onRight {
                log.info("$topicName: Consumer lukket.")
            }
        }
    }

    private suspend fun consume(messages: ConsumerRecords<String, String>) {
        val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        log.debug(
            "$topicName: Prosesserer ${messages.count()} nye meldinger.",
        )

        run breakable@{
            messages.forEach { message ->
                KlageinstanshendelseMapper.map(message, topicName, clock).onRight {
                    klageinstanshendelseService.lagre(it)
                    offsets[TopicPartition(message.topic(), message.partition())] =
                        OffsetAndMetadata(message.offset() + 1)
                }.onLeft {
                    when (it) {
                        KunneIkkeMappeKlageinstanshendelse.FantIkkeEventId,
                        KunneIkkeMappeKlageinstanshendelse.FantIkkeKilde,
                        -> {
                            log.error("$topicName: $it. Key: ${message.key()}, partition: ${message.partition()}, offset: ${message.offset()}")
                            sikkerLogg.error("$topicName: $it. Key: ${message.key()}, Value: ${message.value()}, partition: ${message.partition()}, offset: ${message.offset()} ")
                            consumer.commitSync(offsets)
                            // Kafka tar ikke hensyn til offsetten vi comitter før det skjer en Rebalance.
                            // Vi kan tvinge en rebalance, dersom vi ikke ønsker neste event (som kan føre til at vi comitter lengre frem enn vi faktisk er)
                            // Hvis dette skjer vil Kafka prøve å sende meldingen på nytt mens den blokkerer nyere meldinger.
                            // Dersom dette oppstår kan vi vurdere om vi heller bare skal forkaste meldingen eller lagre den (da flytter vi kompleksiten inn i domenet.)
                            consumer.enforceRebalance()
                            delay(60.seconds)
                            return@breakable
                        }

                        is KunneIkkeMappeKlageinstanshendelse.IkkeAktuellOpplysningstype -> {
                            log.debug("$topicName: Forkastet hendelse med uaktuell kilde: ${it.kilde}, key: ${message.key()}, partition: ${message.partition()}, offset: ${message.offset()}\"")
                            sikkerLogg.debug("$topicName: Forkastet hendelse med uaktuell kilde: ${it.kilde}, key: ${message.key()}, value: ${message.value()} partition: ${message.partition()}, offset: ${message.offset()}\"")
                            offsets[TopicPartition(message.topic(), message.partition())] =
                                OffsetAndMetadata(message.offset() + 1)
                        }
                    }
                }
            }
            consumer.commitSync(offsets)
        }
        log.debug("$topicName: Prosessert ferdig meldingene.")
    }

    override fun stop() {
        log.info("$topicName: stop() kalt. Kanseller Couroutine Job. Inflight meldinger vil bli prosessert ferdig.")
        Either.catch {
            job.cancel()
        }.onLeft {
            log.error("$topicName: Feil under kansellering av Couroutine Job.", it)
        }.onRight {
            log.info("$topicName: Couroutine Job kansellert. Inflight meldinger vil bli prosessert ferdig.")
        }
    }
}
