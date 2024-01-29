package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast.BrevutkastForSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast.KunneIkkeGenerereBrevutkastForSøknadsbehandling
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.pdfATom
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class VilkårsvurderingerSøknadsbehandlingServiceBrevTest {
    private val tilAttesteringInnvilget = søknadsbehandlingTilAttesteringInnvilget().second
    private val uavklart = nySøknadsbehandlingMedStønadsperiode().second

    @Test
    fun `svarer med feil hvis vi ikke finner person`() {
        val brevServiceMock = mock<BrevService> {
            on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedHentingAvInformasjon.left()
        }
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn tilAttesteringInnvilget
        }

        SøknadsbehandlingServiceAndMocks(
            brevService = brevServiceMock,
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).let {
            it.søknadsbehandlingService.genererBrevutkast(
                BrevutkastForSøknadsbehandlingCommand.ForAttestant(
                    søknadsbehandlingId = tilAttesteringInnvilget.id,
                    utførtAv = attestant,
                ),
            ) shouldBe KunneIkkeGenerereBrevutkastForSøknadsbehandling.UnderliggendeFeil(
                KunneIkkeLageDokument.FeilVedHentingAvInformasjon,
            ).left()

            verify(it.brevService).lagDokument(any(), anyOrNull())
            verify(it.søknadsbehandlingRepo).hent(tilAttesteringInnvilget.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil hvis vi ikke finner navn på attestant eller saksbehandler`() {
        val brevServiceMock = mock<BrevService> {
            on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedHentingAvInformasjon.left()
        }
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn tilAttesteringInnvilget
        }
        SøknadsbehandlingServiceAndMocks(
            brevService = brevServiceMock,
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).let {
            it.søknadsbehandlingService.genererBrevutkast(
                BrevutkastForSøknadsbehandlingCommand.ForAttestant(
                    søknadsbehandlingId = tilAttesteringInnvilget.id,
                    utførtAv = attestant,
                ),
            ) shouldBe KunneIkkeGenerereBrevutkastForSøknadsbehandling.UnderliggendeFeil(
                KunneIkkeLageDokument.FeilVedHentingAvInformasjon,
            ).left()
            verify(it.brevService).lagDokument(any(), anyOrNull())
            verify(it.søknadsbehandlingRepo).hent(tilAttesteringInnvilget.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil hvis generering av pdf feiler`() {
        val underliggendeFeil = KunneIkkeLageDokument.FeilVedGenereringAvPdf
        val brevServiceMock = mock<BrevService> {
            on { lagDokument(any(), anyOrNull()) } doReturn underliggendeFeil.left()
        }
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn tilAttesteringInnvilget
        }

        SøknadsbehandlingServiceAndMocks(
            brevService = brevServiceMock,
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).let {
            it.søknadsbehandlingService.genererBrevutkast(
                BrevutkastForSøknadsbehandlingCommand.ForAttestant(
                    søknadsbehandlingId = tilAttesteringInnvilget.id,
                    utførtAv = attestant,
                ),
            ) shouldBe KunneIkkeGenerereBrevutkastForSøknadsbehandling.UnderliggendeFeil(underliggendeFeil).left()
            verify(it.brevService).lagDokument(any(), anyOrNull())
            verify(it.søknadsbehandlingRepo).hent(tilAttesteringInnvilget.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med PdfA dersom alt går fint`() {
        val generertDokument = pdfATom()
        val dokumentUtenMetadata = Dokument.UtenMetadata.Vedtak(
            opprettet = fixedTidspunkt,
            tittel = "tittel1",
            generertDokument = generertDokument,
            generertDokumentJson = "{}",
        )
        val brevServiceMock = mock<BrevService> {
            on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadata.right()
        }
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn tilAttesteringInnvilget
        }

        SøknadsbehandlingServiceAndMocks(
            brevService = brevServiceMock,
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).let {
            it.søknadsbehandlingService.genererBrevutkast(
                BrevutkastForSøknadsbehandlingCommand.ForAttestant(
                    søknadsbehandlingId = tilAttesteringInnvilget.id,
                    utførtAv = attestant,
                ),
            ) shouldBe Pair(generertDokument, tilAttesteringInnvilget.fnr).right()
            verify(it.brevService).lagDokument(
                argThat {
                    it shouldBe IverksettSøknadsbehandlingDokumentCommand.Innvilgelse(
                        fødselsnummer = tilAttesteringInnvilget.fnr,
                        saksnummer = tilAttesteringInnvilget.saksnummer,
                        beregning = tilAttesteringInnvilget.beregning,
                        harEktefelle = false,
                        forventetInntektStørreEnn0 = false,
                        saksbehandler = tilAttesteringInnvilget.saksbehandler,
                        attestant = attestant,
                        fritekst = "",
                        satsoversikt = Satsoversikt(
                            listOf(
                                Satsoversikt.Satsperiode(
                                    fraOgMed = "01.01.2021",
                                    tilOgMed = "31.12.2021",
                                    sats = "høy",
                                    satsBeløp = 20946,
                                    satsGrunn = "ENSLIG",
                                ),
                            ),
                        ),
                        sakstype = Sakstype.UFØRE,
                    )
                },
                anyOrNull(),
            )
            verify(it.søknadsbehandlingRepo).hent(tilAttesteringInnvilget.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kaster exception hvis det ikke er mulig å opprette brev for aktuell behandling`() {
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock<SøknadsbehandlingRepo> {
                on { hent(any()) } doReturn uavklart
            },
        ).let {
            it.søknadsbehandlingService.genererBrevutkast(
                BrevutkastForSøknadsbehandlingCommand.ForAttestant(
                    søknadsbehandlingId = uavklart.id,
                    utførtAv = attestant,
                ),
            ) shouldBe KunneIkkeGenerereBrevutkastForSøknadsbehandling.UgyldigTilstand(
                fra = VilkårsvurdertSøknadsbehandling.Uavklart::class,
            ).left()
            verify(it.søknadsbehandlingRepo).hent(uavklart.id)
            it.verifyNoMoreInteractions()
        }
    }
}
