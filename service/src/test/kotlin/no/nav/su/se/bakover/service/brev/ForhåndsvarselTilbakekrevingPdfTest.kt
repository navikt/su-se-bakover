package no.nav.su.se.bakover.service.brev

import arrow.core.getOrElse
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.kroner
import dokument.domain.generer.bygger.maler.ForhåndsvarselTilbakekrevingPdfInnhold
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfByggerKlientImpl
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.PdfTekst
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.kravgrunnlag.kravgrunnlag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.forhåndsvarsel.fromBrevCommand
import java.util.UUID
import kotlin.test.assertContains

@ExtendWith(PdfByggerExtension::class)
internal class ForhåndsvarselTilbakekrevingPdfTest(private val klient: PdfByggerKlientImpl) {

    private val personalia = PersonaliaPdfInnhold(
        dato = "01.01.2020",
        fødselsnummer = "12345678901",
        fornavn = "Leeroy",
        etternavn = "Jenkins",
        saksnummer = 2021L,
    )

    private val command = ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand(
        fødselsnummer = Fnr("12345678901"),
        saksnummer = Saksnummer(2021L),
        correlationId = CorrelationId.generate(),
        sakId = UUID.randomUUID(),
        sakstype = Sakstype.UFØRE,
        saksbehandler = NavIdentBruker.Saksbehandler("Malenia, blade of Miquella"),
        fritekst = "Her kommer det en lang fritekst om hva som har skjedd.",
        kravgrunnlag = kravgrunnlag(saksnummer = Saksnummer(2021L)),
        dødsbo = false,
    )

    @Test
    fun `skal generere pdf for forhåndsvarsel tilbakekreving rettet mot bruker`() {
        val pdfInnhold = command.tilPdfInnhold()
        val pdfTekst = lagPdfTekst(pdfInnhold)
        verifiserAtDataErBruktIPdf(pdfInnhold, pdfTekst)

        assertContains(pdfTekst.filter { it.isWhitespace() }, "Varsel om at Nav vurderer å kreve tilbake feilutbetalt supplerende stønad".filter { it.isWhitespace() })
        val sumBruttoSkalTilbakekreve = requireNotNull(pdfInnhold.kravgrunnlag).sumBruttoSkalTilbakekreve.kroner(utenDesimaler = true)
        assertContains(
            pdfTekst,
            "Beløpet er ${normalisert(sumBruttoSkalTilbakekreve)} kroner før skatt.",
        )
        assertContains(pdfTekst, "Slik kan du uttale deg")
        pdfTekst shouldNotContain "Boet"
    }

    @Test
    fun `skal generere pdf for forhåndsvarsel tilbakekreving rettet mot dødsbo`() {
        val pdfInnhold = command.copy(
            dødsbo = true,
        ).tilPdfInnhold()
        val pdfTekst = lagPdfTekst(pdfInnhold)
        verifiserAtDataErBruktIPdf(pdfInnhold, pdfTekst)

        assertContains(pdfTekst, "Vi vurderer om boet må betale tilbake supplerende stønad")
        assertContains(
            pdfTekst,
            normalisert(
                "Avdøde ${pdfInnhold.personalia.fornavn} ${pdfInnhold.personalia.etternavn} har fått utbetalt " +
                    "${requireNotNull(pdfInnhold.kravgrunnlag).sumBruttoSkalTilbakekreve.kroner(utenDesimaler = true)} kroner for mye",
            ),
        )
        assertContains(pdfTekst, "Slik kan boet komme med en uttalelse")
        pdfTekst shouldNotContain "Slik kan du uttale deg"
    }

    @Test
    fun `skal generere pdf for forhåndsvarsel tilbakekreving til bruker uten kravgrunnlag`() {
        val pdfInnhold = command.copy(
            kravgrunnlag = null,
        ).tilPdfInnhold()
        val pdfTekst = lagPdfTekst(pdfInnhold)
        assertContains(pdfTekst, requireNotNull(pdfInnhold.fritekst))
        assertContains(pdfTekst, pdfInnhold.saksbehandlerNavn)
        pdfTekst shouldNotContain "Beløpet er"
        pdfTekst shouldNotContain "Vedlegg Beregning av feilutbetalt beløp"
    }

    private fun verifiserAtDataErBruktIPdf(pdfInnhold: ForhåndsvarselTilbakekrevingPdfInnhold, pdfTekst: String) {
        val kravgrunnlag = requireNotNull(pdfInnhold.kravgrunnlag)
        pdfTekst shouldContain normalisert(kravgrunnlag.sumBruttoSkalTilbakekreve.kroner(utenDesimaler = true))
        assertContains(pdfTekst, normalisert(requireNotNull(pdfInnhold.fritekst)))
        assertContains(pdfTekst, "Vedlegg: Beregning av feilutbetalt beløp")
        kravgrunnlag.beregningFeilutbetaltBeløp.forEach {
            assertContains(pdfTekst, it.periode)
            assertContains(pdfTekst, normalisert(it.bruttoSkalTilbakekreve.kroner(utenDesimaler = true)))
        }

        assertContains(pdfTekst, pdfInnhold.saksbehandlerNavn)
        assertContains(pdfTekst, pdfInnhold.personalia.fornavn)
        assertContains(pdfTekst, pdfInnhold.personalia.etternavn)
        assertContains(pdfTekst, StringBuilder(pdfInnhold.personalia.fødselsnummer).insert(6, " ").toString())
        assertContains(pdfTekst, pdfInnhold.personalia.saksnummer.toString())
    }

    private fun ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand.tilPdfInnhold() =
        fromBrevCommand(
            personalia = personalia,
            saksbehandlerNavn = "Malenia, blade of Miquella",
            clock = fixedClock,
        ) as ForhåndsvarselTilbakekrevingPdfInnhold

    private fun lagPdfTekst(pdfInnhold: ForhåndsvarselTilbakekrevingPdfInnhold): String {
        val bestilling = requireNotNull(pdfInnhold.tilPdfBestilling())
        val nyPdf = klient.lagPdf(bestilling).getOrElse {
            throw IllegalStateException("PdfBygger feilet")
        }
        return PdfTekst.tekst(nyPdf)
    }

    private fun normalisert(verdi: String) = PdfTekst.innholdsord(verdi).joinToString(" ")
}
