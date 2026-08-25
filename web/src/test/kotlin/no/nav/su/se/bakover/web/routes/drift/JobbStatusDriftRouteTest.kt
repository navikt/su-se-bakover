package no.nav.su.se.bakover.web.routes.drift

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.job.JobbKjøring
import no.nav.su.se.bakover.common.domain.job.JobbKjøringRepo
import no.nav.su.se.bakover.common.domain.job.JobbKjøringStatus
import no.nav.su.se.bakover.common.domain.job.JobbNavn
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal class JobbStatusDriftRouteTest {

    @Test
    fun `kun Drift-rolle har tilgang`() {
        Brukerrolle.entries.filterNot { it == Brukerrolle.Drift }.forEach { rolle ->
            testApplication {
                application {
                    testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
                }
                defaultRequest(
                    method = HttpMethod.Get,
                    uri = "$DRIFT_PATH/jobber/status",
                    roller = listOf(rolle),
                ) {
                }.apply {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `returnerer tom liste når ingen jobber har kjørt`() {
        val jobbKjøringRepo = mock<JobbKjøringRepo> {
            on { hentSistePerJobb() } doReturn emptyList()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    databaseRepos = no.nav.su.se.bakover.web.MockDatabaseBuilder.build().copy(
                        jobbKjøringRepo = jobbKjøringRepo,
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Get,
                "$DRIFT_PATH/jobber/status",
                listOf(Brukerrolle.Drift),
            ) {
            }.apply {
                status shouldBe HttpStatusCode.OK
                JSONAssert.assertEquals("[]", bodyAsText(), true)
            }
        }
    }

    @Test
    fun `returnerer jobbstatus med korrekte felter`() {
        val id = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val startet = Instant.parse("2026-08-25T10:00:00Z")
        val ferdig = Instant.parse("2026-08-25T10:00:02Z")

        val jobbKjøringRepo = mock<JobbKjøringRepo> {
            on { hentSistePerJobb() } doReturn listOf(
                JobbKjøring(
                    id = id,
                    jobbNavn = JobbNavn.JOURNALFØR_DOKUMENTER.visningsnavn,
                    status = JobbKjøringStatus.FULLFØRT,
                    startetTidspunkt = startet,
                    ferdigTidspunkt = ferdig,
                    feilmelding = null,
                    intervall = Duration.ofSeconds(60),
                ),
            )
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    databaseRepos = no.nav.su.se.bakover.web.MockDatabaseBuilder.build().copy(
                        jobbKjøringRepo = jobbKjøringRepo,
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Get,
                "$DRIFT_PATH/jobber/status",
                listOf(Brukerrolle.Drift),
            ) {
            }.apply {
                status shouldBe HttpStatusCode.OK
                JSONAssert.assertEquals(
                    """
                    [{
                        "id": "11111111-1111-1111-1111-111111111111",
                        "jobbNavn": "Journalfør dokumenter",
                        "beskrivelse": "Journalfører dokumenter (brev, skattedokumenter) mot Joark.",
                        "status": "FULLFØRT",
                        "startetTidspunkt": "2026-08-25T10:00:00Z",
                        "ferdigTidspunkt": "2026-08-25T10:00:02Z",
                        "feilmelding": null,
                        "intervallSekunder": 60
                    }]
                    """.trimIndent(),
                    bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `returnerer feilet jobb med feilmelding`() {
        val id = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val startet = Instant.parse("2026-08-25T12:00:00Z")
        val ferdig = Instant.parse("2026-08-25T12:00:05Z")

        val jobbKjøringRepo = mock<JobbKjøringRepo> {
            on { hentSistePerJobb() } doReturn listOf(
                JobbKjøring(
                    id = id,
                    jobbNavn = JobbNavn.TILBAKEKREVING.visningsnavn,
                    status = JobbKjøringStatus.FEILET,
                    startetTidspunkt = startet,
                    ferdigTidspunkt = ferdig,
                    feilmelding = "Connection refused",
                    intervall = Duration.ofMinutes(5),
                ),
            )
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    databaseRepos = no.nav.su.se.bakover.web.MockDatabaseBuilder.build().copy(
                        jobbKjøringRepo = jobbKjøringRepo,
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Get,
                "$DRIFT_PATH/jobber/status",
                listOf(Brukerrolle.Drift),
            ) {
            }.apply {
                status shouldBe HttpStatusCode.OK
                JSONAssert.assertEquals(
                    """
                    [{
                        "id": "22222222-2222-2222-2222-222222222222",
                        "jobbNavn": "Tilbakekreving",
                        "beskrivelse": "Prosesserer tilbakekrevingsvedtak, forhåndsvarsler og kravgrunnlag.",
                        "status": "FEILET",
                        "startetTidspunkt": "2026-08-25T12:00:00Z",
                        "ferdigTidspunkt": "2026-08-25T12:00:05Z",
                        "feilmelding": "Connection refused",
                        "intervallSekunder": 300
                    }]
                    """.trimIndent(),
                    bodyAsText(),
                    true,
                )
            }
        }
    }
}
