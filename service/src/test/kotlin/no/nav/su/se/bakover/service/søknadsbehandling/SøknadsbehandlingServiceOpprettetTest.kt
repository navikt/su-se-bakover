package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import behandling.søknadsbehandling.domain.KunneIkkeOppretteSøknadsbehandling
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FnrWrapper
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshistorikk
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.test.argShouldBe
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.søknad.nySakMedLukketSøknad
import no.nav.su.se.bakover.test.søknad.nySakMedNySøknad
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknad.oppgaveIdSøknad
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandlingUføreDefault
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import vilkår.vurderinger.domain.Grunnlagsdata
import vilkår.vurderinger.domain.StøtterHentingAvEksternGrunnlag
import java.util.UUID

internal class SøknadsbehandlingServiceOpprettetTest {

    @Test
    fun `svarer med feil dersom vi ikke finner søknad`() {
        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn nySakUføre(fixedClock).first.right()
            },
        ).also {
            shouldThrow<IllegalArgumentException> {
                it.søknadsbehandlingService.opprett(
                    SøknadsbehandlingService.OpprettRequest(
                        søknadId = UUID.randomUUID(),
                        sakId = sakId,
                        saksbehandler = saksbehandler,
                    ),
                )
            }.message shouldContain "Fant ikke søknad"
        }
    }

    @Test
    fun `svarer med feil dersom man oppretter behandling på lukket søknad`() {
        val (sak, lukketSøknad) = nySakMedLukketSøknad()

        SøknadsbehandlingServiceAndMocks(
            sakService = mock { on { hentSak(any<UUID>()) } doReturn sak.right() },
        ).also {
            it.søknadsbehandlingService.opprett(
                SøknadsbehandlingService.OpprettRequest(
                    søknadId = lukketSøknad.id,
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe KunneIkkeOppretteSøknadsbehandling.ErLukket.left()
        }
    }

    @Test
    fun `svarer med feil dersom søknad ikke er journalført med oppgave`() {
        val (sak, søknad) = nySakMedNySøknad()

        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        ).also {
            it.søknadsbehandlingService.opprett(
                SøknadsbehandlingService.OpprettRequest(
                    søknadId = søknad.id,
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe KunneIkkeOppretteSøknadsbehandling.ManglerOppgave.left()
        }
    }

    @Test
    fun `svarer med feil dersom det allerede finnes en åpen søknadsbehandling`() {
        var (sak, _) = vilkårsvurdertSøknadsbehandlingUføreDefault()

        val nySøknad = nySøknadJournalførtMedOppgave(
            clock = fixedClock,
            sakId = sak.id,
            søknadInnhold = søknadinnholdUføre(
                personopplysninger = FnrWrapper(sak.fnr),
            ),
        )

        sak = sak.copy(søknader = sak.søknader + nySøknad)

        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        ).also {
            it.søknadsbehandlingService.opprett(
                SøknadsbehandlingService.OpprettRequest(
                    søknadId = nySøknad.id,
                    sakId = nySøknad.sakId,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe KunneIkkeOppretteSøknadsbehandling.HarÅpenSøknadsbehandling.left()
        }
    }

    @Test
    fun `Oppretter behandling og publiserer event`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        // Skal kunne starte ny behandling selv om vi har iverksatte behandlinger.
        val eksisterendeSøknadsbehandlinger = listOf(
            søknadsbehandlingIverksattAvslagMedBeregning().second,
            søknadsbehandlingIverksattAvslagUtenBeregning().second,
            søknadsbehandlingIverksattInnvilget().second,
        )

        val capturedSøknadsbehandling = argumentCaptor<VilkårsvurdertSøknadsbehandling.Uavklart>()
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentForSak(any(), anyOrNull()) } doReturn eksisterendeSøknadsbehandlinger
            doNothing().whenever(mock).lagre(capturedSøknadsbehandling.capture(), anyOrNull())
        }

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            oppgaveService = mock {
                on { oppdaterOppgave(any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
        )

        val (_, actualBehandling) = serviceAndMocks.søknadsbehandlingService.opprett(
            SøknadsbehandlingService.OpprettRequest(
                søknadId = søknad.id,
                sakId = sak.id,
                saksbehandler = saksbehandler,
            ),
        ).getOrFail()

        actualBehandling.shouldBeEqualToIgnoringFields(
            VilkårsvurdertSøknadsbehandling.Uavklart(
                id = capturedSøknadsbehandling.firstValue.id,
                opprettet = capturedSøknadsbehandling.firstValue.opprettet,
                sakId = søknad.sakId,
                saksnummer = saksnummer,
                søknad = søknad,
                oppgaveId = søknad.oppgaveId,
                fnr = søknad.fnr,
                fritekstTilBrev = "",
                aldersvurdering = null,
                grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling(
                    grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                    vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
                    eksterneGrunnlag = StøtterHentingAvEksternGrunnlag.ikkeHentet(),
                ),
                attesteringer = Attesteringshistorikk.empty(),
                sakstype = sak.type,
                saksbehandler = saksbehandler,
                søknadsbehandlingsHistorikk = Søknadsbehandlingshistorikk.nyHistorikk(
                    hendelse = Søknadsbehandlingshendelse(
                        tidspunkt = fixedTidspunkt,
                        saksbehandler = saksbehandler,
                        handling = SøknadsbehandlingsHandling.StartetBehandling,
                    ),
                ),
            ),
            // periode er null for VilkårsvurdertSøknadsbehandling.Uavklart og vil gi exception dersom man kaller get() på den.
            VilkårsvurdertSøknadsbehandling.Uavklart::periode,
        )
        verify(serviceAndMocks.sakService).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(serviceAndMocks.oppgaveService).oppdaterOppgave(
            argThat { it shouldBe oppgaveIdSøknad },
            argThat {
                it shouldBe OppdaterOppgaveInfo(
                    beskrivelse = "Tilordnet oppgave til saksbehandler",
                    tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(saksbehandler.navIdent),
                )
            },
        )
        verify(søknadsbehandlingRepoMock).defaultTransactionContext()
        verify(søknadsbehandlingRepoMock).lagre(argShouldBe(actualBehandling), sessionContext = anyOrNull())
        verify(serviceAndMocks.observer).handle(
            argThat {
                it shouldBe StatistikkEvent.Behandling.Søknad.Opprettet(
                    actualBehandling,
                    saksbehandler = saksbehandler,
                )
            },
        )
        serviceAndMocks.verifyNoMoreInteractions()
    }
}
