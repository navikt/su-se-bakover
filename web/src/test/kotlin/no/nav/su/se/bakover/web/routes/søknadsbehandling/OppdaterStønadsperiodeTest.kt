package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.behandlingId
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.sakId
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

class OppdaterStønadsperiodeTest {
    private val url = "$sakPath/$sakId/behandlinger/$behandlingId/stønadsperiode"
    private val services = TestServicesBuilder.services()

    @Test
    fun `svarer med 404 dersom behandling ikke finnes`() {
        val serviceMock = mock<SøknadsbehandlingService> {
            on { oppdaterStønadsperiode(any()) } doReturn SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling.left()
        }

        withTestApplication(
            { testSusebakover(services = services.copy(søknadsbehandling = serviceMock)) },
        ) {
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-31"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Fant ikke behandling"
            }
        }
    }

    @Test
    fun `svarer med 400 dersom perioden starter tidligere enn 2021`() {
        withTestApplication(
            { testSusebakover(services = services) },
        ) {
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2019-01-01", "tilOgMed": "2021-12-31"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "En stønadsperiode kan ikke starte før 2021"
            }
        }
    }

    @Test
    fun `svarer med 400 dersom perioden er lenger enn 12 måneder`() {
        withTestApplication(
            { testSusebakover(services = services) },
        ) {
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2022-12-31"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "En stønadsperiode kan være maks 12 måneder"
            }
        }
    }

    @Test
    fun `svarer med 400 dersom fraOgMed ikke er første dag i måneden`() {
        withTestApplication(
            { testSusebakover(services = services) },
        ) {
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2021-01-15", "tilOgMed": "2021-12-31"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Perioder kan kun starte på første dag i måneden"
            }
        }
    }

    @Test
    fun `svarer med 400 dersom tilOgMed ikke er siste dag i måneden`() {
        withTestApplication(
            { testSusebakover(services = services) },
        ) {
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-15"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Perioder kan kun avsluttes siste dag i måneden"
            }
        }
    }

    @Test
    fun `svarer med 400 dersom tilOgMed er før fraOgMed`() {
        withTestApplication(
            { testSusebakover(services = services) },
        ) {
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2021-12-01", "tilOgMed": "2021-01-31"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Startmåned må være tidligere eller lik sluttmåned"
            }
        }
    }

    @Test
    fun `svarer med 201 og søknadsbehandling hvis alt er ok`() {
        val søknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2021),
            søknad = BehandlingTestUtils.journalførtSøknadMedOppgave,
            oppgaveId = OppgaveId("oppgaveId"),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = FnrGenerator.random(),
            fritekstTilBrev = "",
            stønadsperiode = BehandlingTestUtils.stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
        )

        val serviceMock = mock<SøknadsbehandlingService> {
            on { oppdaterStønadsperiode(any()) } doReturn søknadsbehandling.right()
        }

        withTestApplication(
            { testSusebakover(services = services.copy(søknadsbehandling = serviceMock)) },
        ) {
            defaultRequest(
                HttpMethod.Post,
                url,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{"periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-31"}, "begrunnelse": "begrunnelsen"}""")
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
            }
        }
    }
}
