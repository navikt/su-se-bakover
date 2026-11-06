package no.nav.su.se.bakover.web.routes.vedtak

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.Omgjøringsgrunn
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.vedtak.application.NySøknadCommandOmgjøring
import no.nav.su.se.bakover.vedtak.application.VedtakService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SøknadsbehandlingJson
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import person.domain.PersonService
import java.util.UUID

internal class VedtakRoutesTest {
    @ParameterizedTest
    @EnumSource(Brukerrolle::class)
    fun `bare saksbehandler og attestant kan starte ny søknadsbehandling`(rolle: Brukerrolle) {
        val (sak, søknadsbehandling) = søknadsbehandlingVilkårsvurdertInnvilget()
        val sakId = sak.id
        val vedtakId = UUID.randomUUID()
        val fnr = sak.fnr
        val sakstype = sak.type
        val harTillattRolle = when (rolle) {
            Brukerrolle.Saksbehandler, Brukerrolle.Attestant -> true
            Brukerrolle.Veileder, Brukerrolle.Drift -> false
        }
        val request = NySøknadsbehandlingRequest(
            omgjøringsårsak = Revurderingsårsak.Årsak.OMGJØRING_EGET_TILTAK,
            omgjøringsgrunn = Omgjøringsgrunn.FEIL_LOVANVENDELSE,
        )
        val command = NySøknadCommandOmgjøring(request.omgjøringsårsak.name, request.omgjøringsgrunn.name)
        val vedtakService = mock<VedtakService> {
            on {
                startNySøknadsbehandlingForAvslag(eq(sakId), eq(vedtakId), any(), eq(command))
            } doReturn søknadsbehandling.right()
        }
        val personService = mock<PersonService> {
            on { sjekkTilgangTilPerson(fnr, sakstype) } doReturn Unit.right()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    defaultSakstype = sakstype,
                    defaultFnr = listOf(fnr),
                    services = TestServicesBuilder.services(person = personService, vedtakService = vedtakService),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "$SAK_PATH/$sakId/vedtak/$vedtakId/nySoknadsbehandling",
                listOf(rolle),
            ) {
                setBody(serialize(request))
            }.apply {
                if (harTillattRolle) {
                    status shouldBe HttpStatusCode.Created
                    val response = deserialize<SøknadsbehandlingJson>(bodyAsText())
                    response.id shouldBe søknadsbehandling.id.toString()
                    response.sakId shouldBe sakId
                } else {
                    status shouldBe HttpStatusCode.Forbidden
                    deserialize<ErrorJson>(bodyAsText()).code shouldBe "mangler_rolle"
                }
            }
        }

        if (harTillattRolle) {
            verify(personService).sjekkTilgangTilPerson(fnr, sakstype)
            verify(vedtakService).startNySøknadsbehandlingForAvslag(eq(sakId), eq(vedtakId), any(), eq(command))
        } else {
            verifyNoInteractions(personService, vedtakService)
        }
    }

    private data class NySøknadsbehandlingRequest(
        val omgjøringsårsak: Revurderingsårsak.Årsak,
        val omgjøringsgrunn: Omgjøringsgrunn,
    )
}
