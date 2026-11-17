package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.fødselsnummerAvsnitt
import dokument.domain.generer.bygger.hilsen
import dokument.domain.generer.bygger.telefonnummer
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class TrukketSøknadPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val datoSøknadOpprettet: String,
    val trukketDato: String,
    val saksbehandlerNavn: String,
    override val sakstype: Sakstype,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.TrukketSøknad
}

val søknadTrukket = { _: String, pdfInnhold: TrukketSøknadPdfInnhold ->
    val tittel = "Søknaden din om supplerende stønad er trukket"
    dokument(tittel, pdfInnhold.personalia) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        paragraph(
            "Nav viser til søknaden din om supplerende stønad mottatt ${pdfInnhold.datoSøknadOpprettet}. " +
                "Du har i henvendelse av ${pdfInnhold.trukketDato} opplyst at du ønsker å trekke søknaden.",
        )
        paragraph(
            "Vi vil med dette bekrefte at søknaden om supplerende stønad er trukket, og ikke vil bli ytterligere behandlet.",
        )
        paragraph("Du må levere ny søknad hvis du på et senere tidspunkt likevel ønsker å søke supplerende stønad.")
        paragraph(
            "Supplerende stønad kan innvilges inntil 3 måneder forut for søknadstidspunktet hvis vilkårene var oppfylt i " +
                "hele denne perioden. Ved en ny søknad blir den nye søknaden utgangspunktet for det tidspunktet du kan " +
                "innvilges supplerende stønad fra.",
        )
        paragraph("Vi gjør oppmerksom på at du etter forvaltningsloven § 18 har rett til å se sakens dokumenter.")
        paragraph(
            "Skulle du ha spørsmål, er du velkommen til å kontakte oss på telefon ${telefonnummer(pdfInnhold.sakstype)}.",
        )
        paragraph("Husk at du også kan finne mer informasjon på våre hjemmesider nav.no.")
        hilsen(saksbehandler = pdfInnhold.saksbehandlerNavn)
    }
}
