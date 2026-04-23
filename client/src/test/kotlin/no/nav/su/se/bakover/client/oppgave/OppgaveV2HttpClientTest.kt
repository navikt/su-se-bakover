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
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstema
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveV2Include
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.fixedClock
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

    private val saksbehandler = Saksbehandler("Z12345")
    private val journalpostId = JournalpostId("444")
    private val saksnummer = Saksnummer(12345)
    private val clientId = "oppgaveClientId"
    private val bearerToken = "Bearer token"
    private val idempotencyKey = UUID.fromString("0f4cbb5c-9c59-4383-8a62-3c827465d174")

    private fun Sakstype.toBehandlingstema(): Behandlingstema =
        when (this) {
            Sakstype.ALDER -> Behandlingstema.SU_ALDER
            Sakstype.UFØRE -> Behandlingstema.SU_UFØRE_FLYKTNING
        }

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
            clock = fixedClock,
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
        val sakstype = Sakstype.ALDER
        startedWireMockServerWithCorrelationId {
            val oppgave = OppgaveConfig.Søknad(
                sakstype = sakstype,
                journalpostId = journalpostId,
                saksnummer = saksnummer,
                fnr = fnr,
                tilordnetRessurs = saksbehandler,
                clock = fixedClock,
            )
            val expectedRequest = createOppgaveRequest(
                beskrivelse = oppgave.beskrivelse,
                behandlingstema = sakstype.toBehandlingstema(),
                journalpostId = journalpostId,
                tilordnetRessurs = saksbehandler,
                inkluderMeta = true,
            )
            val response = hentOppgaveResponse(
                beskrivelse = oppgave.beskrivelse,
                tilordnetRessurs = saksbehandler,
            )
            stubOppgave(expectedRequest, response)

            val clientOgAzure = createOppgaveClientWithAzure(baseUrl = baseUrl())
            val actual = clientOgAzure.client.opprettOppgave(
                config = oppgave,
                idempotencyKey = idempotencyKey,
            ).getOrFail()

            val expected = OppgaveHttpKallResponse(
                oppgaveId = oppgaveId,
                oppgavetype = Oppgavetype.BEHANDLE_SAK,
                request = expectedRequest,
                response = response,
                beskrivelse = oppgave.beskrivelse,
                tilordnetRessurs = saksbehandler.navIdent,
                tildeltEnhetsnr = "4815",
            )

            verify(clientOgAzure.azure).onBehalfOfToken(
                originalToken = argThat { it shouldBe bearerToken },
                otherAppId = argThat { it shouldBe clientId },
            )
            verifyNoMoreInteractions(clientOgAzure.azure)

            assertOppgaveEquals(actual, expected)
        }
    }

    @Test
    fun `oppretter v2-oppgave med systembruker og include-query`() {
        val sakstype = Sakstype.ALDER
        startedWireMockServerWithCorrelationId {
            val oppgave = OppgaveConfig.Søknad(
                sakstype = sakstype,
                journalpostId = journalpostId,
                saksnummer = saksnummer,
                fnr = fnr,
                tilordnetRessurs = null,
                clock = fixedClock,
            )
            val expectedRequest = createOppgaveRequest(
                beskrivelse = oppgave.beskrivelse,
                behandlingstema = sakstype.toBehandlingstema(),
                journalpostId = journalpostId,
                inkluderMeta = false,
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
                include = setOf(OppgaveV2Include.KOMMENTARER),
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
                config = OppgaveConfig.Søknad(
                    sakstype = Sakstype.UFØRE,
                    journalpostId = journalpostId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    clock = fixedClock,
                    tilordnetRessurs = null,
                ),
                idempotencyKey = idempotencyKey,
            ) shouldBe KunneIkkeOppretteOppgave.left()
        }
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
        behandlingstema: Behandlingstema,
        journalpostId: JournalpostId?,
        tilordnetRessurs: Saksbehandler? = null,
        inkluderMeta: Boolean,
    ): String {
        return serialize(
            OppgaveV2Request(
                beskrivelse = beskrivelse,
                kategorisering = OppgaveV2Request.Kategorisering(
                    tema = OppgaveV2Request.Kode("SUP"),
                    oppgavetype = OppgaveV2Request.Kode("BEH_SAK"),
                    behandlingstema = OppgaveV2Request.Kode(behandlingstema.toString()),
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
                        medarbeider = OppgaveV2Request.Fordeling.Medarbeider(it.navIdent),
                    )
                },
                arkivreferanse = journalpostId?.let { OppgaveV2Request.Arkivreferanse(it.toString()) },
                tilknyttetApplikasjon = "SUPSTONAD",
                meta = if (inkluderMeta) {
                    OppgaveV2Request.Meta(
                        representerer = OppgaveV2Request.Meta.Representerer(
                            enhet = OppgaveV2Request.Meta.Representerer.Enhet("4815"),
                        ),
                    )
                } else {
                    null
                },
            ),
        )
    }

    private fun hentOppgaveResponse(
        beskrivelse: String,
        tilordnetRessurs: Saksbehandler? = null,
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
                            ident = it.navIdent,
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
