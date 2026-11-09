package tilbakekreving.application.service

import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.hendelser.DokumentHendelseRepo
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.dokumentUtenFil
import no.nav.su.se.bakover.test.hendelse.generertDokumentHendelse
import no.nav.su.se.bakover.test.hendelse.hendelseFil
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import person.domain.KunneIkkeHentePerson
import tilbakekreving.application.service.forhåndsvarsel.VisUtsendtForhåndsvarselbrevForTilbakekrevingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.forhåndsvarsel.KunneIkkeHenteUtsendtForhåndsvarsel
import tilbakekreving.domain.forhåndsvarsel.VisUtsendtForhåndsvarselbrevCommand
import tilgangstyring.application.TilgangstyringService
import tilgangstyring.domain.IkkeTilgangTilSak
import java.util.UUID

class VisUtsendtForhåndsvarselbrevForTilbakekrevingServiceTest {
    private val sakId = UUID.randomUUID()
    private val behandlingId = TilbakekrevingsbehandlingId.generer()
    private val dokumentId = UUID.randomUUID()
    private val command = VisUtsendtForhåndsvarselbrevCommand(sakId, behandlingId, dokumentId)
    private val dokument = generertDokumentHendelse(
        sakId = sakId,
        dokumentUtenFil = dokumentUtenFil(id = dokumentId, metadata = Dokument.Metadata(sakId = sakId)),
    )
    private val fil = hendelseFil(hendelseId = dokument.hendelseId)
    private val tilgangstyring = mock<TilgangstyringService> {
        on { assertHarTilgangTilSak(sakId) } doReturn Unit.right()
    }
    private val dokumentRepo = mock<DokumentHendelseRepo> {
        on { hentHendelseOgFilForDokumentId(dokumentId) } doReturn Pair(dokument, fil)
    }
    private val service = VisUtsendtForhåndsvarselbrevForTilbakekrevingService(
        dokumentHendelseRepo = dokumentRepo,
        tilgangstyring = tilgangstyring,
    )

    @Test
    fun `returnerer dokument på saken etter en sakstilgangssjekk`() {
        service.hent(command).shouldBeRight() shouldBe fil.fil

        inOrder(tilgangstyring, dokumentRepo) {
            verify(tilgangstyring).assertHarTilgangTilSak(sakId)
            verify(dokumentRepo).hentHendelseOgFilForDokumentId(dokumentId)
        }
        verifyNoMoreInteractions(tilgangstyring, dokumentRepo)
    }

    @Test
    fun `avviser manglende sakstilgang uten å hente dokumentet`() {
        val feil = IkkeTilgangTilSak(KunneIkkeHentePerson.IkkeTilgangTilPerson)
        whenever(tilgangstyring.assertHarTilgangTilSak(sakId)).thenReturn(feil.left())

        service.hent(command).shouldBeLeft() shouldBe KunneIkkeHenteUtsendtForhåndsvarsel.IkkeTilgang(feil)

        verify(tilgangstyring).assertHarTilgangTilSak(sakId)
        verifyNoMoreInteractions(tilgangstyring)
        verifyNoInteractions(dokumentRepo)
    }

    @Test
    fun `avviser dokument fra en annen sak selv om brukeren har tilgang til oppgitt sak`() {
        val annenSakId = UUID.randomUUID()
        whenever(dokumentRepo.hentHendelseOgFilForDokumentId(dokumentId))
            .thenReturn(Pair(dokument.copy(sakId = annenSakId), fil))

        service.hent(command).shouldBeLeft() shouldBe KunneIkkeHenteUtsendtForhåndsvarsel.FantIkkeDokument

        verify(tilgangstyring).assertHarTilgangTilSak(sakId)
        verify(dokumentRepo).hentHendelseOgFilForDokumentId(dokumentId)
        verifyNoMoreInteractions(tilgangstyring, dokumentRepo)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `returnerer ikke funnet når dokumenthendelsen eller filen mangler`(manglerHendelse: Boolean) {
        val dokumentOgFil = if (manglerHendelse) Pair(null, fil) else Pair(dokument, null)
        whenever(dokumentRepo.hentHendelseOgFilForDokumentId(dokumentId)).thenReturn(dokumentOgFil)

        service.hent(command).shouldBeLeft() shouldBe KunneIkkeHenteUtsendtForhåndsvarsel.FantIkkeDokument

        verify(tilgangstyring).assertHarTilgangTilSak(sakId)
        verify(dokumentRepo).hentHendelseOgFilForDokumentId(dokumentId)
        verifyNoMoreInteractions(tilgangstyring, dokumentRepo)
    }
}
