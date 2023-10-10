package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlag
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.LeggTilBosituasjonRequest
import no.nav.su.se.bakover.domain.revurdering.vilkår.bosituasjon.LeggTilBosituasjonerRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagUtenBeregning
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandling
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.KunneIkkeHentePerson
import person.domain.PersonService
import java.util.UUID

internal class SøknadsbehandlingServiceGrunnlagBosituasjonTest {

    private val behandlingId = UUID.randomUUID()
    private val stønadsperiode = Stønadsperiode.create(år(2021))

    @Test
    fun `kan lagre EPS selvom man ikke har tilgang til saken`() {
        val (_, uavklart) = nySøknadsbehandlingMedStønadsperiode()

        val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = uavklart.periode,
            fnr = Fnr.generer(),
        )

        val expected = uavklart.copy(
            grunnlagsdataOgVilkårsvurderinger = uavklart.grunnlagsdataOgVilkårsvurderinger.copy(
                grunnlagsdata = Grunnlagsdata.create(bosituasjon = listOf(bosituasjon)),
                vilkårsvurderinger = uavklart.vilkårsvurderinger.oppdaterVilkår(tilstrekkeligDokumentert()),
            ),
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturnConsecutively listOf(
                uavklart,
                uavklart,
                expected,
            )
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.IkkeTilgangTilPerson.left()
        }
        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            personService = personServiceMock,
            clock = fixedClock,
        ).leggTilBosituasjongrunnlag(
            saksbehandler = saksbehandler,
            request = LeggTilBosituasjonerRequest(
                behandlingId = uavklart.id,
                bosituasjoner = listOf(
                    LeggTilBosituasjonRequest(
                        periode = stønadsperiode.periode,
                        epsFnr = bosituasjon.fnr.toString(),
                        delerBolig = null,
                        ektemakeEllerSamboerUførFlyktning = false,
                    ),
                ),
            ),
        ) shouldBe KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeSlåOppEPS.left()

        verify(personServiceMock).hentPerson(bosituasjon.fnr)
        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe uavklart.id })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock)
    }

    @Test
    fun `fullfør bosituasjon svarer med feil hvis man ikke finner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).leggTilBosituasjongrunnlag(
            saksbehandler = saksbehandler,
            request = LeggTilBosituasjonerRequest(
                behandlingId = behandlingId,
                bosituasjoner = listOf(
                    LeggTilBosituasjonRequest(
                        periode = stønadsperiode.periode,
                        epsFnr = null,
                        delerBolig = false,
                        ektemakeEllerSamboerUførFlyktning = null,
                    ),
                ),
            ),
        )

        response shouldBe KunneIkkeLeggeTilBosituasjongrunnlag.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `fullfør gir error hvis behandling er i ugyldig tilstand`() {
        val tilAttestering = søknadsbehandlingTilAttesteringAvslagUtenBeregning().second

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn tilAttestering
        }

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).leggTilBosituasjongrunnlag(
            saksbehandler = saksbehandler,
            request = LeggTilBosituasjonerRequest(
                behandlingId = behandlingId,
                bosituasjoner = listOf(
                    LeggTilBosituasjonRequest(
                        periode = stønadsperiode.periode,
                        epsFnr = null,
                        delerBolig = false,
                        ektemakeEllerSamboerUførFlyktning = null,
                    ),
                ),
            ),
        ) shouldBe KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeLeggeTilGrunnlag(
            KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon.UgyldigTilstand(
                fra = tilAttestering::class,
                til = VilkårsvurdertSøknadsbehandling::class,
            ),
        ).left()
    }

    @Test
    fun `endrer bosituasjon & sletter skattegrunnlaget`() {
        val søknadsbehandling = vilkårsvurdertSøknadsbehandling(
            stønadsperiode = stønadsperiode,
            sakOgSøknad = nySakMedjournalførtSøknadOgOppgave(),
        ).second as VilkårsvurdertSøknadsbehandling.Innvilget

        val bosituasjon = bosituasjongrunnlagEnslig()

        val expected = søknadsbehandling.copy(
            grunnlagsdataOgVilkårsvurderinger = søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.copy(
                grunnlagsdata = Grunnlagsdata.create(
                    bosituasjon = listOf(bosituasjon),
                ),
                vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger.oppdaterVilkår(tilstrekkeligDokumentert()),
            ),
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn søknadsbehandling
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            clock = fixedClock,
        ).leggTilBosituasjongrunnlag(
            saksbehandler = saksbehandler,
            request = LeggTilBosituasjonerRequest(
                behandlingId = søknadsbehandling.id,
                bosituasjoner = listOf(
                    LeggTilBosituasjonRequest(
                        periode = stønadsperiode.periode,
                        epsFnr = null,
                        delerBolig = false,
                        ektemakeEllerSamboerUførFlyktning = null,
                    ),
                ),
            ),
        ).getOrFail()

        response shouldBe expected.copy(
            grunnlagsdataOgVilkårsvurderinger = response.grunnlagsdataOgVilkårsvurderinger.oppdaterBosituasjon(
                bosituasjon = listOf(
                    bosituasjon.copy(
                        id = response.grunnlagsdata.bosituasjon.first().id,
                    ),
                ),
            ),
            søknadsbehandlingsHistorikk = expected.søknadsbehandlingsHistorikk.leggTilNyHendelse(
                Søknadsbehandlingshendelse(
                    tidspunkt = fixedTidspunkt,
                    saksbehandler = saksbehandler,
                    handling = SøknadsbehandlingsHandling.OppdatertBosituasjon,
                ),
            ),
        )

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe søknadsbehandling.id })
        verify(søknadsbehandlingRepoMock).lagre(any(), anyOrNull())
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }
}
