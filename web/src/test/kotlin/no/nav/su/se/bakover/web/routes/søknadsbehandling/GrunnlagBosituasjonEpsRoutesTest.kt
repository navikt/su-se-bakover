package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.journalførtSøknadMedOppgave
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.stønadsperiode
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.util.UUID

class GrunnlagBosituasjonEpsRoutesTest {

    private val services = TestServicesBuilder.services()
    private val fnr = FnrGenerator.random()
    private val søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart = Søknadsbehandling.Vilkårsvurdert.Uavklart(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.EPOCH,
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(2021),
        søknad = journalførtSøknadMedOppgave,
        oppgaveId = OppgaveId("oppgaveId"),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        fnr = FnrGenerator.random(),
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata.EMPTY,
        vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
    )

    @Test
    fun `andre roller enn saksbehandler skal ikke ha tilgang til bosituasjon`() {
        Brukerrolle.values().filterNot { it == Brukerrolle.Saksbehandler }.forEach { rolle ->
            withTestApplication(
                {
                    testSusebakover()
                },
            ) {
                defaultRequest(
                    HttpMethod.Post,
                    "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/eps",
                    listOf(rolle),
                ) {
                    setBody("""{ "epsFnr": null}""".trimIndent())
                }.apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `happy case`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjonEpsgrunnlag(any()) } doReturn søknadsbehandling.right()
        }

        withTestApplication(
            {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/eps",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "epsFnr": null}""".trimIndent())
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                verify(søknadsbehandlingServiceMock).leggTilBosituasjonEpsgrunnlag(
                    argThat {
                        it shouldBe LeggTilBosituasjonEpsRequest(
                            behandlingId = søknadsbehandling.id,
                            epsFnr = null,
                        )
                    }
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `happy case med eps`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjonEpsgrunnlag(any()) } doReturn søknadsbehandling.right()
        }

        withTestApplication(
            {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/eps",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "epsFnr": "$fnr"}""".trimIndent())
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                verify(søknadsbehandlingServiceMock).leggTilBosituasjonEpsgrunnlag(
                    argThat {
                        it shouldBe LeggTilBosituasjonEpsRequest(
                            behandlingId = søknadsbehandling.id,
                            epsFnr = fnr,
                        )
                    }
                )
                verifyNoMoreInteractions(søknadsbehandlingServiceMock)
            }
        }
    }

    @Test
    fun `service finner ikke behandling`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjonEpsgrunnlag(any()) } doReturn SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.FantIkkeBehandling.left()
        }

        withTestApplication(
            {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/eps",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "epsFnr": "$fnr"}""".trimIndent())
            }.apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain ("fant_ikke_behandling")
            }
        }
    }

    @Test
    fun `service klarer ikke hente person i pdl`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjonEpsgrunnlag(any()) } doReturn SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KlarteIkkeHentePersonIPdl.left()
        }

        withTestApplication(
            {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/eps",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "epsFnr": "$fnr"}""".trimIndent())
            }.apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain ("fant_ikke_person")
            }
        }
    }

    @Test
    fun `behandling har ugyldig tilstand`() {
        val søknadsbehandlingServiceMock = mock<SøknadsbehandlingService> {
            on { leggTilBosituasjonEpsgrunnlag(any()) } doReturn SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag.UgyldigTilstand(
                fra = Søknadsbehandling.TilAttestering.Avslag.UtenBeregning::class,
                til = Søknadsbehandling.Vilkårsvurdert::class,
            ).left()
        }

        withTestApplication(
            {
                testSusebakover(services = services.copy(søknadsbehandling = søknadsbehandlingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${søknadsbehandling.sakId}/behandlinger/${søknadsbehandling.id}/grunnlag/bosituasjon/eps",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "epsFnr": "$fnr"}""".trimIndent())
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain ("ugyldig_tilstand")
            }
        }
    }
}
