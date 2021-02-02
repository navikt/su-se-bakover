package no.nav.su.se.bakover.web.routes.drift

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteJournalpost
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.service.søknad.OpprettManglendeJournalpostOgOppgaveResultat
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class FixSøknaderTest {

    private val services = TestServicesBuilder.services()

    @Test
    fun `Kun Drift har tilgang til fix-søknader-endepunktet`() {
        Brukerrolle.values().filterNot { it == Brukerrolle.Drift }.forEach {
            withTestApplication({
                testSusebakover(services = services)
            }) {
                defaultRequest(
                    HttpMethod.Patch,
                    "$DRIFT_PATH/søknader/fix",
                    listOf(it)
                ) {
                }.apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `fix-søknader-endepunktet gir tomt resultat`() {
        val søknadServiceMock = mock<SøknadService> {
            on { opprettManglendeJournalpostOgOppgave() } doReturn OpprettManglendeJournalpostOgOppgaveResultat(
                journalpostResultat = emptyList(),
                oppgaveResultat = emptyList()
            )
        }
        withTestApplication({
            testSusebakover(services = services.copy(søknad = søknadServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Patch,
                "$DRIFT_PATH/søknader/fix",
                listOf(Brukerrolle.Drift)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
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
                    response.content!!,
                    true
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
            søknadInnhold = SøknadInnholdTestdataBuilder.build()
        )
        val journalførtSøknadMedOppgave = Søknad.Journalført.MedOppgave(
            id = UUID.fromString("e38df38a-c3fc-48d1-adca-0a9264024a2e"),
            sakId = sakId,
            journalpostId = JournalpostId("2"),
            opprettet = Tidspunkt.EPOCH,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = OppgaveId("2")
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
                    KunneIkkeOppretteOppgave(sakId, søknadIdOppgave, JournalpostId("1"), "Kunne ikke opprette oppgave").left(),
                )
            )
        }
        withTestApplication({
            testSusebakover(services = services.copy(søknad = søknadServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Patch,
                "$DRIFT_PATH/søknader/fix",
                listOf(Brukerrolle.Drift)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
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
                    response.content!!,
                    true
                )
            }
        }
    }
}
