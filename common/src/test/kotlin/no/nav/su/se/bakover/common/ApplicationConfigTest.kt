package no.nav.su.se.bakover.common

import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class ApplicationConfigTest {

    private val expectedApplicationConfig = ApplicationConfig(
        isLocalOrRunningTests = false,
        serviceUser = ApplicationConfig.ServiceUserConfig(
            username = "username",
            password = "password"
        ),
        azure = ApplicationConfig.AzureConfig(
            clientSecret = "clientSecret",
            wellKnownUrl = "wellKnownUrl",
            clientId = "clientId",
            backendCallbackUrl = "backendCallbackUrl",
            groups = ApplicationConfig.AzureConfig.AzureGroups(
                attestant = "attestant",
                saksbehandler = "saksbehandler",
                veileder = "veileder"
            )
        ),
        oppdrag = ApplicationConfig.OppdragConfig(
            mqQueueManager = "oppdragMqQueueManager",
            mqPort = 77665,
            mqHostname = "mqHostname",
            mqChannel = "mqChannel",
            utbetaling = ApplicationConfig.OppdragConfig.UtbetalingConfig(
                mqSendQueue = "utbetalingMqSendQueue",
                mqReplyTo = "utbetalingMqReplyTo"
            ),
            avstemming = ApplicationConfig.OppdragConfig.AvstemmingConfig(mqSendQueue = "avstemmingMqSendQueue"),
            simulering = ApplicationConfig.OppdragConfig.SimuleringConfig(
                url = "simuleringUrl",
                stsSoapUrl = "stsSoapUrl"
            )
        ),
        database = ApplicationConfig.DatabaseConfig(
            databaseName = "databaseName",
            jdbcUrl = "jdbcUrl",
            vaultMountPath = "vaultMountPath",
        ),
        clientsConfig = ApplicationConfig.ClientsConfig(
            oppgaveConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = "oppgaveClientId",
                url = "oppgaveUrl"
            )
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
                "BACKEND_CALLBACK_URL" to "backendCallbackUrl",
                "AZURE_GROUP_ATTESTANT" to "attestant",
                "AZURE_GROUP_SAKSBEHANDLER" to "saksbehandler",
                "AZURE_GROUP_VEILEDER" to "veileder",
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
            )
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
                "BACKEND_CALLBACK_URL" to "backendCallbackUrl",
                "AZURE_GROUP_ATTESTANT" to "attestant",
                "AZURE_GROUP_SAKSBEHANDLER" to "saksbehandler",
                "AZURE_GROUP_VEILEDER" to "veileder",
            )
        ) {
            ApplicationConfig.createLocalConfig() shouldBe expectedApplicationConfig.copy(
                isLocalOrRunningTests = true,
                serviceUser = ApplicationConfig.ServiceUserConfig(
                    username = "unused",
                    password = "unused"
                ),
                oppdrag = ApplicationConfig.OppdragConfig(
                    mqQueueManager = "unused",
                    mqPort = -1,
                    mqHostname = "unused",
                    mqChannel = "unused",
                    utbetaling = ApplicationConfig.OppdragConfig.UtbetalingConfig(
                        mqSendQueue = "unused",
                        mqReplyTo = "unused"
                    ),
                    avstemming = ApplicationConfig.OppdragConfig.AvstemmingConfig(mqSendQueue = "unused"),
                    simulering = ApplicationConfig.OppdragConfig.SimuleringConfig(
                        url = "unused",
                        stsSoapUrl = "unused"
                    )
                ),
                database = ApplicationConfig.DatabaseConfig(
                    databaseName = "supstonad-db-local",
                    jdbcUrl = "jdbc:postgresql://localhost:5432/supstonad-db-local",
                    vaultMountPath = ""
                ),
                clientsConfig = ApplicationConfig.ClientsConfig(
                    oppgaveConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                        clientId = "unused",
                        url = "unused"
                    )
                )
            )
        }
    }
}
