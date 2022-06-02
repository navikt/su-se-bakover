package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag
import no.nav.su.se.bakover.service.vilkår.BosituasjonValg
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdert
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class SøknadsbehandlingServiceGrunnlagBosituasjonTest {

    private val sakId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val oppgaveId = OppgaveId("o")
    private val stønadsperiode = Stønadsperiode.create(år(2021))

    @Test
    fun `ufullstendig svarer med feil hvis man ikke finner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val request = LeggTilBosituasjonEpsRequest(
            behandlingId = behandlingId,
            epsFnr = null,
        )

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).leggTilBosituasjonEpsgrunnlag(request)

        response shouldBe KunneIkkeLeggeTilBosituasjonEpsGrunnlag.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `kan oppdatere med ufullstendig bosituasjon`() {
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn søknadsbehandlingVilkårsvurdertUavklart(
                    grunnlagsdata = grunnlagsdataEnsligMedFradrag(),
                ).second
            },
        ).let {
            val response = it.søknadsbehandlingService.leggTilBosituasjonEpsgrunnlag(
                request = LeggTilBosituasjonEpsRequest(
                    behandlingId = UUID.randomUUID(),
                    epsFnr = null,
                ),
            ).getOrFail()

            response.grunnlagsdata.bosituasjon.single().shouldBeType<Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps>()
        }
    }

    @Test
    fun `ufullstendig gir error hvis behandling er i ugyldig tilstand`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn søknadsbehandlingTilAttesteringAvslagUtenBeregning().second
        }

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).leggTilBosituasjonEpsgrunnlag(
            LeggTilBosituasjonEpsRequest(behandlingId = behandlingId, epsFnr = null),
        )

        actual shouldBe KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KunneIkkeOppdatereBosituasjon(
            feil = Søknadsbehandling.KunneIkkeOppdatereBosituasjon.UgyldigTilstand(
                Søknadsbehandling.TilAttestering.Avslag.UtenBeregning::class,
                Søknadsbehandling.Vilkårsvurdert::class,
            ),
        ).left()
    }

    @Test
    fun `Kan ikke lagre EPS dersom vi ikke finner personen`() {
        val uavklart = søknadsbehandlingVilkårsvurdertUavklart().second

        val nyBosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = uavklart.periode,
            fnr = Fnr.generer(),
        )
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn uavklart
        }

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            personService = personServiceMock,
        ).leggTilBosituasjonEpsgrunnlag(
            LeggTilBosituasjonEpsRequest(behandlingId = uavklart.id, epsFnr = nyBosituasjon.fnr),
        ) shouldBe KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KlarteIkkeHentePersonIPdl.left()
    }

    @Test
    fun `Kan ikke lagre EPS dersom personkallet feiler`() {
        val uavklart = søknadsbehandlingVilkårsvurdertUavklart().second

        val nyBosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = uavklart.periode,
            fnr = Fnr.generer(),
        )
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.Ukjent.left()
        }
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn uavklart
        }

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            personService = personServiceMock,
        ).leggTilBosituasjonEpsgrunnlag(
            LeggTilBosituasjonEpsRequest(behandlingId = uavklart.id, epsFnr = nyBosituasjon.fnr),
        ) shouldBe KunneIkkeLeggeTilBosituasjonEpsGrunnlag.KlarteIkkeHentePersonIPdl.left()
    }

    @Test
    fun `ufullstendig happy case`() {
        val uavklart = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = behandlingId,
            opprettet = fixedTidspunkt,
            sakId = sakId,
            saksnummer = Saksnummer(2021),
            søknad = Søknad.Journalført.MedOppgave.IkkeLukket(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = oppgaveId,
                journalpostId = JournalpostId("j"),
            ),
            oppgaveId = oppgaveId,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = Fnr.generer(),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere(uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående),
        )

        val bosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = stønadsperiode.periode,
        )

        val expected = uavklart.copy(
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(
                    bosituasjon,
                ),
            ),
            behandlingsinformasjon = uavklart.behandlingsinformasjon,
            vilkårsvurderinger = uavklart.vilkårsvurderinger.leggTil(tilstrekkeligDokumentert())
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturnConsecutively listOf(
                uavklart,
                uavklart,
                expected,
            )
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            clock = fixedClock,
        ).leggTilBosituasjonEpsgrunnlag(
            LeggTilBosituasjonEpsRequest(behandlingId = behandlingId, epsFnr = null),
        ).orNull()!!

        response shouldBe expected.copy(
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(
                    bosituasjon.copy(
                        id = (response as Søknadsbehandling.Vilkårsvurdert).grunnlagsdata.bosituasjon.first().id,
                    ),
                ),
            ),
        )

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verify(søknadsbehandlingRepoMock).defaultTransactionContext()
        verify(søknadsbehandlingRepoMock).lagre(
            any(),
            anyOrNull(),
        )
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `kan lagre EPS selvom man ikke har tilgang til saken`() {
        val (_, uavklart) = søknadsbehandlingVilkårsvurdertUavklart()

        val bosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = uavklart.periode,
            fnr = Fnr.generer(),
        )

        val expected = uavklart.copy(
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(
                    bosituasjon,
                ),
            ),
            behandlingsinformasjon = uavklart.behandlingsinformasjon,
            vilkårsvurderinger = uavklart.vilkårsvurderinger.leggTil(tilstrekkeligDokumentert())
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
        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            personService = personServiceMock,
            clock = fixedClock,
        ).leggTilBosituasjonEpsgrunnlag(
            LeggTilBosituasjonEpsRequest(behandlingId = uavklart.id, epsFnr = bosituasjon.fnr),
        ).orNull()!!

        response shouldBe expected.copy(
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(
                    bosituasjon.copy(
                        id = (response as Søknadsbehandling.Vilkårsvurdert).grunnlagsdata.bosituasjon.first().id,
                    ),
                ),
            ),
        )

        verify(personServiceMock).hentPerson(bosituasjon.fnr)
        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe uavklart.id })
        verify(søknadsbehandlingRepoMock).defaultTransactionContext()
        verify(søknadsbehandlingRepoMock).lagre(
            any(),
            anyOrNull(),
        )
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock)
    }

    @Test
    fun `fullfør bosituasjon svarer med feil hvis man ikke finner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val request = FullførBosituasjonRequest(
            behandlingId = behandlingId,
            bosituasjon = BosituasjonValg.BOR_ALENE,
        )

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).fullførBosituasjongrunnlag(request)

        response shouldBe KunneIkkeFullføreBosituasjonGrunnlag.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `fullfør gir error hvis behandling er i ugyldig tilstand`() {
        val tilAttestering = søknadsbehandlingTilAttesteringAvslagUtenBeregning().second

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn tilAttestering
        }

        shouldThrow<StatusovergangVisitor.UgyldigStatusovergangException> {
            createSøknadsbehandlingService(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            ).fullførBosituasjongrunnlag(
                FullførBosituasjonRequest(
                    behandlingId = behandlingId,
                    bosituasjon = BosituasjonValg.BOR_ALENE,
                ),
            )
        }
    }

    @Test
    fun `fullfør happy case`() {
        val uavklart = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = behandlingId,
            opprettet = fixedTidspunkt,
            sakId = sakId,
            saksnummer = Saksnummer(2021),
            søknad = Søknad.Journalført.MedOppgave.IkkeLukket(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = oppgaveId,
                journalpostId = JournalpostId("j"),
            ),
            oppgaveId = oppgaveId,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = Fnr.generer(),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = stønadsperiode.periode,
                    ),
                ),
            ),
            vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere(uhåndtert = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående),
        )

        val bosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = stønadsperiode.periode,
        )

        val expected = uavklart.copy(
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(
                    bosituasjon,
                ),
            ),
            behandlingsinformasjon = uavklart.behandlingsinformasjon,
            vilkårsvurderinger = uavklart.vilkårsvurderinger.leggTil(tilstrekkeligDokumentert()),
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn uavklart
            on { hent(any()) } doReturnConsecutively listOf(
                uavklart,
                expected,
            )
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            clock = fixedClock,
        ).fullførBosituasjongrunnlag(
            FullførBosituasjonRequest(
                behandlingId = behandlingId,
                bosituasjon = BosituasjonValg.BOR_ALENE,
            ),
        ).orNull()!!

        response shouldBe expected.copy(
            grunnlagsdata = Grunnlagsdata.create(
                bosituasjon = listOf(
                    bosituasjon.copy(
                        id = (response as Søknadsbehandling.Vilkårsvurdert).grunnlagsdata.bosituasjon.first().id,
                    ),
                ),
            ),
        )

        verify(søknadsbehandlingRepoMock, Times(2)).hent(argThat { it shouldBe behandlingId })
        verify(søknadsbehandlingRepoMock, Times(2)).defaultTransactionContext()
        verify(søknadsbehandlingRepoMock, Times(2)).lagre(any(), anyOrNull())
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }
}
