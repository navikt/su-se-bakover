package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.BrevPeriode
import dokument.domain.generer.bygger.Innhold
import dokument.domain.generer.bygger.SatsoversiktBrev
import dokument.domain.generer.bygger.beregning
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.fellesKlageinfo
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.fødselsnummerAvsnitt
import dokument.domain.generer.bygger.hilsen
import dokument.domain.generer.bygger.punktliste
import dokument.domain.generer.bygger.somOppramsing
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.su.se.bakover.common.domain.sak.Sakstype

enum class OpphørsgrunnBrev {
    UFØRHET,
    FOR_HØY_INNTEKT,
    SU_UNDER_MINSTEGRENSE,
    FORMUE,
    UTENLANDSOPPHOLD,
    MANGLENDE_DOKUMENTASJON,
    OPPHOLDSTILLATELSE,
    FLYKTNING,
    BOR_OG_OPPHOLDER_SEG_I_NORGE,
    PERSONLIG_OPPMØTE,
    INNLAGT_PÅ_INSTITUSJON,
    ALDERSPENSJON,
    FAMILIEGJENFORENING,
}

fun <T> List<T>.inneholderAlle(vararg elementer: T): Boolean =
    elementer.all { element -> this.any { listeElement: T -> listeElement == element } }

data class OpphørsvedtakPdfInnholdNy(
    override val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val opphørsgrunner: List<OpphørsgrunnBrev>,
    val avslagsparagrafer: List<Int>,
    val harEktefelle: Boolean,
    val beregningsperioder: List<BeregningsperiodeBrev>,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val fritekst: String?,
    val forventetInntektStørreEnn0: Boolean,
    val halvGrunnbeløp: Int,
    val opphørsperiode: BrevPeriode,
    val satsoversikt: SatsoversiktBrev,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Opphørsvedtak
}

val opphørsvedtak = { tittel: String, pdfInnhold: OpphørsvedtakPdfInnholdNy ->

    dokument(tittel, personalia = pdfInnhold.personalia) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        if (pdfInnhold.opphørsgrunner.inneholderAlle(OpphørsgrunnBrev.FAMILIEGJENFORENING)) {
            paragraph("Vi har opphørt stønaden din fordi oppholdstillatelsen din ikke gir deg rett til supplerende stønad.")
        } else {
            paragraph("Vi har opphørt fordi:")
            opphørsgrunner(pdfInnhold)
            paragraph(
                "Stønaden din blir opphørt i perioden ${pdfInnhold.opphørsperiode.fraOgMed}-${pdfInnhold.opphørsperiode.tilOgMed}.",
            )
            paragraph(
                "Vedtaket er gjort etter lov om supplerende stønad for personer " +
                    "med kort botid i Norge ${avslagsparagrafer(pdfInnhold.avslagsparagrafer)}.",
            )
        }

        pdfInnhold.fritekst?.let { tekst ->
            fritekst(tekst)
        }

        if (pdfInnhold.beregningsperioder.isNotEmpty()) {
            beregning(
                sakstype = pdfInnhold.sakstype,
                satsoversikt = pdfInnhold.satsoversikt,
                harFradrag = pdfInnhold.beregningsperioder.harFradrag(),
                forventetInntektStørreEnn0 = pdfInnhold.forventetInntektStørreEnn0,
                beregningsperioder = pdfInnhold.beregningsperioder,
            )
        }
        avslagBaseSlutt(pdfInnhold)
    }
}

private fun Innhold.avslagBaseSlutt(pdfInnhold: OpphørsvedtakPdfInnholdNy) {
    fellesKlageinfo(pdfInnhold.sakstype)
    hilsen(
        saksbehandler = pdfInnhold.saksbehandlerNavn,
        attestant = pdfInnhold.attestantNavn,
        visPostadresse = true,
    )
}

fun avslagsparagrafer(paragrafer: List<Int>): String {
    val tekster = paragrafer.map { paragraf -> paragraf.toString() }
    val prefiks = if (paragrafer.size > 1) "§§" else "§"
    return "$prefiks ${tekster.somOppramsing()}"
}

fun Innhold.opphørsgrunner(data: OpphørsvedtakPdfInnholdNy) {
    val harEktefelle = data.harEktefelle // data["harEktefelle"].sann
    val halvG = data.halvGrunnbeløp.toString() // data["halvGrunnbeløp"].tekst
    val punkter = data.opphørsgrunner.mapNotNull { grunn ->
        // data["opphørsgrunner"].liste.mapNotNull { grunn ->
        when (grunn) {
            OpphørsgrunnBrev.UFØRHET -> listOf("Vi har mottatt opplysninger om at uføretrygden din er opphørt etter paragrafene 12-4 til 12-7.")
            OpphørsgrunnBrev.FOR_HØY_INNTEKT -> listOf(
                if (harEktefelle) {
                    "Nye opplysninger viser at inntekten du og ektefellen/samboeren din har til sammen er for høy."
                } else {
                    "Nye opplysninger viser at du har for høy inntekt."
                },
            )
            OpphørsgrunnBrev.SU_UNDER_MINSTEGRENSE -> listOf(
                "Ny beregning av stønaden viser at den er for lav til at den kan utbetales. " +
                    "Vi kan ikke utbetale stønad som er under to prosent av den høyeste stønadssatsen.",
            )
            OpphørsgrunnBrev.UTENLANDSOPPHOLD -> listOf("Vi har opphørt fordi du har vært eller planlagt å være mer enn 90 dager i utlandet.")
            OpphørsgrunnBrev.FORMUE -> listOf(
                if (harEktefelle) {
                    "Nye opplysninger viser at formuen du og ektefellen/samboeren din har til sammen er mer enn halvparten av " +
                        "folketrygdens grunnbeløp. For tiden er dette $halvG kroner."
                } else {
                    "Nye opplysninger viser at formuen din er mer enn halvparten av folketrygdens grunnbeløp, som for tiden er $halvG kroner."
                },
                "Hvis formuen blir mindre, kan du søke om supplerende stønad på nytt. " +
                    "Du kan ikke gi bort formuen for å få rett til supplerende stønad. " +
                    "Derfor må du dokumentere hva formuen er brukt til hvis du søker på nytt.",
            )
            OpphørsgrunnBrev.MANGLENDE_DOKUMENTASJON -> listOf(
                "Du har ikke levert nødvending dokumentasjon for at vi kan vurdere om du fortsatt har rett til supplerende stønad.",
            )
            OpphørsgrunnBrev.FLYKTNING -> listOf(
                "Nye opplysninger viser at du ikke er registrert som flyktning etter utlendingslova §28. Vi har " +
                    "derfor opphørt din stønad som ufør flyktning.",
            )
            OpphørsgrunnBrev.OPPHOLDSTILLATELSE -> listOf("Nye opplysninger viser at du ikke har lovlig opphold i Norge.")
            OpphørsgrunnBrev.BOR_OG_OPPHOLDER_SEG_I_NORGE -> listOf("Nye opplysninger viser at du ikke oppholder deg fast i Norge.")
            OpphørsgrunnBrev.PERSONLIG_OPPMØTE -> listOf(
                "Nye opplysninger viser at du ikke møtte personlig når du søkte om supplerende stønad eller ved kontrollsamtale.",
            )
            OpphørsgrunnBrev.INNLAGT_PÅ_INSTITUSJON -> listOf("Nye opplysninger viser at du er innlagt på institusjon.")
            OpphørsgrunnBrev.ALDERSPENSJON -> listOf(
                "Du har ikke fullt ut benyttet deg av retten til pensjon fra folketrygden eller andre norske eller utenlandske ordninger.",
            )
            OpphørsgrunnBrev.FAMILIEGJENFORENING -> null
        }
    }
    punktliste(punkter)
}
