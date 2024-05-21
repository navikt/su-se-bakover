package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import behandling.domain.UnderkjennAttesteringsgrunnBehandling
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn.KunneIkkeUnderkjenneSøknadsbehandling
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknad.oppgaveIdSøknad
import no.nav.su.se.bakover.test.tilAttesteringSøknadsbehandlingUføre
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.PersonService
import java.util.UUID

class SøknadsbehandlingServiceUnderkjennTest {
    private val fnr = Fnr.generer()

    private val underkjentAttestering = Attestering.Underkjent(
        attestant = NavIdentBruker.Attestant("a"),
        grunn = UnderkjennAttesteringsgrunnBehandling.ANDRE_FORHOLD,
        kommentar = "begrunnelse",
        opprettet = fixedTidspunkt,
    )

    private val innvilgetBehandlingTilAttestering = tilAttesteringSøknadsbehandlingUføre(
        sakInfo = SakInfo(
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2021),
            fnr = fnr,
            type = Sakstype.UFØRE,
        ),
        stønadsperiode = Stønadsperiode.create(år(2021)),
    ).second as SøknadsbehandlingTilAttestering.Innvilget

    @Test
    fun `Fant ikke behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val personServiceMock = mock<PersonService>()
        val oppgaveServiceMock = mock<OppgaveService>()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering,
            ),
        )

        actual shouldBe KunneIkkeUnderkjenneSøknadsbehandling.FantIkkeBehandling.left()

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
        )
    }

    @Test
    fun `Feil behandlingsstatus`() {
        val behandling: IverksattSøknadsbehandling.Innvilget =
            iverksattSøknadsbehandlingUføre().second as IverksattSøknadsbehandling.Innvilget

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val personServiceMock = mock<PersonService>()
        val oppgaveServiceMock = mock<OppgaveService>()

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = behandling.id,
                attestering = underkjentAttestering,
            ),
        ) shouldBe KunneIkkeUnderkjenneSøknadsbehandling.UgyldigTilstand(
            fra = behandling::class,
        ).left()

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandling.id })
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
        )
    }

    @Test
    fun `attestant kan ikke være den samme som saksbehandler`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
        }

        val attestantSomErLikSaksbehandler =
            NavIdentBruker.Attestant(innvilgetBehandlingTilAttestering.saksbehandler.navIdent)

        val personServiceMock = mock<PersonService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val observerMock: StatistikkEventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = observerMock,
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = Attestering.Underkjent(
                    attestant = attestantSomErLikSaksbehandler,
                    grunn = underkjentAttestering.grunn,
                    kommentar = underkjentAttestering.kommentar,
                    opprettet = fixedTidspunkt,
                ),
            ),
        )

        actual shouldBe KunneIkkeUnderkjenneSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            personServiceMock,
            oppgaveServiceMock,
        )
        verifyNoInteractions(observerMock)
    }

    @Test
    fun `Underkjenner selvom vi ikke klarer oppdatere oppgave`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
            on { hentForSak(any(), anyOrNull()) } doReturn listOf(innvilgetBehandlingTilAttestering)
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { oppdaterOppgave(any(), any()) } doReturn KunneIkkeOppdatereOppgave.FeilVedHentingAvOppgave.left()
        }
        val observerMock: StatistikkEventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            observer = observerMock,
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering,
            ),
        )

        val underkjentMedNyOppgaveIdOgAttestering = UnderkjentSøknadsbehandling.Innvilget(
            id = innvilgetBehandlingTilAttestering.id,
            opprettet = innvilgetBehandlingTilAttestering.opprettet,
            sakId = innvilgetBehandlingTilAttestering.sakId,
            saksnummer = innvilgetBehandlingTilAttestering.saksnummer,
            søknad = innvilgetBehandlingTilAttestering.søknad,
            oppgaveId = oppgaveIdSøknad,
            fnr = innvilgetBehandlingTilAttestering.fnr,
            beregning = innvilgetBehandlingTilAttestering.beregning,
            simulering = innvilgetBehandlingTilAttestering.simulering,
            saksbehandler = innvilgetBehandlingTilAttestering.saksbehandler,
            attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(underkjentAttestering),
            fritekstTilBrev = "",
            aldersvurdering = innvilgetBehandlingTilAttestering.aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger = innvilgetBehandlingTilAttestering.grunnlagsdataOgVilkårsvurderinger,
            sakstype = innvilgetBehandlingTilAttestering.sakstype,
            søknadsbehandlingsHistorikk = innvilgetBehandlingTilAttestering.søknadsbehandlingsHistorikk,
        )

        actual shouldBe underkjentMedNyOppgaveIdOgAttestering.right()

        inOrder(
            søknadsbehandlingRepoMock,
            oppgaveServiceMock,
            observerMock,
        ) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(oppgaveServiceMock).oppdaterOppgave(
                argThat { it shouldBe oppgaveIdSøknad },
                argThat {
                    it shouldBe OppdaterOppgaveInfo(
                        "Behandling har blitt underkjent",
                        Oppgavetype.BEHANDLE_SAK,
                        null,
                        OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(saksbehandler.navIdent),
                    )
                },
            )
            verify(søknadsbehandlingRepoMock).defaultTransactionContext()
            verify(søknadsbehandlingRepoMock).lagre(
                søknadsbehandling = argThat { it shouldBe underkjentMedNyOppgaveIdOgAttestering },
                sessionContext = anyOrNull(),
            )
            verify(observerMock).handle(
                argThat {
                    it shouldBe StatistikkEvent.Behandling.Søknad.Underkjent.Innvilget(
                        underkjentMedNyOppgaveIdOgAttestering,
                    )
                },
            )
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            oppgaveServiceMock,
        )
    }

    @Test
    fun `underkjenner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilgetBehandlingTilAttestering
            on { hentForSak(any(), anyOrNull()) } doReturn emptyList()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { oppdaterOppgave(any(), any()) } doReturn nyOppgaveHttpKallResponse(oppgaveId = oppgaveIdSøknad).right()
        }

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
        ).underkjenn(
            SøknadsbehandlingService.UnderkjennRequest(
                behandlingId = innvilgetBehandlingTilAttestering.id,
                attestering = underkjentAttestering,
            ),
        )

        val underkjentMedNyOppgaveIdOgAttestering = UnderkjentSøknadsbehandling.Innvilget(
            id = innvilgetBehandlingTilAttestering.id,
            opprettet = innvilgetBehandlingTilAttestering.opprettet,
            sakId = innvilgetBehandlingTilAttestering.sakId,
            saksnummer = innvilgetBehandlingTilAttestering.saksnummer,
            søknad = innvilgetBehandlingTilAttestering.søknad,
            oppgaveId = oppgaveIdSøknad,
            fnr = innvilgetBehandlingTilAttestering.fnr,
            beregning = innvilgetBehandlingTilAttestering.beregning,
            simulering = innvilgetBehandlingTilAttestering.simulering,
            saksbehandler = innvilgetBehandlingTilAttestering.saksbehandler,
            attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(underkjentAttestering),
            fritekstTilBrev = "",
            aldersvurdering = innvilgetBehandlingTilAttestering.aldersvurdering,
            grunnlagsdataOgVilkårsvurderinger = innvilgetBehandlingTilAttestering.grunnlagsdataOgVilkårsvurderinger,
            sakstype = innvilgetBehandlingTilAttestering.sakstype,
            søknadsbehandlingsHistorikk = innvilgetBehandlingTilAttestering.søknadsbehandlingsHistorikk,
        )

        actual shouldBe underkjentMedNyOppgaveIdOgAttestering.right()

        inOrder(
            søknadsbehandlingRepoMock,
            oppgaveServiceMock,
        ) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilgetBehandlingTilAttestering.id })
            verify(oppgaveServiceMock).oppdaterOppgave(
                argThat { it shouldBe oppgaveIdSøknad },
                argThat {
                    it shouldBe OppdaterOppgaveInfo(
                        "Behandling har blitt underkjent",
                        Oppgavetype.BEHANDLE_SAK,
                        null,
                        OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(saksbehandler.navIdent),
                    )
                },
            )
            verify(søknadsbehandlingRepoMock).defaultTransactionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(underkjentMedNyOppgaveIdOgAttestering), anyOrNull())
        }

        verifyNoMoreInteractions(
            søknadsbehandlingRepoMock,
            oppgaveServiceMock,
        )
    }
}
