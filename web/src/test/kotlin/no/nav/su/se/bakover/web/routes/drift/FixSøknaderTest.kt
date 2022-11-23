package no.nav.su.se.bakover.web.routes.drift

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod.Companion.Patch
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteJournalpost
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.service.søknad.OpprettManglendeJournalpostOgOppgaveResultat
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.veileder
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class FixSøknaderTest {

    private val services = TestServicesBuilder.services()

    @Test
    fun `Kun Drift har tilgang til fix-søknader-endepunktet`() {
        Brukerrolle.values().filterNot { it == Brukerrolle.Drift }.forEach {
            testApplication {
                application {
                    testSusebakover(services = services)
                }
                defaultRequest(
                    Patch,
                    "$DRIFT_PATH/søknader/fix",
                    listOf(it),
                ).apply {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `fix-søknader-endepunktet gir tomt resultat`() {
        val søknadServiceMock = mock<SøknadService> {
            on { opprettManglendeJournalpostOgOppgave() } doReturn OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = emptyList(),
                oppgaveResultat = emptyList(),
            )
        }
        testApplication {
            application {
                testSusebakover(services = services.copy(søknad = søknadServiceMock))
            }
            defaultRequest(
                Patch,
                "$DRIFT_PATH/søknader/fix",
                listOf(Brukerrolle.Drift),
            ) {
            }.apply {
                status shouldBe HttpStatusCode.OK
                JSONAssert.assertEquals(
                    """
                        {
                            "journalposteringer":{
                                "ok":[],
                                "feilet":[]
                            },
                            "oppgaver":{
                                "ok":[],
                                "feilet":[]
                            }
                        }
                    """.trimIndent(),
                    this.bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `fix-søknader-endepunktet med journalposteringer og oppgaver`() {
        val sakId = UUID.fromString("e8c3325c-4c4e-436c-90ad-7ac72f963a8c")
        val journalførtSøknadUtenOppgave = Søknad.Journalført.UtenOppgave(
            id = UUID.fromString("51c51049-6c55-40d6-8013-b99505a0ef14"),
            sakId = sakId,
            journalpostId = JournalpostId("1"),
            opprettet = Tidspunkt.EPOCH,
            søknadInnhold = søknadinnholdUføre(),
            innsendtAv = veileder,
        )
        val journalførtSøknadMedOppgave = Søknad.Journalført.MedOppgave.IkkeLukket(
            id = UUID.fromString("e38df38a-c3fc-48d1-adca-0a9264024a2e"),
            sakId = sakId,
            journalpostId = JournalpostId("2"),
            opprettet = Tidspunkt.EPOCH,
            søknadInnhold = søknadinnholdUføre(),
            innsendtAv = veileder,
            oppgaveId = OppgaveId("2"),
        )

        val søknadIdJournalpost = UUID.fromString("18e19f68-029d-4731-ad4a-48d902fc92a2")
        val søknadIdOppgave = UUID.fromString("22770c98-31b0-412e-9e63-9a878330386e")
        val søknadServiceMock = mock<SøknadService> {
            on { opprettManglendeJournalpostOgOppgave() } doReturn OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = listOf(
                    journalførtSøknadUtenOppgave.right(),
                    KunneIkkeOppretteJournalpost(sakId, søknadIdJournalpost, "Fant ikke Person").left(),
                ),
                oppgaveResultat = listOf(
                    journalførtSøknadMedOppgave.right(),
                    KunneIkkeOppretteOppgave(
                        sakId,
                        søknadIdOppgave,
                        JournalpostId("1"),
                        "Kunne ikke opprette oppgave",
                    ).left(),
                ),
            )
        }
        testApplication {
            application {
                testSusebakover(services = services.copy(søknad = søknadServiceMock))
            }

            defaultRequest(
                Patch,
                "$DRIFT_PATH/søknader/fix",
                listOf(Brukerrolle.Drift),
            ) {
            }.apply {
                status shouldBe HttpStatusCode.OK
                //language=JSON
                JSONAssert.assertEquals(
                    """
                        {
                           "journalposteringer":{
                              "ok":[
                                 {
                                    "sakId":"e8c3325c-4c4e-436c-90ad-7ac72f963a8c",
                                    "søknadId":"51c51049-6c55-40d6-8013-b99505a0ef14",
                                    "journalpostId":"1"
                                 }
                              ],
                              "feilet":[
                                 {
                                    "sakId":"e8c3325c-4c4e-436c-90ad-7ac72f963a8c",
                                    "søknadId":"18e19f68-029d-4731-ad4a-48d902fc92a2",
                                    "grunn": "Fant ikke Person"
                                 }
                              ]
                           },
                           "oppgaver":{
                              "ok":[
                                 {
                                    "sakId":"e8c3325c-4c4e-436c-90ad-7ac72f963a8c",
                                    "søknadId":"e38df38a-c3fc-48d1-adca-0a9264024a2e",
                                    "oppgaveId":"2"
                                 }
                              ],
                              "feilet":[
                                 {
                                    "sakId":"e8c3325c-4c4e-436c-90ad-7ac72f963a8c",
                                    "søknadId":"22770c98-31b0-412e-9e63-9a878330386e",
                                    "grunn": "Kunne ikke opprette oppgave"
                                 }
                              ]
                           }
                        }
                    """.trimIndent(),
                    this.bodyAsText(),
                    true,
                )
            }
        }
    }
}
