package no.nav.su.se.bakover.domain.brev.dokumentMapper

import dokument.domain.Brevtype
import dokument.domain.Distribusjonstype
import dokument.domain.Dokument
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.domain.brev.command.FritekstDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.brev.pdfInnholdInnvilgetVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import java.util.UUID

class DokumentMapperKtTest {

    @Test
    fun `mapper fritekst dokument sin distribusjonstype til riktig dokument-type`() {
        val pdfA = PdfA("".toByteArray())
        pdfA.tilDokument(
            id = UUID.randomUUID(),
            pdfInnhold = pdfInnholdInnvilgetVedtak(),
            command = FritekstDokumentCommand(
                fødselsnummer = fnr,
                saksnummer = saksnummer,
                sakstype = Sakstype.UFØRE,
                saksbehandler = saksbehandler,
                brevTittel = "tittel",
                fritekst = "fritekst",
                distribusjonstype = Distribusjonstype.VEDTAK,
            ),
            clock = fixedClock,
        ).shouldBeTypeOf<Dokument.UtenMetadata.Vedtak>()

        pdfA.tilDokument(
            id = UUID.randomUUID(),
            pdfInnhold = pdfInnholdInnvilgetVedtak(),
            command = FritekstDokumentCommand(
                fødselsnummer = fnr,
                saksnummer = saksnummer,
                sakstype = Sakstype.UFØRE,
                saksbehandler = saksbehandler,
                brevTittel = "tittel",
                fritekst = "fritekst",
                distribusjonstype = Distribusjonstype.ANNET,
            ),
            clock = fixedClock,
        ).shouldBeTypeOf<Dokument.UtenMetadata.Informasjon.Annet>()
        pdfA.tilDokument(
            id = UUID.randomUUID(),
            pdfInnhold = pdfInnholdInnvilgetVedtak(),
            command = FritekstDokumentCommand(
                fødselsnummer = fnr,
                saksnummer = saksnummer,
                sakstype = Sakstype.UFØRE,
                saksbehandler = saksbehandler,
                brevTittel = "tittel",
                fritekst = "fritekst",
                distribusjonstype = Distribusjonstype.VIKTIG,
            ),
            clock = fixedClock,
        ).shouldBeTypeOf<Dokument.UtenMetadata.Informasjon.Viktig>()
    }

    @Test
    fun `mapper klage oppretthold eller delvis omgjøring til informasjon annet med OVERSENDELSE_KA`() {
        val dokument = PdfA("".toByteArray()).tilDokument(
            id = UUID.randomUUID(),
            pdfInnhold = pdfInnholdInnvilgetVedtak(),
            command = KlageDokumentCommand.OpprettholdEllerDelvisOmgjøring(
                fødselsnummer = fnr,
                saksnummer = saksnummer,
                sakstype = Sakstype.UFØRE,
                saksbehandler = saksbehandler,
                attestant = attestant,
                fritekst = "fritekst",
                klageDato = 15.januar(2021),
                vedtaksbrevDato = 1.januar(2021),
            ),
            clock = fixedClock,
        )

        dokument.shouldBeTypeOf<Dokument.UtenMetadata.Informasjon.Annet>()
        dokument.brevtype shouldBe Brevtype.OVERSENDELSE_KA
    }

    @Test
    fun `mapper avvist klage til vedtak med VEDTAK`() {
        val dokument = PdfA("".toByteArray()).tilDokument(
            id = UUID.randomUUID(),
            pdfInnhold = pdfInnholdInnvilgetVedtak(),
            command = KlageDokumentCommand.Avvist(
                fødselsnummer = fnr,
                saksnummer = saksnummer,
                sakstype = Sakstype.UFØRE,
                saksbehandler = saksbehandler,
                attestant = attestant,
                fritekst = "fritekst",
            ),
            clock = fixedClock,
        )

        dokument.shouldBeTypeOf<Dokument.UtenMetadata.Vedtak>()
        dokument.brevtype shouldBe Brevtype.VEDTAK
    }
}
