package no.nav.su.se.bakover.web.routes.drift

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.services.fradragssjekken.FradragsjobbenService
import no.nav.su.se.bakover.web.services.fradragssjekken.FradragssjekkDriftResultat
import no.nav.su.se.bakover.web.services.fradragssjekken.FradragssjekkFradragStatistikk
import no.nav.su.se.bakover.web.services.fradragssjekken.FradragssjekkKjøringStatus
import no.nav.su.se.bakover.web.services.fradragssjekken.FradragssjekkOpprettetOppgave
import no.nav.su.se.bakover.web.services.fradragssjekken.FradragssjekkOppsummering
import no.nav.su.se.bakover.web.services.fradragssjekken.FradragssjekkSakStatus
import no.nav.su.se.bakover.web.services.fradragssjekken.FradragssjekkSakstypeStatistikk
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class FradragssjekkDriftRouteTest {

    @Test
    fun `returnerer siste resultat med oppsummering og opprettede oppgaver`() {
        val fradragsjobbenService = mock<FradragsjobbenService> {
            on { hentSisteResultatForDrift() } doReturn FradragssjekkDriftResultat(
                id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                dato = LocalDate.parse("2026-09-01"),
                dryRun = false,
                status = FradragssjekkKjøringStatus.FULLFØRT,
                opprettet = Instant.parse("2026-09-01T06:00:00Z"),
                ferdigstilt = Instant.parse("2026-09-01T06:05:00Z"),
                oppsummering = FradragssjekkOppsummering(
                    nøkkeltall = mapOf(FradragssjekkSakStatus.OPPGAVE_OPPRETTET to 1),
                    antallOppgaver = 1,
                    oppgaverPerSakstype = listOf(
                        FradragssjekkSakstypeStatistikk(
                            sakstype = Sakstype.ALDER,
                            antallOppgaver = 1,
                            oppgaverPerFradrag = listOf(
                                FradragssjekkFradragStatistikk(
                                    fradragstype = "Alderspensjon",
                                    beskrivelse = null,
                                    antallOppgaver = 1,
                                ),
                            ),
                        ),
                    ),
                ),
                opprettedeOppgaver = listOf(
                    FradragssjekkOpprettetOppgave(
                        sakId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        saksnummer = Saksnummer(2026001),
                        oppgaveId = OppgaveId("12345"),
                    ),
                ),
            )
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(fradragsjobbenService = fradragsjobbenService),
                )
            }
            defaultRequest(
                HttpMethod.Get,
                "$DRIFT_PATH/fradragssjekk/resultat",
                listOf(Brukerrolle.Drift),
            ) {}.apply {
                status shouldBe HttpStatusCode.OK
                JSONAssert.assertEquals(
                    """
                    {
                      "id": "11111111-1111-1111-1111-111111111111",
                      "dato": "2026-09-01",
                      "dryRun": false,
                      "status": "FULLFØRT",
                      "opprettet": "2026-09-01T06:00:00Z",
                      "ferdigstilt": "2026-09-01T06:05:00Z",
                      "oppsummering": {
                        "nøkkeltall": {"OPPGAVE_OPPRETTET": 1},
                        "antallOppgaver": 1,
                        "oppgaverPerSakstype": [{
                          "sakstype": "ALDER",
                          "antallOppgaver": 1,
                          "oppgaverPerFradrag": [{
                            "fradragstype": "Alderspensjon",
                            "beskrivelse": null,
                            "antallOppgaver": 1
                          }]
                        }]
                      },
                      "opprettedeOppgaver": [{
                        "sakId": "22222222-2222-2222-2222-222222222222",
                        "saksnummer": 2026001,
                        "oppgaveId": "12345"
                      }],
                      "feilmelding": null
                    }
                    """.trimIndent(),
                    bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `returnerer not found når ingen kjøring finnes`() {
        val fradragsjobbenService = mock<FradragsjobbenService> {
            on { hentSisteResultatForDrift() } doReturn null
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(fradragsjobbenService = fradragsjobbenService),
                )
            }
            defaultRequest(
                HttpMethod.Get,
                "$DRIFT_PATH/fradragssjekk/resultat",
                listOf(Brukerrolle.Drift),
            ) {}.status shouldBe HttpStatusCode.NotFound
        }
    }
}
