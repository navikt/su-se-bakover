package dokument.domain.generer

import arrow.core.left
import arrow.core.right
import dokument.domain.Brevtype
import dokument.domain.Dokument
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.generer.bygger.maler.AvvistSøknadVedtakPdfInnhold
import dokument.domain.generer.bygger.maler.avvistSøknadVedtakBrev
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LagBrevTest {

    private val fritekst = "fritekst"
    private val saksbehandlerNavn = "Sak Behandler"

    private val command = BrevmalCommand(
        mal = avvistSøknadVedtakBrev,
        fakta = fritekst,
        fødselsnummer = Fnr("12345678901"),
        saksnummer = Saksnummer(2021),
        sakstype = Sakstype.UFØRE,
        saksbehandler = saksbehandler,
        attestant = null,
    )

    private val felles = Felles(
        sakstype = Sakstype.UFØRE,
        personalia = PersonaliaPdfInnhold(
            dato = "01.01.2021",
            fødselsnummer = "12345678901",
            fornavn = "Tore",
            etternavn = "Strømøy",
            saksnummer = 2021,
        ),
        saksbehandlerNavn = saksbehandlerNavn,
        attestantNavn = "-",
    )

    private val pdf = PdfA("%PDF".toByteArray())
    private val id = UUID.randomUUID()

    @Test
    fun `lager vedtak med innhold, tittel og brevtype fra malen`() {
        val forventetInnhold = AvvistSøknadVedtakPdfInnhold(
            sakstype = Sakstype.UFØRE,
            personalia = felles.personalia,
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = fritekst,
        )
        var innholdSendtTilPdf: PdfInnhold? = null

        lagBrev(
            command = command,
            hentFelles = { felles.right() },
            lagPdf = {
                innholdSendtTilPdf = it
                pdf.right()
            },
            id = id,
            opprettet = fixedTidspunkt,
        ) shouldBe Dokument.UtenMetadata.Vedtak(
            id = id,
            opprettet = fixedTidspunkt,
            tittel = DokumentMal.Søknadsbehandling.AvvistSøknadVedtak.tittel,
            generertDokument = pdf,
            generertDokumentJson = forventetInnhold.toJson(),
            brevtype = Brevtype.FRITEKST_VEDTAK,
        ).right()

        innholdSendtTilPdf shouldBe forventetInnhold
    }

    @Test
    fun `stopper før PDF-generering når felles data ikke kan hentes`() {
        lagBrev(
            command = command,
            hentFelles = { KunneIkkeLageDokument.FeilVedHentingAvInformasjon.left() },
            lagPdf = { fail("lagPdf skal ikke kalles når hentFelles feiler") },
            id = id,
            opprettet = fixedTidspunkt,
        ) shouldBe KunneIkkeLageDokument.FeilVedHentingAvInformasjon.left()
    }

    @Test
    fun `gir feil når PDF-generering feiler`() {
        lagBrev(
            command = command,
            hentFelles = { felles.right() },
            lagPdf = { KunneIkkeLageDokument.FeilVedGenereringAvPdf.left() },
            id = id,
            opprettet = fixedTidspunkt,
        ) shouldBe KunneIkkeLageDokument.FeilVedGenereringAvPdf.left()
    }
}
