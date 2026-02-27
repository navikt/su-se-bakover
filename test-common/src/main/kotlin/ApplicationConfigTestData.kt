package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.domain.config.ServiceUserConfig
import no.nav.su.se.bakover.common.domain.config.TilbakekrevingConfig
import no.nav.su.se.bakover.common.infrastructure.brukerrolle.AzureGroups
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.config.AzureConfig

fun applicationConfig() = ApplicationConfig(
    runtimeEnvironment = ApplicationConfig.RuntimeEnvironment.Test,
    naisCluster = null,
    gitCommit = null,
    leaderPodLookupPath = "leaderPodLookupPath",
    pdfgenLocal = false,
    serviceUser = ServiceUserConfig(
        username = "serviceUserTestUsername",
        password = "serviceUserTestPassword",
    ),
    azure = AzureConfig(
        clientSecret = "testClientSecret",
        wellKnownUrl = "http://localhost/test/wellKnownUrl",
        clientId = "testClientId",
        groups = AzureGroups(
            attestant = "testAzureGroupAttestant",
            saksbehandler = "testAzureGroupSaksbehandler",
            veileder = "testAzureGroupVeileder",
            drift = "testAzureGroupDrift",
        ),
    ),
    oppdrag = ApplicationConfig.OppdragConfig(
        mqQueueManager = "testMqQueueManager",
        mqPort = -22,
        mqHostname = "testMqHostname",
        mqChannel = "testMqChannel",
        utbetaling = ApplicationConfig.OppdragConfig.UtbetalingConfig(
            mqSendQueue = "testMqSendQueue",
            mqReplyTo = "testMqReplyTo",
        ),
        avstemming = ApplicationConfig.OppdragConfig.AvstemmingConfig(mqSendQueue = "avstemmingMqTestSendQueue"),
        tilbakekreving = TilbakekrevingConfig(
            mq = TilbakekrevingConfig.Mq(
                mottak = "tilbakekrevingMqTestSendQueue",
            ),
            serviceUserConfig = ServiceUserConfig(
                username = "tilbakekrevingServiceUserTestUsername",
                password = "tilbakekrevingServiceUserTestPassword",
            ),
        ),
    ),
    database = ApplicationConfig.DatabaseConfig.StaticCredentials(
        jdbcUrl = "jdbcTestUrl",
        username = "user",
        password = "pwd",
    ),
    clientsConfig = ApplicationConfig.ClientsConfig(
        oppgaveConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
            clientId = "oppgaveClientId",
            url = "oppgaveUrl",
        ),
        pdlConfig = ApplicationConfig.ClientsConfig.PdlConfig(
            url = "pdlUrl",
            clientId = "pdlClientId",
        ),
        pdfgenUrl = "pdfgenUrl",
        kontaktOgReservasjonsregisterConfig = ApplicationConfig.ClientsConfig.KontaktOgReservasjonsregisterConfig(
            appId = "krrId",
            url = "krrUrl",
        ),
        kabalConfig = ApplicationConfig.ClientsConfig.KabalConfig(
            url = "kabalUrl",
            clientId = "KabalClientId",
        ),
        safConfig = ApplicationConfig.ClientsConfig.SafConfig(
            url = "safUrl",
            clientId = "safClientId",
        ),
        skatteetatenConfig = ApplicationConfig.ClientsConfig.SkatteetatenConfig(
            apiBaseUrl = "a",
            clientId = "skattClientId",
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
        skjermingConfig = ApplicationConfig.ClientsConfig.SkjermingConfig(
            url = "skjermingUrl",
            clientId = "skjermingClientId",
        ),
        pesysConfig = ApplicationConfig.ClientsConfig.PesysConfig.createLocalConfig(),
        aapApiInternConfig = ApplicationConfig.ClientsConfig.AapApiInternConfig.createLocalConfig(),
        suProxyConfig = ApplicationConfig.ClientsConfig.SuProxyConfig.createLocalConfig(),
    ),
    kafkaConfig = ApplicationConfig.KafkaConfig(
        producerCfg = ApplicationConfig.KafkaConfig.ProducerCfg(emptyMap()),
        consumerCfg = ApplicationConfig.KafkaConfig.ConsumerCfg(emptyMap()),
    ),
    kabalKafkaConfig = ApplicationConfig.KabalKafkaConfig(
        kafkaConfig = emptyMap(),
    ),
    institusjonsoppholdKafkaConfig = ApplicationConfig.InstitusjonsoppholdKafkaConfig(
        kafkaConfig = mapOf(),
        topicName = "stubbedInstitusjonsoppholdTopicName",
    ),

)
