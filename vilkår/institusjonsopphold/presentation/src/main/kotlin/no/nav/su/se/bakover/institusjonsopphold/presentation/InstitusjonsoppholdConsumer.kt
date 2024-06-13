package no.nav.su.se.bakover.institusjonsopphold.presentation

import arrow.core.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.consumer.StoppableConsumer
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationIdSuspend
import no.nav.su.se.bakover.institusjonsopphold.application.service.EksternInstitusjonsoppholdKonsument
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * https://github.com/navikt/institusjon/tree/main/apps/institusjon-opphold-hendelser
 */
class InstitusjonsoppholdConsumer private constructor(
    private val config: ApplicationConfig.InstitusjonsoppholdKafkaConfig,
    private val topicName: String = config.topicName,
    private val institusjonsoppholdService: EksternInstitusjonsoppholdKonsument,
    private val pollTimeoutDuration: Duration = Duration.ofMillis(500),
    private val log: Logger = LoggerFactory.getLogger(InstitusjonsoppholdConsumer::class.java),
    private val consumer: KafkaConsumer<String, String> = KafkaConsumer(config.kafkaConfig),
) : StoppableConsumer {

    constructor(
        config: ApplicationConfig.InstitusjonsoppholdKafkaConfig,
        institusjonsoppholdService: EksternInstitusjonsoppholdKonsument,
    ) : this(
        config = config,
        topicName = config.topicName,
        institusjonsoppholdService = institusjonsoppholdService,
    )
    override val consumerName = topicName

    private val job: Job

    init {
        log.info("InstitusjonsoppholdConsumer settes opp. Lytter på $topicName")
        consumer.subscribe(listOf(topicName))

        job = CoroutineScope(Dispatchers.IO).launch {
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
                    log.error("InstitusjonsoppholdConsumer: Ukjent feil ved konsumering. Tvinger en rebalansering.", it)
                    consumer.enforceRebalance()
                    delay(5.seconds)
                }
            }
            log.info("InstitusjonsoppholdConsumer: Stopper konsumering og frigjør ressurser.")
            Either.catch {
                consumer.close()
            }.onLeft {
                log.error("Feil ved stopp av InstitusjonsoppholdConsumer", it)
            }.onRight {
                log.info("InstitusjonsoppholdConsumer: KafkaConsumer stoppet.")
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

    /**
     * Stopper konsumering av hendelser. Pågående meldinger vil bli prosessert ferdig.
     */
    override fun stop() {
        log.info("InstitusjonsoppholdConsumer: Stopper konsumering av hendelser. Inflight meldinger vil bli prosessert ferdig.")
        Either.catch {
            job.cancel(CancellationException("InstitusjonsoppholdConsumer: stop() kalt. Forventet ved shutdown."))
        }.onLeft {
            log.error("InstitusjonsoppholdConsumer: Feil ved stopp av konsumering.", it)
        }.onRight {
            log.info("InstitusjonsoppholdConsumer: Courotine Job kansellert. Inflight meldinger vil bli prosessert ferdig.")
        }
    }
}
