package no.nav.su.se.bakover.common.infrastructure.config

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.SU_SE_BAKOVER_CONSUMER_ID
import no.nav.su.se.bakover.common.domain.config.ServiceUserConfig
import no.nav.su.se.bakover.common.domain.config.TilbakekrevingConfig
import no.nav.su.se.bakover.common.infrastructure.brukerrolle.AzureGroups
import no.nav.su.se.bakover.common.infrastructure.git.GitCommit
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.time.Duration

// Environment bruker static context og er ikke thread safe
@Execution(value = ExecutionMode.SAME_THREAD)
class ApplicationConfigTest {

    private val sslConfig = mapOf(
        "ssl.endpoint.identification.algorithm" to "",
        "ssl.truststore.type" to "jks",
        "ssl.keystore.type" to "PKCS12",
        "ssl.truststore.location" to "truststorePath",
        "ssl.truststore.password" to "credstorePwd",
        "ssl.keystore.location" to "keystorePath",
        "ssl.keystore.password" to "credstorePwd",
        "ssl.key.password" to "credstorePwd",
    )
    private val kafkaConfig = sslConfig + mapOf(
        "bootstrap.servers" to "brokers",
        "security.protocol" to "SSL",
        "group.id" to "su-se-bakover",
        "client.id" to "hostname",
        "enable.auto.commit" to "false",
        "auto.offset.reset" to "earliest",
        "key.deserializer" to StringDeserializer::class.java,
        "value.deserializer" to StringDeserializer::class.java,
        "max.poll.records" to 100,
    )

    private val expectedApplicationConfig = ApplicationConfig(
        runtimeEnvironment = ApplicationConfig.RuntimeEnvironment.Nais,
        naisCluster = ApplicationConfig.NaisCluster.Prod,
        gitCommit = GitCommit("87a3a5155bf00b4d6854efcc24e8b929549c9302"),
        leaderPodLookupPath = "leaderPodLookupPath",
        pdfgenLocal = false,
        serviceUser = ServiceUserConfig(
            username = "username",
            password = "password",
        ),
        azure = AzureConfig(
            clientSecret = "clientSecret",
            wellKnownUrl = "wellKnownUrl",
            clientId = "clientId",
            groups = AzureGroups(
                attestant = "attestant",
                saksbehandler = "saksbehandler",
                veileder = "veileder",
                drift = "drift",
            ),
        ),
        frikort = ApplicationConfig.FrikortConfig(
            serviceUsername = listOf("frikort"),
            useStubForSts = false,
        ),
        oppdrag = ApplicationConfig.OppdragConfig(
            mqQueueManager = "oppdragMqQueueManager",
            mqPort = 77665,
            mqHostname = "mqHostname",
            mqChannel = "mqChannel",
            utbetaling = ApplicationConfig.OppdragConfig.UtbetalingConfig(
                mqSendQueue = "utbetalingMqSendQueue",
                mqReplyTo = "utbetalingMqReplyTo",
            ),
            avstemming = ApplicationConfig.OppdragConfig.AvstemmingConfig(mqSendQueue = "avstemmingMqSendQueue"),
            simulering = ApplicationConfig.OppdragConfig.SimuleringConfig(
                url = "simuleringUrl",
                stsSoapUrl = "stsSoapUrl",
            ),
            tilbakekreving = TilbakekrevingConfig(
                mq = TilbakekrevingConfig.Mq("tilbakekrevingMottak"),
                soap = TilbakekrevingConfig.Soap("tilbakekrevingUrl", "stsSoapUrl"),
                serviceUserConfig = ServiceUserConfig(
                    username = "username",
                    password = "password",
                ),
            ),
        ),
        database = ApplicationConfig.DatabaseConfig.RotatingCredentials(
            databaseName = "databaseName",
            jdbcUrl = "jdbcUrl",
            vaultMountPath = "vaultMountPath",
        ),
        clientsConfig = ApplicationConfig.ClientsConfig(
            oppgaveConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = "oppgaveUrl",
            ),
            pdlConfig = ApplicationConfig.ClientsConfig.PdlConfig(
                url = "http://pdl-api.default.svc.nais.local",
                clientId = "pdlClientId",
            ),
            pdfgenUrl = "http://su-pdfgen.supstonad.svc.nais.local",
            stsUrl = "stsUrl",
            stsSamlUrl = "stsSamlUrl",
            skjermingUrl = "skjermingUrl",
            kontaktOgReservasjonsregisterConfig = ApplicationConfig.ClientsConfig.KontaktOgReservasjonsregisterConfig(
                appId = "krrId",
                url = "krrUrl",
            ),
            kabalConfig = ApplicationConfig.ClientsConfig.KabalConfig(
                url = "kabalUrl",
                clientId = "kabalClientId",
            ),
            safConfig = ApplicationConfig.ClientsConfig.SafConfig(
                url = "safUrl",
                clientId = "safClientId",
            ),
            skatteetatenConfig = ApplicationConfig.ClientsConfig.SkatteetatenConfig(
                apiBaseUrl = "skatteetatenUrl",
                clientId = "skattClientId",
                consumerId = SU_SE_BAKOVER_CONSUMER_ID,
            ),
            dokArkivConfig = ApplicationConfig.ClientsConfig.DokArkivConfig(
                url = "dokArkivUrl",
                clientId = "dokArkivClientId",
            ),
            dokDistConfig = ApplicationConfig.ClientsConfig.DokDistConfig(
                url = "dokDistUrl",
                clientId = "dokDistClientId",
            ),
            kodeverkConfig = ApplicationConfig.ClientsConfig.KodeverkConfig(
                url = "kodeverkUrl",
                clientId = "kodeverkClientId",
            ),
        ),
        kafkaConfig = ApplicationConfig.KafkaConfig(
            producerCfg = ApplicationConfig.KafkaConfig.ProducerCfg(
                sslConfig + mapOf(
                    "bootstrap.servers" to "brokers",
                    "security.protocol" to "SSL",
                    "acks" to "all",
                    "key.serializer" to StringSerializer::class.java,
                    "value.serializer" to StringSerializer::class.java,
                ),
                retryTaskInterval = Duration.ofSeconds(15),
            ),
            consumerCfg = ApplicationConfig.KafkaConfig.ConsumerCfg(
                kafkaConfig + mapOf(
                    "value.deserializer" to KafkaAvroDeserializer::class.java,
                    "specific.avro.reader" to true,
                    "schema.registry.url" to "some-schema-url",
                    "basic.auth.credentials.source" to "USER_INFO",
                    "basic.auth.user.info" to "usr:pwd",
                ),
            ),
        ),
        kabalKafkaConfig = ApplicationConfig.KabalKafkaConfig(
            kafkaConfig = kafkaConfig,
        ),
        institusjonsoppholdKafkaConfig = ApplicationConfig.InstitusjonsoppholdKafkaConfig(
            kafkaConfig = kafkaConfig,
            topicName = "INSTITUSJONSOPPHOLD_TOPIC",
        ),
    )

    @Test
    fun `environment variables`() {
        withEnvironment(
            mapOf(
                "NAIS_CLUSTER_NAME" to "prod-fss",
                "username" to "username",
                "password" to "password",
                "AZURE_APP_CLIENT_SECRET" to "clientSecret",
                "AZURE_APP_WELL_KNOWN_URL" to "wellKnownUrl",
                "AZURE_APP_CLIENT_ID" to "clientId",
                "AZURE_GROUP_ATTESTANT" to "attestant",
                "AZURE_GROUP_SAKSBEHANDLER" to "saksbehandler",
                "AZURE_GROUP_VEILEDER" to "veileder",
                "AZURE_GROUP_DRIFT" to "drift",
                "FRIKORT_SERVICE_USERNAME" to "frikort",
                "MQ_QUEUE_MANAGER" to "oppdragMqQueueManager",
                "MQ_PORT" to "77665",
                "MQ_HOSTNAME" to "mqHostname",
                "MQ_CHANNEL" to "mqChannel",
                "MQ_SEND_QUEUE_UTBETALING" to "utbetalingMqSendQueue",
                "MQ_REPLY_TO" to "utbetalingMqReplyTo",
                "MQ_SEND_QUEUE_AVSTEMMING" to "avstemmingMqSendQueue",
                "SIMULERING_URL" to "simuleringUrl",
                "STS_URL_SOAP" to "stsSoapUrl",
                "DATABASE_NAME" to "databaseName",
                "DATABASE_JDBC_URL" to "jdbcUrl",
                "VAULT_MOUNTPATH" to "vaultMountPath",
                "OPPGAVE_CLIENT_ID" to "oppgaveClientId",
                "OPPGAVE_URL" to "oppgaveUrl",
                "DOKDIST_URL" to "dokDistUrl",
                "DOKDIST_CLIENT_ID" to "dokDistClientId",
                "DOKARKIV_URL" to "dokArkivUrl",
                "DOKARKIV_CLIENT_ID" to "dokArkivClientId",
                "STS_URL" to "stsUrl",
                "GANDALF_URL" to "stsSamlUrl",
                "SKJERMING_URL" to "skjermingUrl",
                "ELECTOR_PATH" to "leaderPodLookupPath",
                "PDL_CLIENT_ID" to "pdlClientId",
                "KABAL_URL" to "kabalUrl",
                "KABAL_CLIENT_ID" to "kabalClientId",
                "SAF_URL" to "safUrl",
                "SAF_CLIENT_ID" to "safClientId",
                "HOSTNAME" to "hostname",
                "MQ_TILBAKEKREVING_MOTTAK" to "tilbakekrevingMottak",
                "TILBAKEKREVING_URL" to "tilbakekrevingUrl",
                "KRR_URL" to "krrUrl",
                "KRR_APP_ID" to "krrId",
                "NAIS_APP_IMAGE" to "ghcr.io/navikt/su-se-bakover/su-se-bakover:87a3a5155bf00b4d6854efcc24e8b929549c9302",
                "KAFKA_SCHEMA_REGISTRY" to "some-schema-url",
                "SKATTEETATEN_URL" to "skatteetatenUrl",
                "SKATT_CLIENT_ID" to "skattClientId",
                "INSTITUSJONSOPPHOLD_TOPIC" to "INSTITUSJONSOPPHOLD_TOPIC",
                "KODEVERK_URL" to "kodeverkUrl",
                "KODEVERK_CLIENT_ID" to "kodeverkClientId",
            ),
        ) {
            ApplicationConfig.createFromEnvironmentVariables() shouldBe expectedApplicationConfig
        }
    }

    @Test
    fun `local config`() {
        withEnvironment(
            mapOf(
                "AZURE_APP_CLIENT_SECRET" to "clientSecret",
                "AZURE_APP_WELL_KNOWN_URL" to "wellKnownUrl",
                "AZURE_APP_CLIENT_ID" to "clientId",
                "AZURE_GROUP_ATTESTANT" to "attestant",
                "AZURE_GROUP_SAKSBEHANDLER" to "saksbehandler",
                "AZURE_GROUP_VEILEDER" to "veileder",
                "AZURE_GROUP_DRIFT" to "drift",
            ),
        ) {
            ApplicationConfig.createLocalConfig() shouldBe expectedApplicationConfig.copy(
                runtimeEnvironment = ApplicationConfig.RuntimeEnvironment.Local,
                naisCluster = null,
                leaderPodLookupPath = "",
                serviceUser = ServiceUserConfig(
                    username = "unused",
                    password = "unused",
                ),
                frikort = ApplicationConfig.FrikortConfig(
                    serviceUsername = listOf("frikort"),
                    useStubForSts = true,
                ),
                oppdrag = ApplicationConfig.OppdragConfig(
                    mqQueueManager = "unused",
                    mqPort = -1,
                    mqHostname = "unused",
                    mqChannel = "unused",
                    utbetaling = ApplicationConfig.OppdragConfig.UtbetalingConfig(
                        mqSendQueue = "unused",
                        mqReplyTo = "unused",
                    ),
                    avstemming = ApplicationConfig.OppdragConfig.AvstemmingConfig(mqSendQueue = "unused"),
                    simulering = ApplicationConfig.OppdragConfig.SimuleringConfig(
                        url = "unused",
                        stsSoapUrl = "unused",
                    ),
                    tilbakekreving = TilbakekrevingConfig(
                        mq = TilbakekrevingConfig.Mq("unused"),
                        soap = TilbakekrevingConfig.Soap("unused", "unused"),
                        serviceUserConfig = ServiceUserConfig(
                            username = "unused",
                            password = "unused",
                        ),
                    ),
                ),
                database = ApplicationConfig.DatabaseConfig.StaticCredentials(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/supstonad-db-local",
                ),
                clientsConfig = ApplicationConfig.ClientsConfig(
                    oppgaveConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                        clientId = "mocked",
                        url = "mocked",
                    ),
                    pdlConfig = ApplicationConfig.ClientsConfig.PdlConfig(
                        url = "mocked",
                        clientId = "mocked",
                    ),
                    pdfgenUrl = "mocked",
                    stsUrl = "mocked",
                    stsSamlUrl = "mocked",
                    skjermingUrl = "mocked",
                    kontaktOgReservasjonsregisterConfig = ApplicationConfig.ClientsConfig.KontaktOgReservasjonsregisterConfig(
                        appId = "mocked",
                        url = "mocked",
                    ),
                    kabalConfig = ApplicationConfig.ClientsConfig.KabalConfig(
                        url = "mocked",
                        clientId = "mocked",
                    ),
                    safConfig = ApplicationConfig.ClientsConfig.SafConfig(
                        url = "mocked",
                        clientId = "mocked",
                    ),
                    skatteetatenConfig = ApplicationConfig.ClientsConfig.SkatteetatenConfig(
                        apiBaseUrl = "mocked",
                        clientId = "mocked",
                        consumerId = "srvsupstonad",
                    ),
                    dokArkivConfig = ApplicationConfig.ClientsConfig.DokArkivConfig(
                        url = "mocked",
                        clientId = "mocked",
                    ),
                    dokDistConfig = ApplicationConfig.ClientsConfig.DokDistConfig(
                        url = "mocked",
                        clientId = "mocked",
                    ),
                    kodeverkConfig = ApplicationConfig.ClientsConfig.KodeverkConfig(
                        url = "mocked",
                        clientId = "mocked",
                    ),
                ),
                kafkaConfig = ApplicationConfig.KafkaConfig(
                    producerCfg = ApplicationConfig.KafkaConfig.ProducerCfg((emptyMap())),
                    consumerCfg = ApplicationConfig.KafkaConfig.ConsumerCfg(emptyMap()),
                ),
                kabalKafkaConfig = ApplicationConfig.KabalKafkaConfig(emptyMap()),
                institusjonsoppholdKafkaConfig = ApplicationConfig.InstitusjonsoppholdKafkaConfig(
                    emptyMap(),
                    "INSTITUSJONSOPPHOLD_TOPIC",
                ),
            )
        }
    }
}
