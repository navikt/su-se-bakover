package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.SatsoversiktBrev
import dokument.domain.generer.bygger.beregning
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.fellesKlageinfo
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.fødselsnummerAvsnitt
import dokument.domain.generer.bygger.hilsen
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class RevurderingAvInntektPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val beregningsperioder: List<BeregningsperiodeBrev>,
    val fritekst: String?,
    val harEktefelle: Boolean,
    val forventetInntektStørreEnn0: Boolean,
    val satsoversikt: SatsoversiktBrev,
    val harFradrag: Boolean,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Revurdering.Inntekt
}

val revurderingAvInntekt = { tittel: String, pdfInnhold: RevurderingAvInntektPdfInnhold ->
    dokument(tittel, personalia = pdfInnhold.personalia) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        pdfInnhold.fritekst?.let { fritekst(it) }
        beregning(
            sakstype = pdfInnhold.sakstype,
            satsoversikt = pdfInnhold.satsoversikt,
            stønadErRedusert = pdfInnhold.harFradrag,
            forventetInntektStørreEnn0 = pdfInnhold.forventetInntektStørreEnn0,
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
