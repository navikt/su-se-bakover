package no.nav.su.se.bakover.client.oppgave

import arrow.core.left
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.forbidden
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.argThat
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Config
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Includes
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oppgave.oppgaveId
import no.nav.su.se.bakover.test.wiremock.startedWireMockServerWithCorrelationId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.util.UUID

internal class OppgaveV2HttpClientTest {

    private val clientId = "oppgaveClientId"
    private val bearerToken = "Bearer token"
    private val idempotencyKey = UUID.fromString("0f4cbb5c-9c59-4383-8a62-3c827465d174")

    private data class OppgaveOgAzure(
        val client: OppgaveV2HttpClient,
        val azure: AzureAd,
    )

    private fun createOppgaveClientWithAzure(
        azureAdMock: AzureAd = mock<AzureAd> { on { onBehalfOfToken(any(), any()) } doReturn "token" },
        baseUrl: String,
    ): OppgaveOgAzure {
        val client = OppgaveV2HttpClient(
            connectionConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
                clientId = clientId,
                url = baseUrl,
            ),
            exchange = azureAdMock,
        )

        return OppgaveOgAzure(client, azureAdMock)
    }

    private fun WireMockServer.stubOppgave(expectedBody: String, response: String, status: Int = 201) {
        stubFor(
            stubMapping
                .withHeader("Idempotency-Key", equalTo(idempotencyKey.toString()))
                .withRequestBody(equalToJson(expectedBody))
                .willReturn(aResponse().withBody(response).withStatus(status)),
        )
    }

    @Test
    fun `oppretter v2-oppgave for saksbehandler med meta`() {
        startedWireMockServerWithCorrelationId {
            val oppgave = søknadOppgave(tilordnetRessurs = "Z12345")
            val expectedRequest = createOppgaveRequest(
                beskrivelse = oppgave.beskrivelse,
                tilordnetRessurs = "Z12345",
                journalpostId = "444",
                representertEnhetsnr = "4815",
            )
            val response = hentOppgaveResponse(
                beskrivelse = oppgave.beskrivelse,
                tilordnetRessurs = "Z12345",
            )
            stubOppgave(expectedRequest, response)

            val clientOgAzure = createOppgaveClientWithAzure(baseUrl = baseUrl())
            val actual = clientOgAzure.client.opprettOppgave(
                config = oppgave,
                representertEnhetsnr = "4815",
                idempotencyKey = idempotencyKey,
            ).getOrFail()

            verify(clientOgAzure.azure).onBehalfOfToken(
                originalToken = argThat { it shouldBe bearerToken },
                otherAppId = argThat { it shouldBe clientId },
            )
            verifyNoMoreInteractions(clientOgAzure.azure)

            assertOppgaveEquals(
                actual = actual,
                expected = OppgaveHttpKallResponse(
                    oppgaveId = oppgaveId,
                    oppgavetype = Oppgavetype.BEHANDLE_SAK,
                    request = expectedRequest,
                    response = response,
                    beskrivelse = oppgave.beskrivelse,
                    tilordnetRessurs = "Z12345",
                    tildeltEnhetsnr = "4815",
                ),
            )
        }
    }

    @Test
    fun `oppretter v2-oppgave med systembruker og include-query`() {
        startedWireMockServerWithCorrelationId {
            val oppgave = søknadOppgave()
            val expectedRequest = createOppgaveRequest(
                beskrivelse = oppgave.beskrivelse,
                journalpostId = "444",
            )
            val response = hentOppgaveResponse(beskrivelse = oppgave.beskrivelse)

            stubFor(
                WireMock.post(urlPathEqualTo(OPPGAVE_V2_PATH))
                    .withQueryParam("include", equalTo("kommentarer"))
                    .withHeader("Authorization", equalTo(bearerToken))
                    .withHeader("X-Correlation-ID", equalTo("correlationId"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withHeader("Idempotency-Key", equalTo(idempotencyKey.toString()))
                    .withRequestBody(equalToJson(expectedRequest))
                    .willReturn(aResponse().withBody(response).withStatus(200)),
            )

            val clientOgAzure = createOppgaveClientWithAzure(
                baseUrl = baseUrl(),
                azureAdMock = mock<AzureAd> { on { getSystemToken(any()) } doReturn "token" },
            )
            val actual = clientOgAzure.client.opprettOppgaveMedSystembruker(
                config = oppgave,
                idempotencyKey = idempotencyKey,
                include = listOf(OppgaveV2Includes.KOMMENTARER),
            ).getOrFail()

            verify(clientOgAzure.azure).getSystemToken(any())
            verifyNoMoreInteractions(clientOgAzure.azure)

            assertOppgaveEquals(
                actual = actual,
                expected = OppgaveHttpKallResponse(
                    oppgaveId = oppgaveId,
                    oppgavetype = Oppgavetype.BEHANDLE_SAK,
                    request = expectedRequest,
                    response = response,
                    beskrivelse = oppgave.beskrivelse,
                    tilordnetRessurs = null,
                    tildeltEnhetsnr = "4815",
                ),
            )
        }
    }

    @Test
    fun `oppretter v2-oppgave med systembruker uten meta`() {
        startedWireMockServerWithCorrelationId {
            val oppgave = søknadOppgave()
            val expectedRequest = createOppgaveRequest(
                beskrivelse = oppgave.beskrivelse,
                journalpostId = "444",
            )
            val response = hentOppgaveResponse(beskrivelse = oppgave.beskrivelse)
            stubOppgave(expectedRequest, response)

            val clientOgAzure = createOppgaveClientWithAzure(
                baseUrl = baseUrl(),
                azureAdMock = mock<AzureAd> { on { getSystemToken(any()) } doReturn "token" },
            )
            val actual = clientOgAzure.client.opprettOppgaveMedSystembruker(
                config = oppgave,
                idempotencyKey = idempotencyKey,
            ).getOrFail()

            verify(clientOgAzure.azure).getSystemToken(any())
            verifyNoMoreInteractions(clientOgAzure.azure)

            assertOppgaveEquals(
                actual = actual,
                expected = OppgaveHttpKallResponse(
                    oppgaveId = oppgaveId,
                    oppgavetype = Oppgavetype.BEHANDLE_SAK,
                    request = expectedRequest,
                    response = response,
                    beskrivelse = oppgave.beskrivelse,
                    tilordnetRessurs = null,
                    tildeltEnhetsnr = "4815",
                ),
            )
        }
    }

    @Test
    fun `returns KunneIkkeOppretteOppgave for v2`() {
        startedWireMockServerWithCorrelationId {
            stubFor(stubMapping.willReturn(forbidden()))

            val clientOgAzure = createOppgaveClientWithAzure(baseUrl = baseUrl())

            clientOgAzure.client.opprettOppgave(
                config = søknadOppgave(),
                representertEnhetsnr = "4815",
                idempotencyKey = idempotencyKey,
            ) shouldBe KunneIkkeOppretteOppgave.left()
        }
    }

    @Test
    fun `bygger deterministisk uri for include-query`() {
        createOppgaveV2Uri(
            baseUrl = "https://oppgave.test",
            include = listOf("zeta", OppgaveV2Includes.KOMMENTARER, "alpha", "zeta"),
        ).toString() shouldBe "https://oppgave.test/api/v2/oppgaver?include=alpha&include=kommentarer&include=zeta"
    }

    @Test
    fun `url-enkoder include-verdier i uri`() {
        createOppgaveV2Uri(
            baseUrl = "https://oppgave.test",
            include = listOf("kommentar & mer", "pluss+tegn"),
        ).toString() shouldBe "https://oppgave.test/api/v2/oppgaver?include=kommentar+%26+mer&include=pluss%2Btegn"
    }

    private fun søknadOppgave(tilordnetRessurs: String? = null): OppgaveV2Config {
        return OppgaveV2Config(
            beskrivelse = "Søknad om supplerende stønad",
            kategorisering = OppgaveV2Config.Kategorisering(
                tema = OppgaveV2Config.Kode("SUP"),
                oppgavetype = OppgaveV2Config.Kode("BEH_SAK"),
                behandlingstema = OppgaveV2Config.Kode("ab0432"),
                behandlingstype = OppgaveV2Config.Kode("ae0034"),
            ),
            bruker = OppgaveV2Config.Bruker(
                ident = fnr.toString(),
                type = OppgaveV2Config.Bruker.Type.PERSON,
            ),
            aktivDato = LocalDate.of(2021, 1, 1),
            fristDato = LocalDate.of(2021, 1, 31),
            fordeling = tilordnetRessurs?.let {
                OppgaveV2Config.Fordeling(
                    enhet = OppgaveV2Config.Fordeling.Enhet("4815"),
                    medarbeider = OppgaveV2Config.Fordeling.Medarbeider(it),
                )
            },
            arkivreferanse = OppgaveV2Config.Arkivreferanse("444"),
            tilknyttetApplikasjon = "SUPSTONAD",
        )
    }

    private fun assertOppgaveEquals(actual: OppgaveHttpKallResponse, expected: OppgaveHttpKallResponse) {
        actual.oppgaveId shouldBe expected.oppgaveId
        actual.oppgavetype shouldBe expected.oppgavetype
        actual.beskrivelse shouldBe expected.beskrivelse
        actual.response shouldBe expected.response
        actual.tilordnetRessurs shouldBe expected.tilordnetRessurs
        actual.tildeltEnhetsnr shouldBe expected.tildeltEnhetsnr
        JSONAssert.assertEquals(expected.request, actual.request, true)
    }

    private fun createOppgaveRequest(
        beskrivelse: String,
        tilordnetRessurs: String? = null,
        journalpostId: String? = null,
        representertEnhetsnr: String? = null,
    ): String {
        return serialize(
            OppgaveV2Request(
                beskrivelse = beskrivelse,
                kategorisering = OppgaveV2Request.Kategorisering(
                    tema = OppgaveV2Request.Kode("SUP"),
                    oppgavetype = OppgaveV2Request.Kode("BEH_SAK"),
                    behandlingstema = OppgaveV2Request.Kode("ab0432"),
                    behandlingstype = OppgaveV2Request.Kode("ae0034"),
                ),
                bruker = OppgaveV2Request.Bruker(
                    ident = fnr.toString(),
                    type = OppgaveV2Request.Bruker.Type.PERSON,
                ),
                aktivDato = LocalDate.of(2021, 1, 1),
                fristDato = LocalDate.of(2021, 1, 31),
                fordeling = tilordnetRessurs?.let {
                    OppgaveV2Request.Fordeling(
                        enhet = OppgaveV2Request.Fordeling.Enhet("4815"),
                        medarbeider = OppgaveV2Request.Fordeling.Medarbeider(it),
                    )
                },
                arkivreferanse = journalpostId?.let { OppgaveV2Request.Arkivreferanse(it) },
                tilknyttetApplikasjon = "SUPSTONAD",
                meta = representertEnhetsnr?.let {
                    OppgaveV2Request.Meta(
                        representerer = OppgaveV2Request.Meta.Representerer(
                            enhet = OppgaveV2Request.Meta.Representerer.Enhet(it),
                        ),
                    )
                },
            ),
        )
    }

    private fun hentOppgaveResponse(
        beskrivelse: String,
        tilordnetRessurs: String? = null,
    ): String {
        return serialize(
            OppgaveV2Response(
                id = 123,
                beskrivelse = beskrivelse,
                status = "AAPEN",
                fordeling = OppgaveV2Response.Fordeling(
                    enhet = OppgaveV2Response.Fordeling.Enhet(
                        nr = "4815",
                        navn = "Nav arbeid og ytelser",
                    ),
                    mappe = null,
                    medarbeider = tilordnetRessurs?.let {
                        OppgaveV2Response.Fordeling.Medarbeider(
                            ident = it,
                            navn = "Saks Behandler",
                        )
                    },
                ),
                kategorisering = OppgaveV2Response.Kategorisering(
                    tema = OppgaveV2Response.Kategorisering.Kodeverkverdi(
                        kode = "SUP",
                        term = "Supplerende stønad",
                    ),
                    oppgavetype = OppgaveV2Response.Kategorisering.Kodeverkverdi(
                        kode = "BEH_SAK",
                        term = "Behandle sak",
                    ),
                    behandlingstema = OppgaveV2Response.Kategorisering.Kodeverkverdi(
                        kode = "ab0432",
                        term = "Alder",
                    ),
                    behandlingstype = OppgaveV2Response.Kategorisering.Kodeverkverdi(
                        kode = "ae0034",
                        term = "Søknad",
                    ),
                ),
            ),
        )
    }

    private val stubMapping = WireMock.post(urlPathEqualTo(OPPGAVE_V2_PATH))
        .withHeader("Authorization", equalTo(bearerToken))
        .withHeader("X-Correlation-ID", equalTo("correlationId"))
        .withHeader("Accept", equalTo("application/json"))
        .withHeader("Content-Type", equalTo("application/json"))
}
