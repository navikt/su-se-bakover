package no.nav.su.se.bakover.institusjonsopphold.presentation

import arrow.core.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationIdSuspend
import no.nav.su.se.bakover.institusjonsopphold.application.service.EksternInstitusjonsoppholdKonsument
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * https://github.com/navikt/institusjon/tree/main/apps/institusjon-opphold-hendelser
 */
class InstitusjonsoppholdConsumer private constructor(
    private val config: ApplicationConfig.InstitusjonsoppholdKafkaConfig,
    private val topicName: String = config.topicName,
    private val institusjonsoppholdService: EksternInstitusjonsoppholdKonsument,
    private val pollTimeoutDuration: Duration = Duration.ofMillis(500),
    private val clock: Clock,
    private val log: Logger = LoggerFactory.getLogger(InstitusjonsoppholdConsumer::class.java),
    private val sikkerLogg: Logger = no.nav.su.se.bakover.common.sikkerLogg,
    private val consumer: KafkaConsumer<String, String> = KafkaConsumer(config.kafkaConfig),
) {
    constructor(
        config: ApplicationConfig.InstitusjonsoppholdKafkaConfig,
        institusjonsoppholdService: EksternInstitusjonsoppholdKonsument,
        clock: Clock,
    ) : this(
        config = config,
        topicName = config.topicName,
        institusjonsoppholdService = institusjonsoppholdService,
        clock = clock,
    )

    init {
        log.info("InstitusjonsoppholdConsumer settes opp. Lytter på $topicName")
        consumer.subscribe(listOf(topicName))

        CoroutineScope(Dispatchers.IO).launch {
            while (this.isActive) {
                Either.catch {
                    withCorrelationIdSuspend { correlationId ->
                        val messages = consumer.poll(pollTimeoutDuration)
                        if (!messages.isEmpty) {
                            consume(messages, correlationId)
                        }
                    }
                }.mapLeft {
                    // Dette vil føre til en timeout, siden vi ikke gjør noen commit. Da vil vi ikke få noen meldinger i mellomtiden.
                    log.error("InstitusjonsoppholdConsumer: Ukjent feil ved konsumering.", it)
                    consumer.enforceRebalance()
                    delay(5.seconds)
                }
            }
        }
    }

    private fun consume(
        messages: ConsumerRecords<String, String>,
        correlationId: CorrelationId,
    ) {
        val offsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        log.debug("InstitusjonsoppholdConsumer: Prosesserer ${messages.count()} nye meldinger.")

        run offsets@{
            messages.forEach { message ->
                val eksternInstHendelse = deserialize<EksternInstitusjonsoppholdHendelseJson>(message.value())
                institusjonsoppholdService.process(eksternInstHendelse.toDomain(), correlationId)
                offsets[TopicPartition(message.topic(), message.partition())] = OffsetAndMetadata(message.offset() + 1)
            }
            consumer.commitSync(offsets)
        }
        log.debug("$topicName: Prosessert ferdig meldingene.")
    }
}
