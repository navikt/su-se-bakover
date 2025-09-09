package no.nav.su.se.bakover.service.sak

import arrow.core.right
import dokument.domain.Distribusjonstype
import dokument.domain.Dokument
import dokument.domain.DokumentRepo
import dokument.domain.GenererDokumentCommand
import dokument.domain.brev.BrevService
import dokument.domain.distribuering.Distribueringsadresse
import dokument.domain.journalføring.Journalpost
import dokument.domain.journalføring.QueryJournalpostClient
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.command.FritekstDokumentCommand
import no.nav.su.se.bakover.domain.sak.JournalførOgSendOpplastetPdfSomBrevCommand
import no.nav.su.se.bakover.domain.sak.OpprettDokumentRequest
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.dokumentUtenMetadataInformasjonAnnet
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fullstendigMedEPSUnder67UførFlyktning
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.sak.SakFakeRepo
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.søknad.nySakMedjournalførtSøknadOgOppgave
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.vilkår.formuevilkårMedEps0Innvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

internal class SakServiceImplTest {

    @Test
    fun `Oppretter sak og publiserer event`() {
        val sakId = UUID.randomUUID()
        val sak: Sak = mock {
            on { id } doReturn sakId
        }
        val sakRepo: SakRepo = mock {
            on { hentSak(any<UUID>()) } doReturn sak
        }

        val observer: StatistikkEventObserver = mock()

        val sakService = SakServiceImpl(sakRepo, fixedClock, mock(), mock(), mock(), mock())
        sakService.addObserver(observer)
        sakService.opprettSak(mock { on { id } doReturn sakId })

        verify(sakRepo).opprettSak(any())
        verify(sakRepo).hentSak(sak.id)
        verify(observer).handle(argThat { it shouldBe StatistikkEvent.SakOpprettet(sak) })
    }

    @Test
    fun `Publiserer ikke event ved feil av opprettelse av sak`() {
        val sakRepo: SakRepo = mock {
            on { opprettSak(any()) } doThrow RuntimeException("hehe exception")
        }

        val observer: StatistikkEventObserver = mock()

        val sakService = SakServiceImpl(sakRepo, fixedClock, mock(), mock(), mock(), mock())
        sakService.addObserver(observer)
        assertThrows<RuntimeException> {
            sakService.opprettSak(mock())
            verify(sakRepo).opprettSak(any())
            verifyNoMoreInteractions(sakRepo)
            verifyNoInteractions(observer)
        }
    }

    @Test
    fun `henter bare åpen søknad på en sak`() {
        val nySakMedjournalførtSøknadOgOppgave = nySakMedjournalførtSøknadOgOppgave().second
        val sakRepo: SakRepo = mock {
            on { hentÅpneBehandlinger() } doReturn listOf(
                Behandlingssammendrag(
                    saksnummer = Saksnummer(nummer = 2021),
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingssammendrag.Behandlingsstatus.NY_SØKNAD,
                    behandlingStartet = nySakMedjournalførtSøknadOgOppgave.opprettet,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo, fixedClock, mock(), mock(), mock(), mock())
        val sakMedÅpenSøknad = sakService.hentÅpneBehandlingerForAlleSaker()

        sakMedÅpenSøknad shouldBe listOf(
            Behandlingssammendrag(
                saksnummer = Saksnummer(nummer = 2021),
                behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                status = Behandlingssammendrag.Behandlingsstatus.NY_SØKNAD,
                behandlingStartet = nySakMedjournalførtSøknadOgOppgave.opprettet,
                periode = år(2021),
                sakType = Sakstype.UFØRE,
            ),
        )
    }

    @Test
    fun `henter bare åpne søknadsbehandlinger på en sak`() {
        val saksnr1 = Saksnummer(2021)
        val saksnr2 = Saksnummer(2022)

        val uavklartSøkandsbehandling = nySøknadsbehandlingMedStønadsperiode(saksnummer = saksnr1).second
        val underkjentSøknadsbehandling = søknadsbehandlingUnderkjentInnvilget(saksnummer = saksnr1).second
        val tilAttesteringSøknadsbehandling = søknadsbehandlingTilAttesteringInnvilget(saksnummer = saksnr2).second

        val sakRepo: SakRepo = mock {
            on { hentÅpneBehandlinger() } doReturn listOf(
                Behandlingssammendrag(
                    saksnummer = saksnr1,
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                    behandlingStartet = uavklartSøkandsbehandling.opprettet,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = saksnr1,
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingssammendrag.Behandlingsstatus.UNDERKJENT,
                    behandlingStartet = underkjentSøknadsbehandling.opprettet,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
                Behandlingssammendrag(
                    saksnummer = saksnr2,
                    behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                    status = Behandlingssammendrag.Behandlingsstatus.TIL_ATTESTERING,
                    behandlingStartet = tilAttesteringSøknadsbehandling.opprettet,
                    periode = år(2021),
                    sakType = Sakstype.UFØRE,
                ),
            )
        }

        val sakService = SakServiceImpl(sakRepo, fixedClock, mock(), mock(), mock(), mock())
        val sakerMedÅpneBehandlinger = sakService.hentÅpneBehandlingerForAlleSaker()

        sakerMedÅpneBehandlinger shouldBe listOf(
            Behandlingssammendrag(
                saksnummer = saksnr1,
                behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                status = Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
                behandlingStartet = uavklartSøkandsbehandling.opprettet,
                periode = år(2021),
                sakType = Sakstype.UFØRE,
            ),
            Behandlingssammendrag(
                saksnummer = saksnr1,
                behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                status = Behandlingssammendrag.Behandlingsstatus.UNDERKJENT,
                behandlingStartet = underkjentSøknadsbehandling.opprettet,
                periode = år(2021),
                sakType = Sakstype.UFØRE,
            ),
            Behandlingssammendrag(
                saksnummer = saksnr2,
                behandlingstype = Behandlingssammendrag.Behandlingstype.SØKNADSBEHANDLING,
                status = Behandlingssammendrag.Behandlingsstatus.TIL_ATTESTERING,
                behandlingStartet = tilAttesteringSøknadsbehandling.opprettet,
                periode = år(2021),
                sakType = Sakstype.UFØRE,
            ),
        )
    }

    @Test
    fun `oppretter fritekst dokument`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first
        val sakRepo: SakRepo = mock {
            on { hentSak(any<UUID>()) } doReturn sak
        }
        val brevService = mock<BrevService> {
            on {
                lagDokument(
                    any<GenererDokumentCommand>(),
                    anyOrNull(),
                )
            } doReturn dokumentUtenMetadataInformasjonAnnet(tittel = "test-dokument-informasjon-annet").right()
        }

        SakServiceImpl(sakRepo, fixedClock, mock(), brevService, mock(), mock())
            .genererFritekstbrevPåSak(
                request = OpprettDokumentRequest(
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                    tittel = "Brev tittel",
                    fritekst = "Brev fritekst",
                    distribueringsadresse = null,
                    distribusjonstype = Distribusjonstype.ANNET,
                ),
            ).shouldBeRight()
    }

    @Test
    fun `lagrer og sender fritekst dokument med adresse`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first
        val sakRepo: SakRepo = mock {
            on { hentSak(any<UUID>()) } doReturn sak
        }
        val expectedGenerertDokument = dokumentUtenMetadataInformasjonAnnet(tittel = "test-dokument-informasjon-annet")
        val brevService = mock<BrevService> {
            on {
                lagDokument(
                    any<GenererDokumentCommand>(),
                    anyOrNull(),
                )
            } doReturn expectedGenerertDokument.right()
        }
        val dokumentRepo = mock<DokumentRepo> {
            doNothing().whenever(it).lagre(any(), anyOrNull())
        }

        val actual = SakServiceImpl(sakRepo, fixedClock, dokumentRepo, brevService, mock(), mock())
            .genererLagreOgSendFritekstbrevPåSak(
                request = OpprettDokumentRequest(
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                    tittel = "tittel",
                    fritekst = "fritekst",
                    distribueringsadresse = Distribueringsadresse(
                        adresselinje1 = "adresselinje1",
                        adresselinje2 = "adresselinje2",
                        adresselinje3 = null,
                        postnummer = "postnummer",
                        poststed = "poststed",
                    ),
                    distribusjonstype = Distribusjonstype.ANNET,
                ),
            ).getOrFail()

        verify(sakRepo).hentSak(argThat<UUID> { it shouldBe sak.id })
        verify(brevService).lagDokument(
            argThat {
                it shouldBe FritekstDokumentCommand(
                    fødselsnummer = sak.fnr,
                    saksnummer = sak.saksnummer,
                    sakstype = Sakstype.UFØRE,
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
                    brevTittel = "tittel",
                    fritekst = "fritekst",
                    distribusjonstype = Distribusjonstype.ANNET,
                )
            },
            anyOrNull(),
        )
        verify(dokumentRepo).lagre(argThat { it shouldBe actual }, anyOrNull())

        verifyNoMoreInteractions(sakRepo)
        verifyNoMoreInteractions(brevService)
        verifyNoMoreInteractions(dokumentRepo)
    }

    @Test
    fun `lagrer og sender et fritekst dokument der saksbehandler har lastet opp pdf`() {
        val expecedSakId = UUID.randomUUID()
        val dokumentRepo = mock<DokumentRepo> {
            doNothing().whenever(it).lagre(any(), anyOrNull())
        }

        val actual =
            SakServiceImpl(SakFakeRepo(), fixedClock, dokumentRepo, mock(), mock(), mock()).lagreOgSendOpplastetPdfPåSak(
                request = JournalførOgSendOpplastetPdfSomBrevCommand(
                    sakId = expecedSakId,
                    saksbehandler = saksbehandler,
                    journaltittel = "Vedtaksbrev om nytt vedtak",
                    pdf = PdfA(content = "".toByteArray()),
                    distribueringsadresse = null,
                    distribusjonstype = Distribusjonstype.VEDTAK,
                ),
            )

        actual shouldBe Dokument.MedMetadata.Vedtak(
            utenMetadata = Dokument.UtenMetadata.Vedtak(
                // id'en blir generert på innsiden av opprettelsen av dokumentet
                id = actual.id,
                opprettet = fixedTidspunkt,
                tittel = "Vedtaksbrev om nytt vedtak",
                generertDokument = PdfA(content = "".toByteArray()),
                //language=json
                generertDokumentJson = """{"saksbehandler":"saksbehandler","journaltittel":"Vedtaksbrev om nytt vedtak","distribueringsadresse":null,"distribusjonstype":"VEDTAK","kommentar":"Pdf er lastet opp manuelt. Innholdet i brevet er ukjent"}""",
            ),
            metadata = Dokument.Metadata(sakId = expecedSakId),
            distribueringsadresse = null,
        )
    }

    @Test
    fun `henter alle journalposter`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first
        val sakRepo: SakRepo = mock {
            on { hentSakInfo(any<UUID>()) } doReturn SakInfo(sak.id, sak.saksnummer, sak.fnr, sak.type)
        }
        val queryJournalpostClient = mock<QueryJournalpostClient> {
            on { hentJournalposterFor(any(), any()) } doReturn listOf(
                Journalpost(JournalpostId("journalpostId"), "journalpost tittel"),
            ).right()
        }
        SakServiceImpl(sakRepo, fixedClock, mock(), mock(), queryJournalpostClient, mock())
            .hentAlleJournalposter(sak.id).shouldBeRight()

        verify(sakRepo).hentSakInfo(argThat<UUID> { it shouldBe sak.id })
        verify(queryJournalpostClient).hentJournalposterFor(argThat { it shouldBe sak.saksnummer }, eq(50))
    }

    @Test
    fun `kaster exception dersom sak ikke finnes ved henting av journalposter`() {
        val sak = nySakMedjournalførtSøknadOgOppgave().first
        val sakRepo: SakRepo = mock {
            on { hentSakInfo(any<UUID>()) } doReturn null
        }

        assertThrows<IllegalArgumentException> {
            SakServiceImpl(sakRepo, fixedClock, mock(), mock(), mock(), mock())
                .hentAlleJournalposter(sak.id)
        }
        verify(sakRepo).hentSakInfo(argThat<UUID> { it shouldBe sak.id })
    }

    @Test
    fun `henter saksIder for eps`() {
        val (epsSak) = nySakMedjournalførtSøknadOgOppgave(sakId = UUID.randomUUID(), fnr = Fnr.generer())
        val brukersSak = iverksattSøknadsbehandling(
            customVilkår = listOf(formuevilkårMedEps0Innvilget(epsFnr = epsSak.fnr)),
            customGrunnlag = listOf(fullstendigMedEPSUnder67UførFlyktning(fnr = epsSak.fnr)),
        ).first
        val sakRepo: SakRepo = mock {
            on { hentSak(any<UUID>()) } doReturn brukersSak
            on { hentSakInfo(any<Fnr>()) } doReturn listOf(epsSak.info())
        }
        SakServiceImpl(
            sakRepo,
            fixedClock,
            mock(),
            mock(),
            mock(),
            mock(),
        ).hentEpsSaksIderForBrukersSak(brukersSak.id) shouldBe listOf(epsSak.id)
    }
}
