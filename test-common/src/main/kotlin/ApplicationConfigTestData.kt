package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.ApplicationConfig

fun applicationConfig() = ApplicationConfig(
    runtimeEnvironment = ApplicationConfig.RuntimeEnvironment.Test,
    naisCluster = null,
    gitCommit = null,
    leaderPodLookupPath = "leaderPodLookupPath",
    pdfgenLocal = false,
    serviceUser = ApplicationConfig.ServiceUserConfig(
        username = "serviceUserTestUsername",
        password = "serviceUserTestPassword",
    ),
    azure = ApplicationConfig.AzureConfig(
        clientSecret = "testClientSecret",
        wellKnownUrl = "http://localhost/test/wellKnownUrl",
        clientId = "testClientId",
        groups = ApplicationConfig.AzureConfig.AzureGroups(
            attestant = "testAzureGroupAttestant",
            saksbehandler = "testAzureGroupSaksbehandler",
            veileder = "testAzureGroupVeileder",
            drift = "testAzureGroupDrift",
        ),
    ),
    frikort = ApplicationConfig.FrikortConfig(
        serviceUsername = listOf("frikort"),
        useStubForSts = true,
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
        simulering = ApplicationConfig.OppdragConfig.SimuleringConfig(
            url = "simuleringTestUrl",
            stsSoapUrl = "simuleringStsTestSoapUrl",
        ),
        tilbakekreving = ApplicationConfig.OppdragConfig.TilbakekrevingConfig(
            mq = ApplicationConfig.OppdragConfig.TilbakekrevingConfig.Mq(
                mottak = "tilbakekrevingMqTestSendQueue",
            ),
            soap = ApplicationConfig.OppdragConfig.TilbakekrevingConfig.Soap(
                url = "tilbakekrevingUrl",
            ),
        ),
    ),
    database = ApplicationConfig.DatabaseConfig.StaticCredentials(
        jdbcUrl = "jdbcTestUrl",
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
        dokDistUrl = "dokDistUrl",
        pdfgenUrl = "pdfgenUrl",
        dokarkivUrl = "dokarkivUrl",
        kodeverkUrl = "kodeverkUrl",
        stsUrl = "stsUrl",
        skjermingUrl = "skjermingUrl",
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
        maskinportenConfig = ApplicationConfig.ClientsConfig.MaskinportenConfig(
            clientId = "maskinportenClientId",
            scopes = "maskinportenScopes",
            clientJwk = "maskinportenClientJwk",
            wellKnownUrl = "maskinportenWellKnownUrl",
            issuer = "maskinportenIssuer",
            jwksUri = "maskinportenJwksUri",
            tokenEndpoint = "maskinporteTokenEndpointn",
        ),
        skatteetatenConfig = ApplicationConfig.ClientsConfig.SkatteetatenConfig(apiUri = "a"),
    ),
    kafkaConfig = ApplicationConfig.KafkaConfig(
        producerCfg = ApplicationConfig.KafkaConfig.ProducerCfg(emptyMap()),
        consumerCfg = ApplicationConfig.KafkaConfig.ConsumerCfg(emptyMap()),
    ),
    unleash = ApplicationConfig.UnleashConfig("https://localhost", "su-se-bakover"),
    kabalKafkaConfig = ApplicationConfig.KabalKafkaConfig(
        kafkaConfig = emptyMap(),
    ),
)
