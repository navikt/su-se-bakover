package no.nav.su.se.bakover.web.routes.notat

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.notat.Notat
import no.nav.su.se.bakover.domain.notat.NotatHandling
import no.nav.su.se.bakover.domain.notat.NotatHendelse
import no.nav.su.se.bakover.domain.notat.NotatService
import no.nav.su.se.bakover.domain.notat.ReferanseType
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.KunneIkkeHentePerson
import person.domain.PersonService
import java.util.UUID

internal class NotatRouteTest {
    private val sakId = UUID.randomUUID()
    private val notatId = UUID.randomUUID()
    private val referanseId = UUID.randomUUID()
    private val referanseType = ReferanseType.SØKNADSBEHANDLING
    private val fnr = Fnr.generer()
    private val sakstype = Sakstype.UFØRE
    private val notatTekst = "Notat for testsaken"
    private val saksbehandler = NavIdentBruker.Saksbehandler("Z123456")
    private val tidspunkt = Tidspunkt.now(fixedClock)
    private val notat = Notat(
        id = notatId,
        sakId = sakId,
        referanseId = referanseId,
        referanseType = referanseType,
        notat = notatTekst,
        opprettet = tidspunkt,
        endret = tidspunkt,
        hendelser = listOf(
            NotatHendelse(
                navIdent = saksbehandler,
                tidspunkt = tidspunkt,
                handling = NotatHandling.OPPRETTET,
            ),
        ),
    )
    private val forventetResponse = NotatTestResponse(
        id = notatId,
        sakId = sakId,
        notat = notatTekst,
        hendelser = listOf(
            NotatHendelseTestResponse(
                navIdent = saksbehandler.navIdent,
                handling = NotatHandling.OPPRETTET,
            ),
        ),
    )

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `lesing av notater krever persontilgang gjennom rutebindingen`(harTilgang: Boolean) {
        val notater = listOf(notat)
        val notatService = mock<NotatService> {
            on { hentNotaterForSak(sakId) } doReturn notater.right()
        }
        val personService = personService(harTilgang)

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    defaultSakstype = sakstype,
                    defaultFnr = listOf(fnr),
                    services = TestServicesBuilder.services(person = personService).copy(notatService = notatService),
                )
            }
            defaultRequest(HttpMethod.Get, "$NOTAT_PATH/$sakId", listOf(Brukerrolle.Saksbehandler)).apply {
                if (harTilgang) {
                    status shouldBe HttpStatusCode.OK
                    deserialize<List<NotatTestResponse>>(bodyAsText()) shouldBe listOf(forventetResponse)
                } else {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        verify(personService).sjekkTilgangTilPerson(fnr, sakstype)
        verifyNoMoreInteractions(personService)
        if (harTilgang) {
            verify(notatService).hentNotaterForSak(sakId)
        } else {
            verifyNoInteractions(notatService)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `oppdatering av notat krever persontilgang gjennom rutebindingen`(harTilgang: Boolean) {
        val notatService = mock<NotatService> {
            on {
                oppdaterNotatSaksbehandler(eq(sakId), eq(notatId), eq(notatTekst), any(), eq(fixedClock))
            } doReturn notat.right()
        }
        val personService = personService(harTilgang)
        val request = OppdaterNotatRequest(notat = notatTekst)

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    clock = fixedClock,
                    defaultSakstype = sakstype,
                    defaultFnr = listOf(fnr),
                    services = TestServicesBuilder.services(person = personService).copy(notatService = notatService),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "$NOTAT_PATH/$sakId/$notatId/saksbehandler",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.apply {
                if (harTilgang) {
                    status shouldBe HttpStatusCode.OK
                    deserialize<NotatTestResponse>(bodyAsText()) shouldBe forventetResponse
                } else {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        verify(personService).sjekkTilgangTilPerson(fnr, sakstype)
        verifyNoMoreInteractions(personService)
        if (harTilgang) {
            verify(notatService).oppdaterNotatSaksbehandler(eq(sakId), eq(notatId), eq(notatTekst), any(), eq(fixedClock))
        } else {
            verifyNoInteractions(notatService)
        }
    }

    @Test
    fun `øvrige notatoperasjoner avviser manglende persontilgang før tjenestekall`() {
        val vedleggId = UUID.randomUUID()
        val opprettRequest = OpprettNotatRequest(referanseId = referanseId, referanseType = referanseType)
        val oppdaterRequest = OppdaterNotatRequest(notat = notatTekst)
        val filnavn = "notat.pdf"
        val notatService = mock<NotatService>()
        val personService = personService(harTilgang = false)
        val roller = listOf(Brukerrolle.Saksbehandler, Brukerrolle.Attestant)

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    defaultSakstype = sakstype,
                    defaultFnr = listOf(fnr),
                    services = TestServicesBuilder.services(person = personService).copy(notatService = notatService),
                )
            }
            listOf(
                "$NOTAT_PATH/$sakId/$notatId",
                "$NOTAT_PATH/$sakId/$referanseId/hentNotat?referanseType=${referanseType.name}",
            ).forEach { uri ->
                withClue(uri) {
                    defaultRequest(HttpMethod.Get, uri, roller).status shouldBe HttpStatusCode.Forbidden
                }
            }
            defaultRequest(HttpMethod.Post, "$NOTAT_PATH/$sakId", roller) {
                setBody(serialize(opprettRequest))
            }.status shouldBe HttpStatusCode.Forbidden
            defaultRequest(HttpMethod.Post, "$NOTAT_PATH/$sakId/$notatId/attestant", roller) {
                setBody(serialize(oppdaterRequest))
            }.status shouldBe HttpStatusCode.Forbidden
            defaultRequest(HttpMethod.Post, "$NOTAT_PATH/$sakId/$notatId/vedlegg", roller) {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "fil",
                                "Testvedlegg".toByteArray(),
                                Headers.build {
                                    append(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
                                    append(HttpHeaders.ContentDisposition, "filename=$filnavn")
                                },
                            )
                        },
                    ),
                )
            }.status shouldBe HttpStatusCode.Forbidden
            defaultRequest(
                HttpMethod.Delete,
                "$NOTAT_PATH/$sakId/$notatId/vedlegg/$vedleggId",
                roller,
            ).status shouldBe HttpStatusCode.Forbidden
        }

        verify(personService, times(6)).sjekkTilgangTilPerson(fnr, sakstype)
        verifyNoMoreInteractions(personService)
        verifyNoInteractions(notatService)
    }

    private fun personService(harTilgang: Boolean): PersonService = mock {
        on { sjekkTilgangTilPerson(fnr, sakstype) } doReturn if (harTilgang) {
            Unit.right()
        } else {
            KunneIkkeHentePerson.IkkeTilgangTilPerson.left()
        }
    }

    private data class OpprettNotatRequest(
        val referanseId: UUID,
        val referanseType: ReferanseType,
    )

    private data class OppdaterNotatRequest(
        val notat: String,
    )

    private data class NotatTestResponse(
        val id: UUID,
        val sakId: UUID,
        val notat: String,
        val hendelser: List<NotatHendelseTestResponse>,
    )

    private data class NotatHendelseTestResponse(
        val navIdent: String,
        val handling: NotatHandling,
    )
}
