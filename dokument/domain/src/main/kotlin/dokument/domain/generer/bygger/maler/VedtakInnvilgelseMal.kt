package dokument.domain.generer.bygger.maler

import com.fasterxml.jackson.annotation.JsonInclude
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.Beregningsperiode
import dokument.domain.generer.bygger.Satsoversikt
import no.nav.su.se.bakover.common.domain.sak.Sakstype

// TODO Ta i bruk i mapping fra Command..
data class InnvilgetSøknadsbehandlingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val fradato: String,
    val tildato: String,
    val søkedato: String,
    val forventetInntektStørreEnn0: Boolean,
    val harEktefelle: Boolean,
    val beregningsperioder: List<Beregningsperiode>,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val fritekst: String,
    val satsoversikt: Satsoversikt,
    override val sakstype: Sakstype,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.InnvilgetVedtak

    @Suppress("unused")
    @JsonInclude
    val harFradrag: Boolean = beregningsperioder.harFradrag()
}
fun List<Beregningsperiode>.harFradrag(): Boolean {
    return this.any {
        // TODO jah: Rest av Avkorting. Brukes kun fra brev. Det skal egentlig ikke finnes nye avkortingsfradrag i nye behandlinger. Undersøk om vi kan fjerne denne filtreringen.
        it.fradrag.bruker.filterNot { fradrag -> fradrag.type == "Avkorting på grunn av tidligere utenlandsopphold" }
            .isNotEmpty() ||
            it.fradrag.eps.fradrag.isNotEmpty()
    }
}

val vedtakInnvilgelse = { tittel: String, pdfInnhold: KontrollnotatPdfInnholdNy ->
}
