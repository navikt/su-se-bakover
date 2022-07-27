package no.nav.su.se.bakover.common

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.time.Duration

// Environment bruker static context og er ikke thread safe
@Execution(value = ExecutionMode.SAME_THREAD)
internal class ApplicationConfigTest {

    private val expectedApplicationConfig = ApplicationConfig(
        runtimeEnvironment = ApplicationConfig.RuntimeEnvironment.Nais,
        naisCluster = ApplicationConfig.NaisCluster.Prod,
        leaderPodLookupPath = "leaderPodLookupPath",
        pdfgenLocal = false,
        serviceUser = ApplicationConfig.ServiceUserConfig(
            username = "username",
            password = "password",
        ),
        azure = ApplicationConfig.AzureConfig(
            clientSecret = "clientSecret",
            wellKnownUrl = "wellKnownUrl",
            clientId = "clientId",
            groups = ApplicationConfig.AzureConfig.AzureGroups(
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
            tilbakekreving = ApplicationConfig.OppdragConfig.TilbakekrevingConfig(
                mq = ApplicationConfig.OppdragConfig.TilbakekrevingConfig.Mq("tilbakekrevingMottak"),
                soap = ApplicationConfig.OppdragConfig.TilbakekrevingConfig.Soap("tilbakekrevingUrl"),
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
            dokDistUrl = "dokDistUrl",
            pdfgenUrl = "http://su-pdfgen.supstonad.svc.nais.local",
            dokarkivUrl = "dokarkivUrl",
            kodeverkUrl = "http://kodeverk.default.svc.nais.local",
            stsUrl = "stsUrl",
            skjermingUrl = "skjermingUrl",
            dkifUrl = "http://dkif.default.svc.nais.local",
            kabalConfig = ApplicationConfig.ClientsConfig.KabalConfig(
                url = "kabalUrl",
                clientId = "kabalClientId",
            ),
            safConfig = ApplicationConfig.ClientsConfig.SafConfig(
                url = "safUrl",
                clientId = "safClientId",
            ),
            maskinportenConfig = ApplicationConfig.ClientsConfig.MaskinportenConfig(
                clientId = "maskinporten_client_id",
                scopes = "maskinporten_scopes",
                clientJwk = "maskinporten_client_jwk",
                wellKnownUrl = "maskinporten_well_known_url",
                issuer = "maskinporten_issuer",
                jwksUri = "maskinporten_jwks_uri",
                tokenEndpoint = "maskinporten_token_endpoint",
            ),
            skatteetatenConfig = ApplicationConfig.ClientsConfig.SkatteetatenConfig(apiUri = "https://api-test.sits.no"),
        ),
        kafkaConfig = ApplicationConfig.KafkaConfig(
            producerCfg = ApplicationConfig.KafkaConfig.ProducerCfg(
                mapOf(
                    "bootstrap.servers" to "brokers",
                    "security.protocol" to "SSL",
                    "ssl.endpoint.identification.algorithm" to "",
                    "ssl.truststore.type" to "jks",
                    "ssl.keystore.type" to "PKCS12",
                    "ssl.truststore.location" to "truststorePath",
                    "ssl.truststore.password" to "credstorePwd",
                    "ssl.keystore.location" to "keystorePath",
                    "ssl.keystore.password" to "credstorePwd",
                    "ssl.key.password" to "credstorePwd",
                    "acks" to "all",
                    "key.serializer" to StringSerializer::class.java,
                    "value.serializer" to StringSerializer::class.java,
                ),
                retryTaskInterval = Duration.ofSeconds(15),
            ),
            consumerCfg = ApplicationConfig.KafkaConfig.ConsumerCfg(
                mapOf(
                    "bootstrap.servers" to "kafka_onprem_brokers",
                    "security.protocol" to "SASL_SSL",
                    "sasl.mechanism" to "PLAIN",
                    "sasl.jaas.config" to "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"username\" password=\"password\";",
                    "ssl.truststore.location" to "truststorePath",
                    "ssl.truststore.password" to "credstorePwd",
                    "ssl.key.password" to "credstorePwd",
                    "specific.avro.reader" to true,
                    "schema.registry.url" to "schema_onprem_registry",
                    "key.deserializer" to KafkaAvroDeserializer::class.java,
                    "value.deserializer" to KafkaAvroDeserializer::class.java,
                    "basic.auth.credentials.source" to "USER_INFO",
                    "basic.auth.user.info" to "usr:pwd",
                    "group.id" to "su-se-bakover",
                    "client.id" to "hostname",
                    "enable.auto.commit" to "false",
                    "max.poll.records" to 100,
                ),
            ),
        ),
        unleash = ApplicationConfig.UnleashConfig("https://unleash.nais.io/api", "su-se-bakover"),
        kabalKafkaConfig = ApplicationConfig.KabalKafkaConfig(
            kafkaConfig = mapOf(
                "bootstrap.servers" to "brokers",
                "security.protocol" to "SSL",
                "ssl.endpoint.identification.algorithm" to "",
                "ssl.truststore.type" to "jks",
                "ssl.keystore.type" to "PKCS12",
                "ssl.truststore.location" to "truststorePath",
                "ssl.truststore.password" to "credstorePwd",
                "ssl.keystore.location" to "keystorePath",
                "ssl.keystore.password" to "credstorePwd",
                "ssl.key.password" to "credstorePwd",
                "group.id" to "su-se-bakover",
                "client.id" to "hostname",
                "enable.auto.commit" to "false",
                "auto.offset.reset" to "earliest",
                "key.deserializer" to StringDeserializer::class.java,
                "value.deserializer" to StringDeserializer::class.java,
                "max.poll.records" to 100,
            ),
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
                "DOKARKIV_URL" to "dokarkivUrl",
                "STS_URL" to "stsUrl",
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
                "MASKINPORTEN_CLIENT_ID" to "maskinporten_client_id",
                "MASKINPORTEN_SCOPES" to "maskinporten_scopes",
                "MASKINPORTEN_CLIENT_JWK" to "maskinporten_client_jwk",
                "MASKINPORTEN_WELL_KNOWN_URL" to "maskinporten_well_known_url",
                "MASKINPORTEN_ISSUER" to "maskinporten_issuer",
                "MASKINPORTEN_JWKS_URI" to "maskinporten_jwks_uri",
                "MASKINPORTEN_TOKEN_ENDPOINT" to "maskinporten_token_endpoint",
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
                serviceUser = ApplicationConfig.ServiceUserConfig(
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
                    tilbakekreving = ApplicationConfig.OppdragConfig.TilbakekrevingConfig(
                        mq = ApplicationConfig.OppdragConfig.TilbakekrevingConfig.Mq("unused"),
                        soap = ApplicationConfig.OppdragConfig.TilbakekrevingConfig.Soap("unused"),
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
                    dokDistUrl = "mocked",
                    pdfgenUrl = "mocked",
                    dokarkivUrl = "mocked",
                    kodeverkUrl = "mocked",
                    stsUrl = "mocked",
                    skjermingUrl = "mocked",
                    dkifUrl = "mocked",
                    kabalConfig = ApplicationConfig.ClientsConfig.KabalConfig("mocked", "mocked"),
                    safConfig = ApplicationConfig.ClientsConfig.SafConfig("mocked", "mocked"),
                    maskinportenConfig = ApplicationConfig.ClientsConfig.MaskinportenConfig(
                        clientId = "mocked",
                        scopes = "mocked",
                        clientJwk = "mocked",
                        wellKnownUrl = "mocked",
                        issuer = "mocked",
                        jwksUri = "mocked",
                        tokenEndpoint = "mocked"
                    ),
                    skatteetatenConfig = ApplicationConfig.ClientsConfig.SkatteetatenConfig(apiUri = "mocked"),
                ),
                kafkaConfig = ApplicationConfig.KafkaConfig(
                    producerCfg = ApplicationConfig.KafkaConfig.ProducerCfg((emptyMap())),
                    consumerCfg = ApplicationConfig.KafkaConfig.ConsumerCfg(emptyMap()),
                ),
                unleash = ApplicationConfig.UnleashConfig("https://unleash.nais.io/api", "su-se-bakover"),
                kabalKafkaConfig = ApplicationConfig.KabalKafkaConfig(emptyMap()),
            )
        }
    }
}
