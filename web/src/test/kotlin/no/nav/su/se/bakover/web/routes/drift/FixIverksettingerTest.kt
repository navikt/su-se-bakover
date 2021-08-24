package no.nav.su.se.bakover.web.routes.drift

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class FixIverksettingerTest {

    private val services = TestServicesBuilder.services()

    @Test
    fun `Kun Drift har tilgang til fix-iverksettinger-endepunktet`() {
        Brukerrolle.values().filterNot { it == Brukerrolle.Drift }.forEach {
            withTestApplication({
                testSusebakover(services = services)
            }) {
                defaultRequest(
                    HttpMethod.Patch,
                    "$DRIFT_PATH/iverksettinger/fix",
                    listOf(it)
                ) {
                }.apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `fix-iverksettinger-endepunktet gir tomt resultat`() {
        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService> {
            on { opprettManglendeJournalposterOgBrevbestillingerOgLukkOppgaver() } doReturn FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
                journalpostresultat = emptyList(),
                brevbestillingsresultat = emptyList(),
            )
        }
        withTestApplication({
            testSusebakover(services = services.copy(ferdigstillVedtak = ferdigstillVedtakServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Patch,
                "$DRIFT_PATH/iverksettinger/fix",
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
                            "brevbestillinger":{
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
    fun `fix-iverksettinger-endepunktet med journalposteringer og bestilling av brev`() {
        val sakId = UUID.fromString("e8c3325c-4c4e-436c-90ad-7ac72f963a8c")
        val journalført = FerdigstillVedtakService.OpprettetJournalpostForIverksetting(
            behandlingId = UUID.fromString("51c51049-6c55-40d6-8013-b99505a0ef14"),
            sakId = sakId,
            journalpostId = JournalpostId("1"),
        )
        val bestiltBrev = FerdigstillVedtakService.BestiltBrev(
            behandlingId = UUID.fromString("e38df38a-c3fc-48d1-adca-0a9264024a2e"),
            sakId = sakId,
            journalpostId = JournalpostId("2"),
            brevbestillingId = BrevbestillingId("3")
        )

        val søknadIdJournalpost = UUID.fromString("18e19f68-029d-4731-ad4a-48d902fc92a2")
        val søknadIdOppgave = UUID.fromString("22770c98-31b0-412e-9e63-9a878330386e")
        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService> {
            on { opprettManglendeJournalposterOgBrevbestillingerOgLukkOppgaver() } doReturn FerdigstillVedtakService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
                journalpostresultat = listOf(
                    journalført.right(),
                    FerdigstillVedtakService.KunneIkkeOppretteJournalpostForIverksetting(
                        sakId,
                        søknadIdJournalpost,
                        "Fant ikke Person"
                    ).left(),
                ),
                brevbestillingsresultat = listOf(
                    bestiltBrev.right(),
                    FerdigstillVedtakService.KunneIkkeBestilleBrev(
                        sakId,
                        søknadIdOppgave,
                        JournalpostId("1"),
                        "Kunne ikke bestille brev"
                    ).left(),
                ),
            )
        }
        withTestApplication({
            testSusebakover(services = services.copy(ferdigstillVedtak = ferdigstillVedtakServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Patch,
                "$DRIFT_PATH/iverksettinger/fix",
                listOf(Brukerrolle.Drift)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                //language=JSON
                JSONAssert.assertEquals(
                    """
                        {
                           "journalposteringer":{
                              "ok":[
                                 {
                                    "sakId":"e8c3325c-4c4e-436c-90ad-7ac72f963a8c",
                                    "behandlingId":"51c51049-6c55-40d6-8013-b99505a0ef14",
                                    "journalpostId":"1"
                                 }
                              ],
                              "feilet":[
                                 {
                                    "sakId":"e8c3325c-4c4e-436c-90ad-7ac72f963a8c",
                                    "behandlingId":"18e19f68-029d-4731-ad4a-48d902fc92a2",
                                    "grunn": "Fant ikke Person"
                                 }
                              ]
                           },
                           "brevbestillinger":{
                              "ok":[
                                 {
                                    "sakId":"e8c3325c-4c4e-436c-90ad-7ac72f963a8c",
                                    "behandlingId":"e38df38a-c3fc-48d1-adca-0a9264024a2e",
                                    "brevbestillingId":"3"
                                 }
                              ],
                              "feilet":[
                                 {
                                    "sakId":"e8c3325c-4c4e-436c-90ad-7ac72f963a8c",
                                    "behandlingId":"22770c98-31b0-412e-9e63-9a878330386e",
                                    "grunn": "Kunne ikke bestille brev"
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
