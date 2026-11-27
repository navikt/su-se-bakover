package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.PdfBestilling
import dokument.domain.generer.bygger.SatsoversiktBrev
import dokument.domain.generer.bygger.beregning
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.fellesKlageinfo
import dokument.domain.generer.bygger.fødselsnummerAvsnitt
import dokument.domain.generer.bygger.hilsen
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.title2
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class VedtakVedReguleringPdfInnhold(
    override val sakstype: Sakstype,
    val fraOgMed: String,
    val alderEndretFraOgMed: String,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val beregningsperioder: List<BeregningsperiodeBrev>,
    val satsoversikt: SatsoversiktBrev,
    val attestantNavn: String,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.VedtaksbrevReguleringAldersfradrag
    override fun bestilling(): PdfBestilling = vedtaksbrevReguleringAldersfradrag(dokumentMal.tittel, this)
}

/**
 * KLADD, ikke godkjent av fag. Teksten er overført fra su-pdfgen-malen `vedtaksbrevReguleringAldersfradrag.hbs`.
 *
 * Avvik fra su-pdfgen som faget må vurdere: beregningen vises med satsoversikt og eventuell tekst om
 * redusert stønad, slik som i revurdering av inntekt. I su-pdfgen fikk delmalen for beregning bare listen
 * over perioder, så satsoversikten ble ikke vist der.
 */
val vedtaksbrevReguleringAldersfradrag = { _: String, pdfInnhold: VedtakVedReguleringPdfInnhold ->
    dokument("Vi har vurdert din supplerende stønad på nytt", pdfInnhold.personalia) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        paragraph(
            "Alderspensjonen din økte fra ${pdfInnhold.alderEndretFraOgMed} fordi du har fått økt minstepensjon. " +
                "Den supplerende stønaden din er derfor omregnet fra ${pdfInnhold.fraOgMed}, måneden etter at " +
                "alderspensjonen din økte.",
        )
        paragraph(
            "Etter forvaltningsloven § 16 skal en part som ikke allerede har uttalt seg i saken, enten ved søknad " +
                "eller på en annen måte, varsles om revurdering i forkant av vedtak. Varsling kan unnlates hvis det " +
                "anses åpenbart unødvendig. Fordi vurderingen vår ikke vil kunne endres på bakgrunn av eventuelle " +
                "tilbakemeldinger fra deg, er varsling unnlatt.",
        )
        title2("Begrunnelse")
        paragraph(
            "Hvis inntekten din endrer seg i stønadsperioden, skal den supplerende stønaden din endres hvis " +
                "inntektsendringen gjør at du får minst 10 prosent endring i utbetalt supplerende stønad.",
        )
        paragraph(
            "Hvis inntekten øker, skal den supplerende stønaden omregnes fra måneden etter at inntekten økte. " +
                "Stønaden skal omregnes fra samme måned som inntektsendringen hvis inntekten din blir redusert.",
        )
        paragraph("Økningen i alderspensjonen din medfører minst 10 prosent endring i utbetalt supplerende stønad.")
        paragraph(
            "Den supplerende stønaden din er derfor endret fra ${pdfInnhold.fraOgMed}. Vedtaket er gjort etter lov " +
                "om supplerende stønad til personer med kort botid i Norge §§ 10 og 11 tredje ledd.",
        )
        beregning(
            sakstype = pdfInnhold.sakstype,
            satsoversikt = pdfInnhold.satsoversikt,
            stønadErRedusert = pdfInnhold.beregningsperioder.any {
                it.fradrag.bruker.isNotEmpty() || it.fradrag.eps.fradrag.isNotEmpty()
            },
            forventetInntektStørreEnn0 = false,
            beregningsperioder = pdfInnhold.beregningsperioder,
        )
        fellesKlageinfo(pdfInnhold.sakstype)
        hilsen(
            saksbehandler = pdfInnhold.saksbehandlerNavn,
            attestant = pdfInnhold.attestantNavn,
            visPostadresse = true,
        )
    }
}
