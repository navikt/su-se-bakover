package no.nav.su.se.bakover.web.routes.fritekst

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.fritekst.Fritekst
import no.nav.su.se.bakover.domain.fritekst.FritekstDomain
import no.nav.su.se.bakover.domain.fritekst.FritekstFeil
import no.nav.su.se.bakover.domain.fritekst.FritekstHentDomain
import no.nav.su.se.bakover.domain.fritekst.FritekstService
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.MockDatabaseBuilder
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.inputvalidation.InputFelter
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringFeilResponse
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringsfeil
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import person.domain.KunneIkkeHentePerson
import person.domain.PersonService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import java.util.UUID

internal class FritekstRouteTest {

    @Test
    fun `henting og lagring av fritekst krever persontilgang for alle friteksttyper`() {
        FritekstType.entries.forEach { type ->
            listOf(false, true).forEach { harTilgang ->
                val referanseId = UUID.randomUUID()
                val sakId = UUID.randomUUID()
                val fnr = Fnr.generer()
                val sakstype = Sakstype.UFØRE
                val friteksttekst = "fritekst for vedtak"
                val fritekst = Fritekst(referanseId = referanseId, type = type, fritekst = friteksttekst)
                val hentDomain = FritekstHentDomain(referanseId = referanseId, sakId = sakId, type = type)
                val lagreDomain = FritekstDomain(referanseId = referanseId, type = type, fritekst = friteksttekst, sakId = sakId)
                val hentRequest = FritekstRequestHent(referanseId = referanseId.toString(), sakId = sakId.toString(), type = type.name)
                val lagreRequest = FritekstRequestLagre(referanseId = referanseId.toString(), sakId = sakId.toString(), type = type.name, fritekst = friteksttekst)
                val tilgang: Either<KunneIkkeHentePerson, Unit> =
                    if (harTilgang) Unit.right() else KunneIkkeHentePerson.IkkeTilgangTilPerson.left()
                val personService = mock<PersonService> {
                    on { sjekkTilgangTilPerson(fnr, sakstype) } doReturn tilgang
                }
                val fritekstService = mock<FritekstService> {
                    on { hentFritekst(hentDomain) } doReturn fritekst.right()
                }
                val tilbakekrevingRepo = mock<TilbakekrevingsbehandlingRepo> {
                    on { tilhørerSak(sakId, TilbakekrevingsbehandlingId(referanseId)) } doReturn true
                }
                val forventetStatus = if (harTilgang) HttpStatusCode.OK else HttpStatusCode.Forbidden

                withClue("friteksttype=$type, harTilgang=$harTilgang") {
                    testApplication {
                        application {
                            testSusebakoverWithMockedDb(
                                databaseRepos = MockDatabaseBuilder.build(
                                    defaultSakstype = sakstype,
                                    defaultFnr = listOf(fnr),
                                    tilbakekrevingsbehandlingRepo = tilbakekrevingRepo,
                                ),
                                services = TestServicesBuilder.services(
                                    fritekstService = fritekstService,
                                    person = personService,
                                ),
                            )
                        }
                        defaultRequest(HttpMethod.Post, FRITEKST_PATH, listOf(Brukerrolle.Saksbehandler)) {
                            setBody(serialize(hentRequest))
                        }.apply {
                            status shouldBe forventetStatus
                            if (harTilgang) {
                                deserialize<Fritekst>(bodyAsText()) shouldBe fritekst
                            }
                        }
                        verify(personService).sjekkTilgangTilPerson(fnr, sakstype)
                        defaultRequest(HttpMethod.Post, "$FRITEKST_PATH/lagre", listOf(Brukerrolle.Saksbehandler)) {
                            setBody(serialize(lagreRequest))
                        }.apply {
                            status shouldBe forventetStatus
                        }
                    }
                    verify(personService, times(2)).sjekkTilgangTilPerson(fnr, sakstype)
                    if (harTilgang) {
                        verify(fritekstService).hentFritekst(hentDomain)
                        verify(fritekstService).lagreFritekst(lagreDomain)
                    } else {
                        verifyNoInteractions(fritekstService)
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(
        value = FritekstType::class,
        names = ["FORHÅNDSVARSEL_TILBAKEKREVING", "VEDTAKSBREV_TILBAKEKREVING", "NOTAT_TILBAKEKREVING"],
    )
    fun `avviser lesing og lagring når tilbakekrevingsreferansen ikke tilhører saken`(type: FritekstType) {
        val sakId = UUID.randomUUID()
        val referanseId = UUID.randomUUID()
        val behandlingId = TilbakekrevingsbehandlingId(referanseId)
        val fnr = Fnr.generer()
        val sakstype = Sakstype.UFØRE
        val friteksttekst = "fritekst på en annen sak"
        val hentRequest = FritekstRequestHent(referanseId.toString(), sakId.toString(), type.name)
        val lagreRequest = FritekstRequestLagre(referanseId.toString(), sakId.toString(), type.name, friteksttekst)
        val fritekstService = mock<FritekstService> {
            on { hentFritekst(FritekstHentDomain(referanseId, type, sakId)) } doReturn
                Fritekst(referanseId, type, friteksttekst).right()
        }
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(fnr, sakstype) } doReturn Unit.right()
        }
        val tilbakekrevingRepo = mock<TilbakekrevingsbehandlingRepo> {
            on { tilhørerSak(sakId, behandlingId) } doReturn false
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    databaseRepos = MockDatabaseBuilder.build(
                        defaultSakstype = sakstype,
                        defaultFnr = listOf(fnr),
                        tilbakekrevingsbehandlingRepo = tilbakekrevingRepo,
                    ),
                    services = TestServicesBuilder.services(person = personService, fritekstService = fritekstService),
                )
            }
            listOf(
                FRITEKST_PATH to serialize(hentRequest),
                "$FRITEKST_PATH/lagre" to serialize(lagreRequest),
            ).forEach { (uri, body) ->
                defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)) {
                    setBody(body)
                }.apply {
                    status shouldBe HttpStatusCode.NotFound
                    deserialize<ErrorJson>(bodyAsText()).code shouldBe "fant_ikke_fritekstreferanse"
                }
            }
        }

        verify(personService, times(2)).sjekkTilgangTilPerson(fnr, sakstype)
        verify(tilbakekrevingRepo, times(2)).tilhørerSak(sakId, behandlingId)
        verifyNoInteractions(fritekstService)
    }

    @Test
    fun `kan hente fritekst`() {
        val referanseId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val fritekst = Fritekst(referanseId = referanseId, FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING, fritekst = "fritekst for vedtak")
        val request = FritekstRequestHent(referanseId = referanseId.toString(), sakId = sakId.toString(), type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name)
        val fritekstMockService = mock<FritekstService> {
            on {
                hentFritekst(
                    FritekstHentDomain(
                        referanseId = referanseId,
                        sakId = sakId,
                        type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING,
                    ),
                )
            } doReturn fritekst.right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    databaseRepos = databaseMedTilbakekrevingsreferanse(sakId, referanseId),
                    services = TestServicesBuilder.services(fritekstService = fritekstMockService),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                FRITEKST_PATH,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.OK
                deserialize<Fritekst>(bodyAsText()) shouldBe fritekst
            }
        }
    }

    @Test
    fun `Får fritekst feil for ikke eksisterende fritekst`() {
        val referanseId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val request = FritekstRequestHent(referanseId = referanseId.toString(), sakId = sakId.toString(), type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name)
        val fritekstMockService = mock<FritekstService> {
            on {
                hentFritekst(
                    FritekstHentDomain(
                        referanseId = referanseId,
                        sakId = sakId,
                        type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING,
                    ),
                )
            } doReturn FritekstFeil.FantIkkeFritekst.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    databaseRepos = databaseMedTilbakekrevingsreferanse(sakId, referanseId),
                    services = TestServicesBuilder.services(fritekstService = fritekstMockService),
                )
            }

            defaultRequest(
                HttpMethod.Post,
                FRITEKST_PATH,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldBe FeilResponser.fantIkkeFritekst.json
            }
        }
    }

    @Test
    fun `Kan lagre fritekst`() {
        val referanseId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val friteksttekst = "fritekst for vedtak"
        val fritekstDomain = FritekstDomain(referanseId = referanseId, type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING, fritekst = friteksttekst, sakId = sakId)
        val fritekstMockService = mock<FritekstService>()

        doNothing()
            .`when`(fritekstMockService)
            .lagreFritekst(fritekstDomain)

        val request = FritekstRequestLagre(referanseId = referanseId.toString(), sakId = sakId.toString(), type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name, fritekst = friteksttekst)
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    databaseRepos = databaseMedTilbakekrevingsreferanse(sakId, referanseId),
                    services = TestServicesBuilder.services(fritekstService = fritekstMockService),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "$FRITEKST_PATH/lagre",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
        verify(fritekstMockService).lagreFritekst(fritekstDomain)
    }

    @Test
    fun `Sjekker at lagring kun skjer hvis det blir riktig domeneobjekt`() {
        val referanseId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val friteksttekst = "fritekst for vedtak"

        val request = FritekstRequestLagre(referanseId = "", sakId = sakId.toString(), type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name, fritekst = friteksttekst)
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
            }
            defaultRequest(
                HttpMethod.Post,
                "$FRITEKST_PATH/lagre",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldBe FeilResponser.ugyldigBody("Ugyldig verdi for felt 'referanseId': ''").json
            }
        }
    }

    private fun ugyldigInput(vararg feil: UgyldigInputValideringsfeil) = UgyldigInputValideringFeilResponse(
        message = "Ugyldig input",
        code = "ugyldig_felt_input",
        errors = feil.toList(),
    )

    private fun lagreRequest(fritekst: String) = FritekstRequestLagre(
        referanseId = UUID.randomUUID().toString(),
        sakId = UUID.randomUUID().toString(),
        type = FritekstType.FORHÅNDSVARSEL_TILBAKEKREVING.name,
        fritekst = fritekst,
    )

    @Test
    fun `avviser mistenkelig innhold i strengfelt uten å kalle FritekstService`() {
        val mistenkelig = "javascript:alert(1)"
        listOf(
            Triple("fritekst", "$FRITEKST_PATH/lagre", serialize(lagreRequest(fritekst = mistenkelig))),
            Triple(
                "type",
                FRITEKST_PATH,
                serialize(FritekstRequestHent(referanseId = UUID.randomUUID().toString(), sakId = UUID.randomUUID().toString(), type = mistenkelig)),
            ),
        ).forEach { (felt, uri, body) ->
            val fritekstMockService = mock<FritekstService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(services = TestServicesBuilder.services(fritekstService = fritekstMockService))
                }
                defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)) {
                    setBody(body)
                }.apply {
                    withClue("$uri - $felt") {
                        status shouldBe HttpStatusCode.BadRequest
                        deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe
                            ugyldigInput(UgyldigInputValideringsfeil(felt, "inneholder mistenkelig innhold"))
                    }
                }
            }
            verifyNoInteractions(fritekstMockService)
        }
    }

    @Test
    fun `avviser tegn utenfor tillatt tegnsett og viser tegnene i meldingen`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(HttpMethod.Post, "$FRITEKST_PATH/lagre", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(lagreRequest(fritekst = "<b>hei</b>")))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("fritekst", "inneholder tegn utenfor tillatt tegnsett: '<>'"),
                )
            }
        }
    }

    @Test
    fun `avviser fritekst over maks fritekstlengde`() {
        val maks = InputFelter.FRITEKST.maksLengde
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(HttpMethod.Post, "$FRITEKST_PATH/lagre", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(lagreRequest(fritekst = "a".repeat(maks + 1))))
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe ugyldigInput(
                    UgyldigInputValideringsfeil("fritekst", "for lang verdi, antall tegn ${maks + 1}, maks $maks"),
                )
            }
        }
    }

    @Test
    fun `lagrer fritekst opp til maks fritekstlengde`() {
        val fritekstMockService = mock<FritekstService>()
        val request = lagreRequest(fritekst = "a".repeat(InputFelter.FRITEKST.maksLengde))
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    databaseRepos = databaseMedTilbakekrevingsreferanse(UUID.fromString(request.sakId), UUID.fromString(request.referanseId)),
                    services = TestServicesBuilder.services(fritekstService = fritekstMockService),
                )
            }
            defaultRequest(HttpMethod.Post, "$FRITEKST_PATH/lagre", listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(request))
            }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
        verify(fritekstMockService).lagreFritekst(any())
    }

    private fun databaseMedTilbakekrevingsreferanse(sakId: UUID, referanseId: UUID) = MockDatabaseBuilder.build(
        tilbakekrevingsbehandlingRepo = mock {
            on { tilhørerSak(sakId, TilbakekrevingsbehandlingId(referanseId)) } doReturn true
        },
    )

    @Test
    fun `svarer ugyldig body uten å kalle FritekstService når bodyen ikke er json eller ikke passer DTO-en`() {
        listOf("{ikke json", "{}").forEach { body ->
            val fritekstMockService = mock<FritekstService>()
            testApplication {
                application {
                    testSusebakoverWithMockedDb(services = TestServicesBuilder.services(fritekstService = fritekstMockService))
                }
                defaultRequest(HttpMethod.Post, "$FRITEKST_PATH/lagre", listOf(Brukerrolle.Saksbehandler)) {
                    setBody(body)
                }.apply {
                    withClue(body) {
                        status shouldBe HttpStatusCode.BadRequest
                        bodyAsText() shouldContain "ugyldig_body"
                    }
                }
            }
            verifyNoInteractions(fritekstMockService)
        }
    }
}
