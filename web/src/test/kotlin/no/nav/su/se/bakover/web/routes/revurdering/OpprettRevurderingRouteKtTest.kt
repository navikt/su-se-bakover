package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.opprett.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert

internal class OpprettRevurderingRouteKtTest {
    val periode = år(2021)

    //language=JSON
    private val validBody = """
        {
         "fraOgMed": "${periode.fraOgMed}",
         "tilOgMed": "${periode.tilOgMed}",
         "årsak": "ANDRE_KILDER",
         "begrunnelse": "begrunnelse",
         "informasjonSomRevurderes" : ["Uførhet"]
        }
    """.trimIndent()

    @Test
    fun `uautoriserte kan ikke opprette revurdering`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger",
                listOf(Brukerrolle.Veileder),
            ) {
                setBody(validBody)
            }.apply {
                status shouldBe HttpStatusCode.Forbidden
                JSONAssert.assertEquals(
                    """
                    {
                        "message":"Bruker mangler en av de tillatte rollene: [Saksbehandler]",
                        "code":"mangler_rolle"
                    }
                    """.trimIndent(),
                    bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `kan opprette revurdering`() {
        val (_, opprettetRevurdering) = opprettetRevurdering(
            revurderingsperiode = periode,
        )
        val revurderingServiceMock = mock<RevurderingService> {
            on { opprettRevurdering(any()) } doReturn opprettetRevurdering.right()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "fraOgMed": "${periode.fraOgMed}",
                        "tilOgMed": "${periode.tilOgMed}",
                        "årsak":"DØDSFALL",
                        "begrunnelse":"begrunnelse",
                        "informasjonSomRevurderes": ["Uførhet"]
                    }
                    """.trimMargin(),
                )
            }.apply {
                status shouldBe HttpStatusCode.Created
                val actualResponse = deserialize<OpprettetRevurderingJson>(bodyAsText())
                actualResponse.id shouldBe opprettetRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.OPPRETTET
            }
        }
    }

    @Test
    fun `fant ingen vedtak som kan revurderes for angitt periode`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.VedtakInnenforValgtPeriodeKanIkkeRevurderes(Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.FantIngenVedtakSomKanRevurderes),
            expectedStatusCode = HttpStatusCode.NotFound,
            expectedJsonResponse = """
                {
                    "message":"Fant ingen vedtak som kan revurderes for angitt periode",
                    "code":"ingenting_å_revurdere_i_perioden"
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `hele revurderingsperioden inneholder ikke vedtak`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.VedtakInnenforValgtPeriodeKanIkkeRevurderes(Sak.GjeldendeVedtaksdataErUgyldigForRevurdering.HeleRevurderingsperiodenInneholderIkkeVedtak),
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Sak mangler vedtak for en eller flere måneder i valgt revurderingsperiode!",
                    "code":"vedtak_mangler_i_en_eller_flere_måneder_av_revurderingsperiode"
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `fant ikke aktør id`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.FantIkkeAktørId(KunneIkkeHentePerson.FantIkkePerson),
            expectedStatusCode = HttpStatusCode.NotFound,
            expectedJsonResponse = """
                {
                    "message":"Fant ikke person",
                    "code":"fant_ikke_person"
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `kunne ikke opprette oppgave`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave(OppgaveFeil.KunneIkkeOppretteOppgave),
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Kunne ikke opprette oppgave",
                    "code":"kunne_ikke_opprette_oppgave"
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `må velge minst en ting å revurdere`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes,
            expectedStatusCode = HttpStatusCode.BadRequest,
            expectedJsonResponse = """
                {
                    "message":"Må velge minst en ting som skal revurderes",
                    "code":"må_velge_informasjon_som_revurderes"
                }
            """.trimIndent(),
        )
    }

    private fun shouldMapErrorCorrectly(
        error: KunneIkkeOppretteRevurdering,
        expectedStatusCode: HttpStatusCode,
        expectedJsonResponse: String,
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { opprettRevurdering(any()) } doReturn error.left()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
                status shouldBe expectedStatusCode
                JSONAssert.assertEquals(
                    expectedJsonResponse,
                    bodyAsText(),
                    true,
                )
            }
        }
    }
}
