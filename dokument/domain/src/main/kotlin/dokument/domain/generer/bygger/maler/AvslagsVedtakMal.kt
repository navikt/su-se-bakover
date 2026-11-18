package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.BeregningsperiodeBrev
import dokument.domain.generer.bygger.Innhold
import dokument.domain.generer.bygger.SatsoversiktBrev
import dokument.domain.generer.bygger.beregning
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.fellesKlageinfo
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.fødselsnummerAvsnitt
import dokument.domain.generer.bygger.hilsen
import dokument.domain.generer.bygger.kroner
import dokument.domain.generer.bygger.punktliste
import no.nav.brev.brevbaker.markup.dsl.cell
import no.nav.brev.brevbaker.markup.dsl.column
import no.nav.brev.brevbaker.markup.dsl.header
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.row
import no.nav.brev.brevbaker.markup.dsl.table
import no.nav.brev.brevbaker.markup.outline.Block.Table.ColumnAlignment
import no.nav.brev.brevbaker.markup.outline.Text.FontType
import no.nav.su.se.bakover.common.domain.sak.Sakstype

data class AvslagSøknadsbehandlingPdfInnhold(
    val personalia: PersonaliaPdfInnhold,
    val avslagsgrunner: List<AvslagsgrunnBrev>,
    val avslagsparagrafer: List<Int>,
    val harFlereAvslagsgrunner: Boolean,
    val harEktefelle: Boolean,
    val halvGrunnbeløp: Int,
    val beregningsperioder: List<BeregningsperiodeBrev>,
    val saksbehandlerNavn: String,
    val attestantNavn: String,
    val fritekst: String,
    val forventetInntektStørreEnn0: Boolean?,
    val formueVerdier: FormueForBrev?,
    val satsoversikt: SatsoversiktBrev?,
    override val sakstype: Sakstype,
) : PdfInnhold {

    override val dokumentMal = DokumentMal.AvslagsVedtak
}

data class FormueForBrev(
    val søkersFormue: FormueVerdierForBrev,
    val epsFormue: FormueVerdierForBrev?,
    val totalt: Int,
)

data class FormueVerdierForBrev(
    val verdiSekundærBoliger: Int,
    val verdiSekundærKjøretøyer: Int,
    val pengerIBanken: Int,
    val depositumskonto: Int,
    val pengerIKontanter: Int,
    val aksjerOgVerdiPapir: Int,
    val pengerSøkerSkyldes: Int,
)

// Midlertidig duplisering av Avslagsgrunn.kt frem til vi løser modularisering av dokument/brev
enum class AvslagsgrunnBrev {
    UFØRHET,
    FLYKTNING,
    OPPHOLDSTILLATELSE,
    PERSONLIG_OPPMØTE,
    FORMUE,
    BOR_OG_OPPHOLDER_SEG_I_NORGE,
    FOR_HØY_INNTEKT,
    SU_UNDER_MINSTEGRENSE,
    UTENLANDSOPPHOLD_OVER_90_DAGER,
    INNLAGT_PÅ_INSTITUSJON,
    MANGLENDE_DOKUMENTASJON,
    SØKNAD_MANGLER_DOKUMENTASJON,
    ALDERSPENSJON_FOLKETRYGDEN,
    ALDERSPENSJON_ANDRE_NORSKE_PENSJONSORDNINGER,
    ALDERSPENSJON_UTENLANDSKE_PENSJONSORDNINGER,
    FAMILIEGJENFORENING,
}

val vedtakAvslag = { _: String, pdfInnhold: AvslagSøknadsbehandlingPdfInnhold ->
    val halvG = pdfInnhold.halvGrunnbeløp.kroner(true)
    dokument("Vi har avslått søknaden din om supplerende stønad", pdfInnhold.personalia) {
        fødselsnummerAvsnitt(pdfInnhold.personalia)
        paragraph("Vi har avslått fordi:")
        punktliste(
            pdfInnhold.avslagsgrunner.tilPunktliste(
                pdfInnhold.sakstype,
                pdfInnhold.harEktefelle,
                pdfInnhold.halvGrunnbeløp,
            ),
        )
        paragraph(
            "Vedtaket er gjort etter lov om supplerende stønad for personer " +
                "med kort botid i Norge ${avslagsparagrafer(pdfInnhold.avslagsparagrafer)}.",
        )
        if (pdfInnhold.fritekst.isNotEmpty()) fritekst(pdfInnhold.fritekst)
        if (pdfInnhold.avslagsgrunner.contains(AvslagsgrunnBrev.FORMUE)) formueTabell(pdfInnhold)
        if (pdfInnhold.beregningsperioder.isNotEmpty()) {
            beregning(
                sakstype = pdfInnhold.sakstype,
                satsoversikt = pdfInnhold.satsoversikt,
                stønadErRedusert = false,
                forventetInntektStørreEnn0 = pdfInnhold.forventetInntektStørreEnn0 == true,
                beregningsperioder = pdfInnhold.beregningsperioder,
            )
        }
        fellesKlageinfo(pdfInnhold.sakstype)
        hilsen(saksbehandler = pdfInnhold.saksbehandlerNavn, attestant = pdfInnhold.attestantNavn)
    }
}

private fun avslagsparagrafer(paragrafer: List<Int>): String = when (paragrafer.size) {
    0 -> ""
    1 -> "§ ${paragrafer.single()}"
    else -> "§§ ${paragrafer.dropLast(1).joinToString(", ")} og ${paragrafer.last()}"
}

private fun List<AvslagsgrunnBrev>.tilPunktliste(
    sakstype: Sakstype,
    harEktefelle: Boolean,
    halvGrunnbeløp: Int,
) = this.mapNotNull { grunn ->
    val halvG = halvGrunnbeløp.kroner(true)
    when (grunn) {
        AvslagsgrunnBrev.UFØRHET -> listOf("Du har ikke fått vedtak om uføretrygd der vilkårene i folketrygdloven §§ 12-4 til 12-7 er oppfylte.")
        AvslagsgrunnBrev.FLYKTNING -> listOf("Du har ikke status som flyktning etter § 28 i Utlendingsloven.")
        AvslagsgrunnBrev.OPPHOLDSTILLATELSE -> listOf(
            if (sakstype == Sakstype.ALDER) "Du har ikke varig lovlig opphold i Norge." else "Du har ikke lovlig opphold i Norge.",
        )

        AvslagsgrunnBrev.BOR_OG_OPPHOLDER_SEG_I_NORGE -> listOf("Du er ikke registrert bosatt i Norge.")
        AvslagsgrunnBrev.INNLAGT_PÅ_INSTITUSJON -> listOf("Du oppholder deg på institusjon.")
        AvslagsgrunnBrev.UTENLANDSOPPHOLD_OVER_90_DAGER -> listOf("Du skal oppholde deg utenfor Norge i mer enn 90 dager.")
        AvslagsgrunnBrev.FAMILIEGJENFORENING -> listOf(
            "Du har fått oppholdstillatelse i Norge på grunn av familiegjenforening med barn, barnebarn, nevø " +
                "eller niese. I det første vedtaket ditt om oppholdstillatelse ble det stilt krav til at den du søkte " +
                "gjenforening med, skulle forsørge deg. Selv om dette familiemedlemmet ikke lenger forsørger deg, har du likevel ikke rett til " +
                "supplerende stønad.",
        )

        AvslagsgrunnBrev.ALDERSPENSJON_FOLKETRYGDEN -> listOf(
            "Du utnytter ikke fullt ut din rett til alderspensjon fra folketrygden, " +
                "som er et krav for å kunne få rett til supplerende stønad. " +
                "Dersom du ikke har søkt om alderspensjon, kan du kontakte Nav for å få vurdert om det er aktuelt.",
        )

        AvslagsgrunnBrev.ALDERSPENSJON_ANDRE_NORSKE_PENSJONSORDNINGER -> listOf(
            "Du er registrert som medlem i en tjenestepensjonsordning. Vi har ikke mottatt informasjon om at " +
                "du har fått vedtak fra denne ordningen. Du må ha utnyttet retten til alle pensjoner i Norge og " +
                "utlandet for å få rett til supplerende stønad.",
        )

        AvslagsgrunnBrev.ALDERSPENSJON_UTENLANDSKE_PENSJONSORDNINGER -> listOf(
            "Du har ikke fått vedtak om pensjon fra utlandet, som er et krav for å få rett til supplerende stønad.",
        )

        AvslagsgrunnBrev.FORMUE -> listOf(
            (
                if (harEktefelle) {
                    "Formuen du og ektefellen/samboeren din har til sammen er mer enn halvparten av folketrygdens grunnbeløp. For tiden er dette $halvG kroner."
                } else {
                    "Formuen din er mer enn halvparten av grunnbeløpet, som for tiden er $halvG kroner."
                }
                ) + " Hvis formuen din blir mindre, kan du søke om supplerende stønad på nytt. " +
                "Du kan ikke gi bort formuen din for å få rett til supplerende stønad. " +
                "Derfor må du dokumentere hva du har brukt formuen til hvis du søker på nytt.",
        )

        AvslagsgrunnBrev.PERSONLIG_OPPMØTE -> listOf(
            "Du har ikke møtt personlig på Nav-kontoret, og har ikke dokumentert at du har vært for syk til å møte eller levert fullmakt som gir noen rett til å møte for deg.",
        )

        AvslagsgrunnBrev.FOR_HØY_INNTEKT -> listOf(
            (if (harEktefelle) "Inntekten du og ektefellen/samboeren din har til sammen er for høy." else "Du har for høy inntekt.") +
                " Inntekten er høyere enn det du kan få i supplerende stønad.",
        )

        AvslagsgrunnBrev.SU_UNDER_MINSTEGRENSE -> listOf(
            "Stønaden vi har beregnet til deg er for lav til at den kan utbetales. " +
                "Vi kan ikke utbetale stønad som er under to prosent av den høyeste stønadssatsen.",
        )

        AvslagsgrunnBrev.MANGLENDE_DOKUMENTASJON -> listOf(
            "Du har ikke sendt inn den dokumentasjonen vi har bedt om. Vi har derfor ikke nødvendig " +
                "informasjon for å kunne vurdere om du har rett til supplerende stønad.",
            "Hvis vi mottar den dokumentasjonen vi har bedt om innen klagefristen, vil vi vurdere søknaden på ny.",
        )

        AvslagsgrunnBrev.SØKNAD_MANGLER_DOKUMENTASJON -> null
    }
}

private fun Innhold.formueTabell(pdfInnhold: AvslagSøknadsbehandlingPdfInnhold) {
    val formueVerdier = pdfInnhold.formueVerdier ?: return
    val søker = formuerader(formueVerdier.søkersFormue, medKrForKjøretøyOgBolig = true)
    val eps = formueVerdier.epsFormue?.let { formuerader(it, medKrForKjøretøyOgBolig = false) }
    table {
        header {
            column("Din formue")
            column("", ColumnAlignment.RIGHT)
        }
        søker.forEach { (tekst, verdi) ->
            row {
                cell(tekst)
                cell(verdi)
            }
        }
        if (eps != null) {
            row {
                cell("Ektefelle/samboers formue", FontType.BOLD)
                cell("")
            }
            eps.forEach { (tekst, verdi) ->
                row {
                    cell(tekst)
                    cell(verdi)
                }
            }
        }
        row {
            cell("Totalt", FontType.BOLD)
            cell("${formueVerdier.totalt.kroner()} kr", FontType.BOLD)
        }
    }
}

private fun formuerader(formue: FormueVerdierForBrev, medKrForKjøretøyOgBolig: Boolean): List<Pair<String, String>> {
    val krBoligOgKjøretøy = if (medKrForKjøretøyOgBolig) " kr" else ""
    return listOf(
        Triple("Verdi på bolig, fritidsbolig eller tomt utover vanlig bolig", formue.verdiSekundærBoliger, krBoligOgKjøretøy),
        Triple("Verdi på sekundære kjøretøy", formue.verdiSekundærKjøretøyer, krBoligOgKjøretøy),
        Triple("Penger du har i banken", formue.pengerIBanken, " kr"),
        Triple("Depositumskonto", formue.depositumskonto, " kr"),
        Triple("Penger i kontanter", formue.pengerIKontanter, " kr"),
        Triple("Aksjer, aksjefond eller verdipapirer", formue.aksjerOgVerdiPapir, " kr"),
        Triple("Penger eller verdier noen skylder deg", formue.pengerSøkerSkyldes, " kr"),
    ).mapNotNull { (tekst, verdi, kr) ->
        if (verdi <= 0) return@mapNotNull null
        val minus = if (tekst == "Depositumskonto") "-" else ""
        tekst to "$minus${verdi.kroner()}$kr"
    }
}
