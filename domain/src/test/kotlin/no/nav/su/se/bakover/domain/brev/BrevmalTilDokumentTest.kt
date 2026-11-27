package no.nav.su.se.bakover.domain.brev

import dokument.domain.Brevtype
import dokument.domain.Distribusjonstype
import dokument.domain.Dokument
import dokument.domain.generer.BrevmalCommand
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.domain.brev.command.FritekstDokumentCommand
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.brev.pdfInnholdInnvilgetVedtak
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import java.util.UUID

/** Dokumenttype og brevtype kommer fra malen som kommandoen peker på. */
internal class BrevmalTilDokumentTest {

    @Test
    fun `fritekstdokument får dokumenttype fra distribusjonstypen`() {
        tilDokument(fritekst(Distribusjonstype.VEDTAK)).shouldBeTypeOf<Dokument.UtenMetadata.Vedtak>()
        tilDokument(fritekst(Distribusjonstype.ANNET)).shouldBeTypeOf<Dokument.UtenMetadata.Informasjon.Annet>()
        tilDokument(fritekst(Distribusjonstype.VIKTIG)).shouldBeTypeOf<Dokument.UtenMetadata.Informasjon.Viktig>()
    }

    @Test
    fun `klage som opprettholdes eller delvis omgjøres blir informasjon annet med OVERSENDELSE_KA`() {
        val dokument = tilDokument(
            KlageDokumentCommand.OpprettholdEllerDelvisOmgjøring(
                fødselsnummer = fnr,
                saksnummer = saksnummer,
                sakstype = Sakstype.UFØRE,
                saksbehandler = saksbehandler,
                attestant = attestant,
                fritekst = "fritekst",
                klageDato = 15.januar(2021),
                vedtaksbrevDato = 1.januar(2021),
                dødsbo = false,
            ),
        )

        dokument.shouldBeTypeOf<Dokument.UtenMetadata.Informasjon.Annet>()
        dokument.brevtype shouldBe Brevtype.OVERSENDELSE_KA
    }

    @Test
    fun `avvist klage blir vedtak med VEDTAK`() {
        val dokument = tilDokument(
            KlageDokumentCommand.Avvist(
                fødselsnummer = fnr,
                saksnummer = saksnummer,
                sakstype = Sakstype.UFØRE,
                saksbehandler = saksbehandler,
                attestant = attestant,
                fritekst = "fritekst",
                dødsbo = false,
            ),
        )

        dokument.shouldBeTypeOf<Dokument.UtenMetadata.Vedtak>()
        dokument.brevtype shouldBe Brevtype.VEDTAK
    }

    private fun fritekst(distribusjonstype: Distribusjonstype) = FritekstDokumentCommand(
        fødselsnummer = fnr,
        saksnummer = saksnummer,
        sakstype = Sakstype.UFØRE,
        saksbehandler = saksbehandler,
        brevTittel = "tittel",
        fritekst = "fritekst",
        distribusjonstype = distribusjonstype,
    )

    private fun tilDokument(command: BrevmalCommand): Dokument.UtenMetadata = command.mal.tilDokument(
        pdf = PdfA("".toByteArray()),
        innhold = pdfInnholdInnvilgetVedtak(),
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
    )
}
