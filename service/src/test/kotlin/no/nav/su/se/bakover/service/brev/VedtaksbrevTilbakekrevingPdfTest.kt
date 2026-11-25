package no.nav.su.se.bakover.service.brev

import arrow.core.getOrElse
import arrow.core.nonEmptyListOf
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.kroner
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfByggerKlientImpl
import no.nav.su.se.bakover.service.brev.migrerTilPdfBygger.PdfTekst
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.vurderingerMedKrav
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tilbakekreving.domain.vedtaksbrev.VedtaksbrevTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.vedtaksbrev.fromBrevCommand
import tilbakekreving.domain.vurdering.Vurdering
import tilbakekreving.domain.vurdering.Vurderinger
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertFalse

@ExtendWith(PdfByggerExtension::class)
internal class VedtaksbrevTilbakekrevingPdfTest(private val klient: PdfByggerKlientImpl) {

    private val personalia = PersonaliaPdfInnhold(
        dato = "01.01.2020",
        fødselsnummer = "12345678901",
        fornavn = "Leeroy",
        etternavn = "Jenkins",
        saksnummer = 2021L,
    )

    private val skalTilbakekreveCommand = VedtaksbrevTilbakekrevingsbehandlingDokumentCommand(
        fødselsnummer = Fnr("12345678901"),
        saksnummer = Saksnummer(2021L),
        correlationId = CorrelationId.generate(),
        sakId = UUID.randomUUID(),
        sakstype = Sakstype.UFØRE,
        saksbehandler = NavIdentBruker.Saksbehandler("Malenia, blade of Miquella"),
        attestant = NavIdentBruker.Attestant("James Eugene Raynor"),
        fritekst = "Her kommer det en lang fritekst om hvorfor du må betale tilbake.",
        vurderingerMedKrav = vurderingerMedKrav(
            perioderVurderinger = nonEmptyListOf(
                Vurderinger.Periodevurdering(
                    periode = januar(2021),
                    vurdering = Vurdering.SkalTilbakekreve,
                ),
            ),
        ),
        skalTilbakekreve = true,
        dødsbo = false,
    )

    private val skalIkkeTilbakekreveCommand = VedtaksbrevTilbakekrevingsbehandlingDokumentCommand(
        fødselsnummer = Fnr("12345678901"),
        saksnummer = Saksnummer(2021L),
        correlationId = CorrelationId.generate(),
        sakId = UUID.randomUUID(),
        sakstype = Sakstype.UFØRE,
        saksbehandler = NavIdentBruker.Saksbehandler("Malenia, blade of Miquella"),
        attestant = NavIdentBruker.Attestant("James Eugene Raynor"),
        fritekst = "Her kommer det en lang fritekst om hvorfor du ikke må betale tilbake.",
        vurderingerMedKrav = vurderingerMedKrav(
            perioderVurderinger = nonEmptyListOf(
                Vurderinger.Periodevurdering(
                    periode = januar(2021),
                    vurdering = Vurdering.SkalIkkeTilbakekreve,
                ),
            ),
        ),
        skalTilbakekreve = false,
        dødsbo = false,
    )

    @Test
    fun `skal generere riktig pdf for vedtaksbrev tilbakekreving når bruker skal betale tilbake`() {
        val pdfTekst = genererOgVerifiserSkalTilbakekrevePdf(dødsbo = false)

        assertContains(pdfTekst, "Du må betale tilbake supplerende stønad")
        assertContains(pdfTekst, "Du har rett til innsyn")
        assertContains(pdfTekst, "Vedlegg Informasjon om klage og anke")
        assertFalse(pdfTekst.contains("Boet"))
    }

    @Test
    fun `skal generere riktig pdf for vedtaksbrev tilbakekreving med dødsbo`() {
        val pdfTekst = genererOgVerifiserSkalTilbakekrevePdf(dødsbo = true)

        assertContains(pdfTekst, "Boet må betale tilbake supplerende stønad")
        assertContains(pdfTekst, "Boet har rett til innsyn")
        assertFalse(pdfTekst.contains("Du har rett til innsyn"))
        assertFalse(pdfTekst.contains("Vedlegg Informasjon om klage og anke"))
    }

    @Test
    fun `skal generere riktig pdf for vedtaksbrev tilbakekreving når bruker ikke skal betale tilbake`() {
        val pdfTekst = genererOgVerifiserSkalIkkeTilbakekrevePdf(dødsbo = false)

        assertContains(pdfTekst, "Du må ikke betale tilbake supplerende stønad")
        assertContains(pdfTekst, "Du har rett til innsyn")
        assertFalse(pdfTekst.contains("Vedlegg Informasjon om klage og anke"))
        assertFalse(pdfTekst.contains("Vedlegg Oversikt over feilutbetalingen"))
        assertFalse(pdfTekst.contains("Boet"))
    }

    private fun genererOgVerifiserSkalTilbakekrevePdf(dødsbo: Boolean): String {
        val pdfInnhold = skalTilbakekreveCommand
            .copy(dødsbo = dødsbo)
            .fromBrevCommand(
                personalia = personalia,
                saksbehandlerNavn = "Malenia, blade of Miquella",
                attestantNavn = "James Eugene Raynor",
                clock = fixedClock,
            )
        val bestilling = requireNotNull(pdfInnhold.tilPdfBestilling())
        val nyPdf = klient.lagPdf(bestilling).getOrElse {
            throw IllegalStateException("PdfBygger feilet")
        }
        val pdfTekst = PdfTekst.ord(nyPdf).joinToString(" ") { it.ord }

        fun normalisert(verdi: String) = PdfTekst.innholdsord(verdi).joinToString(" ")
        assertContains(pdfTekst, normalisert(requireNotNull(pdfInnhold.fritekst)))
        assertContains(pdfTekst, normalisert(pdfInnhold.månedsoversiktMedSum.sumBruttoSkalTilbakekreve.kroner(utenDesimaler = true)))
        assertContains(pdfTekst, normalisert(pdfInnhold.månedsoversiktMedSum.sumNettoSkalTilbakekreve.kroner(utenDesimaler = true)))
        pdfInnhold.månedsoversiktMedSum.sorterteMåneder.forEach {
            assertContains(pdfTekst, normalisert(it.periode))
        }
        assertContains(pdfTekst, "Vedlegg Oversikt over feilutbetalingen")
        assertContains(pdfTekst, normalisert(pdfInnhold.saksbehandlerNavn))
        assertContains(pdfTekst, normalisert(requireNotNull(pdfInnhold.attestantNavn)))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.fornavn))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.etternavn))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.fødselsnummer))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.saksnummer.toString()))

        return pdfTekst
    }

    private fun genererOgVerifiserSkalIkkeTilbakekrevePdf(dødsbo: Boolean): String {
        val pdfInnhold = skalIkkeTilbakekreveCommand
            .copy(dødsbo = dødsbo)
            .fromBrevCommand(
                personalia = personalia,
                saksbehandlerNavn = "Malenia, blade of Miquella",
                attestantNavn = "James Eugene Raynor",
                clock = fixedClock,
            )
        val bestilling = requireNotNull(pdfInnhold.tilPdfBestilling())
        val nyPdf = klient.lagPdf(bestilling).getOrElse {
            throw IllegalStateException("PdfBygger feilet")
        }
        val pdfTekst = PdfTekst.ord(nyPdf).joinToString(" ") { it.ord }

        fun normalisert(verdi: String) = PdfTekst.innholdsord(verdi).joinToString(" ")
        assertContains(pdfTekst, normalisert(requireNotNull(pdfInnhold.fritekst)))
        assertContains(pdfTekst, normalisert(pdfInnhold.bruttoSkalIkkeTilbakekreveSummert.kroner(utenDesimaler = true)))
        assertContains(pdfTekst, normalisert(pdfInnhold.saksbehandlerNavn))
        assertContains(pdfTekst, normalisert(requireNotNull(pdfInnhold.attestantNavn)))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.fornavn))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.etternavn))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.fødselsnummer))
        assertContains(pdfTekst, normalisert(pdfInnhold.personalia.saksnummer.toString()))

        return pdfTekst
    }
}
