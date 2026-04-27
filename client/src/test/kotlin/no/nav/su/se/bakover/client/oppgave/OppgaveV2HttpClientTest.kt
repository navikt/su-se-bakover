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
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.jsonNode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Config
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

    private fun WireMockServer.stubOppgave(expectedRequest: OppgaveV2Request, response: String, status: Int = 201) {
        stubFor(
            stubMapping
                .withHeader("Idempotency-Key", equalTo(idempotencyKey.toString()))
                .withRequestBody(equalToJson(serialize(expectedRequest)))
                .willReturn(aResponse().withBody(response).withStatus(status)),
        )
    }

    @Test
    fun `oppretter v2-oppgave for saksbehandler med meta`() {
        startedWireMockServerWithCorrelationId {
            val oppgave = søknadOppgave(
                tilordnetRessurs = "Z12345",
                mappeId = 123,
                kommentar = "Valgfri kommentar",
                nokkelord = setOf("foo", "bar"),
                saksnr = "1234",
                prioritet = OppgaveV2Config.Prioritet.NORMAL,
            )
            val expectedRequest = createOppgaveRequest(
                beskrivelse = oppgave.beskrivelse,
                tilordnetRessurs = "Z12345",
                mappeId = 123,
                journalpostId = "444",
                saksnr = "1234",
                representertEnhetsnr = "4815",
                kommentar = "Valgfri kommentar",
                nokkelord = setOf("foo", "bar"),
                prioritet = OppgaveV2Request.Prioritet.NORMAL,
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

            val requestJson = jsonNode(actual.request)
            val actualRequest = deserialize<OppgaveV2Request>(actual.request)
            requestJson.has("nokkelord") shouldBe false
            actualRequest.nøkkelord shouldBe setOf("foo", "bar")
            actualRequest.aktivDato shouldBe LocalDate.of(2021, 1, 1)

            verify(clientOgAzure.azure).onBehalfOfToken(
                originalToken = argThat { it shouldBe bearerToken },
                otherAppId = argThat { it shouldBe clientId },
            )
            verifyNoMoreInteractions(clientOgAzure.azure)

            assertOppgaveEquals(
                actual = actual,
                expectedRequest = expectedRequest,
                expectedResponse = response,
                expectedBeskrivelse = oppgave.beskrivelse,
                expectedTilordnetRessurs = "Z12345",
                expectedTildeltEnhetsnr = "4815",
            )
        }
    }

    @Test
    fun `oppretter v2-oppgave med systembruker`() {
        startedWireMockServerWithCorrelationId {
            val oppgave = søknadOppgave()
            val expectedRequest = createOppgaveRequest(
                beskrivelse = oppgave.beskrivelse,
                journalpostId = "444",
            )
            val response = hentOppgaveResponse(beskrivelse = oppgave.beskrivelse)

            stubFor(
                WireMock.post(urlPathEqualTo(OPPGAVE_V2_PATH))
                    .withHeader("Authorization", equalTo(bearerToken))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader("Content-Type", equalTo("application/json"))
                    .withHeader("Idempotency-Key", equalTo(idempotencyKey.toString()))
                    .withRequestBody(equalToJson(serialize(expectedRequest)))
                    .willReturn(aResponse().withBody(response).withStatus(200)),
            )

            val clientOgAzure = createOppgaveClientWithAzure(
                baseUrl = baseUrl(),
                azureAdMock = mock<AzureAd> { on { getSystemToken(any()) } doReturn "token" },
            )
            val actual = clientOgAzure.client.opprettOppgaveMedSystembruker(
                config = oppgave,
                idempotencyKey = idempotencyKey,
            ).getOrFail()

            deserialize<OppgaveV2Request>(actual.request).nøkkelord shouldBe emptySet()

            verify(clientOgAzure.azure).getSystemToken(any())
            verifyNoMoreInteractions(clientOgAzure.azure)

            assertOppgaveEquals(
                actual = actual,
                expectedRequest = expectedRequest,
                expectedResponse = response,
                expectedBeskrivelse = oppgave.beskrivelse,
                expectedTilordnetRessurs = null,
                expectedTildeltEnhetsnr = "4815",
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
                expectedRequest = expectedRequest,
                expectedResponse = response,
                expectedBeskrivelse = oppgave.beskrivelse,
                expectedTilordnetRessurs = null,
                expectedTildeltEnhetsnr = "4815",
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
    fun `bygger uri uten query-parametre`() {
        createOppgaveV2Uri(
            baseUrl = "https://oppgave.test",
        ).toString() shouldBe "https://oppgave.test/api/v2/oppgaver"
    }

    private fun søknadOppgave(
        tilordnetRessurs: String? = null,
        mappeId: Long? = null,
        kommentar: String? = null,
        nokkelord: Set<String> = emptySet(),
        saksnr: String? = null,
        prioritet: OppgaveV2Config.Prioritet? = null,
    ): OppgaveV2Config {
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
            prioritet = prioritet,
            fordeling = tilordnetRessurs?.let {
                OppgaveV2Config.Fordeling(
                    enhet = OppgaveV2Config.Fordeling.Enhet("4815"),
                    mappe = mappeId?.let { OppgaveV2Config.Fordeling.Mappe(it) },
                    medarbeider = OppgaveV2Config.Fordeling.Medarbeider(it),
                )
            },
            nokkelord = nokkelord,
            arkivreferanse = OppgaveV2Config.Arkivreferanse(
                saksnr = saksnr,
                journalpostId = "444",
            ),
            tilknyttetSystem = "SUPSTONAD",
            meta = kommentar?.let { OppgaveV2Config.Meta(kommentar = it) },
        )
    }

    private fun assertOppgaveEquals(
        actual: OppgaveHttpKallResponse,
        expectedRequest: OppgaveV2Request,
        expectedResponse: String,
        expectedBeskrivelse: String,
        expectedTilordnetRessurs: String?,
        expectedTildeltEnhetsnr: String?,
    ) {
        actual.oppgaveId shouldBe oppgaveId
        actual.oppgavetype shouldBe Oppgavetype.BEHANDLE_SAK
        actual.beskrivelse shouldBe expectedBeskrivelse
        actual.response shouldBe expectedResponse
        actual.tilordnetRessurs shouldBe expectedTilordnetRessurs
        actual.tildeltEnhetsnr shouldBe expectedTildeltEnhetsnr
        deserialize<OppgaveV2Request>(actual.request) shouldBe expectedRequest
    }

    private fun createOppgaveRequest(
        beskrivelse: String,
        tilordnetRessurs: String? = null,
        mappeId: Long? = null,
        journalpostId: String? = null,
        saksnr: String? = null,
        representertEnhetsnr: String? = null,
        kommentar: String? = null,
        nokkelord: Set<String> = emptySet(),
        prioritet: OppgaveV2Request.Prioritet? = null,
    ): OppgaveV2Request {
        return OppgaveV2Request(
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
            prioritet = prioritet,
            fordeling = tilordnetRessurs?.let {
                OppgaveV2Request.Fordeling(
                    enhet = OppgaveV2Request.Fordeling.Enhet("4815"),
                    mappe = mappeId?.let { id -> OppgaveV2Request.Fordeling.Mappe(id) },
                    medarbeider = OppgaveV2Request.Fordeling.Medarbeider(it),
                )
            },
            nøkkelord = nokkelord,
            arkivreferanse = if (journalpostId != null || saksnr != null) {
                OppgaveV2Request.Arkivreferanse(
                    saksnr = saksnr,
                    journalpostId = journalpostId,
                )
            } else {
                null
            },
            tilknyttetSystem = "SUPSTONAD",
            meta = if (representertEnhetsnr != null || kommentar != null) {
                OppgaveV2Request.Meta(
                    representerer = representertEnhetsnr?.let {
                        OppgaveV2Request.Meta.Representerer(
                            enhet = OppgaveV2Request.Meta.Representerer.Enhet(it),
                        )
                    },
                    kommentar = kommentar,
                )
            } else {
                null
            },
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
        .withHeader("Accept", equalTo("application/json"))
        .withHeader("Content-Type", equalTo("application/json"))
}
