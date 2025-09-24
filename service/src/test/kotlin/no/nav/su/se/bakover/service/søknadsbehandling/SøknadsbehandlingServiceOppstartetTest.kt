package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import behandling.søknadsbehandling.domain.KunneIkkeStarteSøknadsbehandling
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
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
import no.nav.su.se.bakover.test.nySøknadsbehandlingUtenStønadsperiode
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.søknad.nySakMedNySøknad
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknad.oppgaveIdSøknad
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdert
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

internal class SøknadsbehandlingServiceOppstartetTest {

    @Test
    fun `Starter behandling`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        val åpenBehandling = nySøknadsbehandlingUtenStønadsperiode(søknadId = søknad.id, saksbehandler = null).second

        // Skal kunne starte ny behandling selv om vi har iverksatte behandlinger.
        val eksisterendeSøknadsbehandlinger = listOf(
            søknadsbehandlingIverksattAvslagMedBeregning().second,
            søknadsbehandlingIverksattAvslagUtenBeregning().second,
            søknadsbehandlingIverksattInnvilget().second,
            åpenBehandling,
        )

        val capturedSøknadsbehandling = argumentCaptor<VilkårsvurdertSøknadsbehandling.Uavklart>()
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentForSak(any(), anyOrNull()) } doReturn eksisterendeSøknadsbehandlinger
            on { hentForSøknad(søknadId = søknad.id) } doReturn åpenBehandling
            doNothing().whenever(mock).lagre(capturedSøknadsbehandling.capture(), anyOrNull())
        }
        val oppgaveService = mock<OppgaveService> {
            on { oppdaterOppgave(any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
        }

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            oppgaveService = oppgaveService,
        )

        val (_, actualBehandling) = serviceAndMocks.søknadsbehandlingService.startBehandling(
            SøknadsbehandlingService.OppstartRequest(
                søknadId = søknad.id,
                sakId = sak.id,
                saksbehandler = saksbehandler,
            ),
        ).getOrFail()

        actualBehandling.shouldBeEqualToIgnoringFields(
            åpenBehandling.copy(
                saksbehandler = saksbehandler,
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
        verify(oppgaveService).oppdaterOppgave(any(), any())
        // serviceAndMocks.verifyNoMoreInteractions() TODO hva faen
    }

    @Test
    fun `svarer med feil dersom vi ikke finner søknadsbehandling eller søknad`() {
        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn nySakUføre(fixedClock).first.right()
            },
            søknadsbehandlingRepo = mock<SøknadsbehandlingRepo> {
                on { hentForSøknad(søknadId = any()) } doReturn null
            },
        ).also {
            shouldThrow<IllegalArgumentException> {
                it.søknadsbehandlingService.startBehandling(
                    SøknadsbehandlingService.OppstartRequest(
                        søknadId = UUID.randomUUID(),
                        sakId = sakId,
                        saksbehandler = saksbehandler,
                    ),
                )
            }.message shouldContain "Fant ikke søknad"
        }
    }

    @Test
    fun `svarer med feil dersom søknad ikke er journalført med oppgave`() {
        val (sak, søknad) = nySakMedNySøknad()

        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            søknadsbehandlingRepo = mock<SøknadsbehandlingRepo> {
                on { hentForSøknad(søknadId = any()) } doReturn null
            },
        ).also {
            it.søknadsbehandlingService.startBehandling(
                SøknadsbehandlingService.OppstartRequest(
                    søknadId = søknad.id,
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe KunneIkkeStarteSøknadsbehandling.ManglerOppgave.left()
        }
    }

    @Test
    fun `oppretter behandling som det fantes søknad uten søknadsbehandling fra før`() {
        val (sak, søknad) = nySakMedjournalførtSøknadOgOppgave()

        val capturedSøknadsbehandling = argumentCaptor<VilkårsvurdertSøknadsbehandling.Uavklart>()

        val oppgaveService = mock<OppgaveService> {
            on { oppdaterOppgave(any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
        }

        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            søknadsbehandlingRepo = mock<SøknadsbehandlingRepo> {
                on { hentForSøknad(søknadId = any()) } doReturn null
                doNothing().whenever(mock).lagre(capturedSøknadsbehandling.capture(), anyOrNull())
            },
            oppgaveService = oppgaveService,
        ).also {
            val actualBehandling = it.søknadsbehandlingService.startBehandling(
                SøknadsbehandlingService.OppstartRequest(
                    søknadId = søknad.id,
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                ),
            ).getOrNull()!!.second

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
                    omgjøringsårsak = null,
                    omgjøringsgrunn = null,
                ),
                // periode er null for VilkårsvurdertSøknadsbehandling.Uavklart og vil gi exception dersom man kaller get() på den.
                VilkårsvurdertSøknadsbehandling.Uavklart::periode,
            )
        }

        verify(oppgaveService).oppdaterOppgave(any(), any())
    }
}
