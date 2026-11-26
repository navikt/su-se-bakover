package dokument.domain.generer.bygger.maler

import dokument.domain.Brevtype
import dokument.domain.generer.Brevmal
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.Dokumenttype
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.PdfBestilling
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.fellesKlageinfo
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.fødselsnummerAvsnitt
import dokument.domain.generer.bygger.hilsen
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr

/** Saksbehandler avviser søknaden uten behandlingsflyt, med fritekst til bruker. */
data class AvvistSøknadDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekst: String,
) : BrevmalCommand {
    override val attestant: NavIdentBruker? get() = null
    override val mal: Brevmal<AvvistSøknadDokumentCommand> get() = avvistSøknadVedtakBrev
    override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
}

val avvistSøknadVedtakBrev = Brevmal<AvvistSøknadDokumentCommand>(
    dokumenttype = Dokumenttype.VEDTAK,
    brevtype = Brevtype.FRITEKST_VEDTAK,
) { felles, kommando ->
    AvvistSøknadVedtakPdfInnhold(
        sakstype = felles.sakstype,
        personalia = felles.personalia,
        saksbehandlerNavn = felles.saksbehandlerNavn,
        fritekst = kommando.fritekst,
    )
}

data class AvvistSøknadVedtakPdfInnhold(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val fritekst: String?,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Søknadsbehandling.AvvistSøknadVedtak
    override fun bestilling(): PdfBestilling = avvistSøknadVedtak(dokumentMal.tittel, this)
}

val avvistSøknadVedtak = { _: String, pdfInnhold: AvvistSøknadVedtakPdfInnhold ->
    val tittel = "Vi har avslått søknaden din om supplerende stønad"
    dokument(tittel, pdfInnhold.personalia) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        pdfInnhold.fritekst?.let { fritekst(it) }
        fellesKlageinfo(pdfInnhold.sakstype)
        hilsen(saksbehandler = pdfInnhold.saksbehandlerNavn)
    }
}
