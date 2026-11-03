package dokument.domain.generer.bygger.maler

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.bygger.Innhold
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.linjer
import no.nav.brev.brevbaker.markup.dsl.ContentBuilder
import no.nav.brev.brevbaker.markup.dsl.cell
import no.nav.brev.brevbaker.markup.dsl.column
import no.nav.brev.brevbaker.markup.dsl.header
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.row
import no.nav.brev.brevbaker.markup.dsl.table
import no.nav.brev.brevbaker.markup.outline.Text.FontType
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import person.domain.Person

data class KontrollnotatPdfInnholdNy(
    override val sakstype: Sakstype,
    val saksnummer: Saksnummer,
    val navn: Person.Navn,
    val dagensDatoOgTidspunkt: String,

    val personligOppmøte: Boolean,
    val fullmaktOgLegeerklæring: Boolean?,
    val originalPass: Boolean,
    val gyldigPass: Boolean,
    val harVærtUtenlands: Boolean,
    val utenlandsoppholdDatoer: List<String>,
    val harPlanerOmUtenlandsreise: Boolean,
    val planlagteUtenlandsreiseDatoer: List<String>,
    val reiseDokumentasjon: Boolean,
    val økonomiskSituasjon: Boolean,
    val andreForhold: Boolean,
    val skatteOpplysninger: Boolean,
    val fritekst: String?,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Kontrollnotat
}

val kontrollnotat = { tittel: String, pdfInnhold: PdfInnhold ->
    pdfInnhold as KontrollnotatPdfInnholdNy // TODO midlertidig hack frem til jeg finner en bedre typing..
    dokument(tittel, personalia = null) {
        table {
            header {
                column("Kontrollnotat")
                column("")
            }
            row {
                cell { feltMedVerdi("Navn:", navn(pdfInnhold.navn)) }
                cell { feltMedVerdi("Saksnummer:", pdfInnhold.saksnummer.toString()) }
            }
            row {
                cell { feltMedVerdi("Dato og tidspunkt:", pdfInnhold.dagensDatoOgTidspunkt) }
                cell("")
            }
        }
        kontrollseksjon(
            tittel = "Personlig oppmøte",
            rader = listOf("Har bruker møtt personlig?" to svar(jaNei(pdfInnhold.personligOppmøte))),
        )
        kontrollseksjon(
            tittel = "Fullmakt og legeerklæring",
            rader = listOf(
                "Foreligger det fullmakt og legeerklæring som bekrefter at sykdom gjør at bruker ikke kan møte personlig?" to
                    svar(jaNei(pdfInnhold.fullmaktOgLegeerklæring)),
            ),
        )
        kontrollseksjon(
            tittel = "Kontroll av reisedokumenter",
            rader = listOf(
                "Har bruker framvist originalt pass og eventuelle andre originale reisedokumenter?" to svar(
                    jaNei(
                        pdfInnhold.originalPass,
                    ),
                ),
                "Er det nye stempler i passet siden forrige møte?" to svar(jaNei(pdfInnhold.gyldigPass)),
            ),
        )
        kontrollseksjon(
            tittel = "Reise til utlandet",
            rader = buildList {
                add("Har bruker vært utenlands siden siste møte?" to svar(jaNei(pdfInnhold.harVærtUtenlands)))
                if (pdfInnhold.harVærtUtenlands) {
                    add(
                        "Registrerte utenlandsopphold" to datoliste(
                            datoer = pdfInnhold.utenlandsoppholdDatoer,
                            hvisIngenTekst = "Ingen registrerte utenlandsopphold",
                        ),
                    )
                }
                add("Har bruker planer om å reise utenlands?" to svar(jaNei(pdfInnhold.harPlanerOmUtenlandsreise)))
                if (pdfInnhold.harPlanerOmUtenlandsreise) {
                    add(
                        "Planlagte reisedatoer" to datoliste(
                            datoer = pdfInnhold.planlagteUtenlandsreiseDatoer,
                            hvisIngenTekst = "Ingen datoer registrert",
                        ),
                    )
                    add(
                        "Har bruker fått beskjed om at reisedokumenter, billetter/boardingpass må tas vare på?" to
                            svar(jaNei(pdfInnhold.reiseDokumentasjon)),
                    )
                }
            },
        )
        kontrollseksjon(
            tittel = "Økonomisk situasjon",
            rader = listOf(
                "Er det endringer i den økonomiske situasjonen for bruker eller ektefelle/partner/samboer?" to
                    svar(jaNei(pdfInnhold.økonomiskSituasjon)),
            ),
        )
        kontrollseksjon(
            "Andre forhold",
            listOf(
                "Er det endringer i andre forhold av betydning for stønaden (f.eks. sivilstand, institusjonsopphold, oppholdstillatelse)?" to
                    svar(jaNei(pdfInnhold.andreForhold)),
            ),
        )
        kontrollseksjon(
            "Skatteopplysninger",
            listOf(
                "Er siste skattemelding og grunnlag for skatt for bruker og eventuell ektefelle/partner/samboer levert?" to
                    svar(jaNei(pdfInnhold.skatteOpplysninger)),
            ),
        )
        table {
            header { column("Kommentar") }
            row {
                cell {
                    if (pdfInnhold.fritekst != null) {
                        linjer(
                            pdfInnhold.fritekst.replace(
                                "\r\n",
                                "\n",
                            ),
                        )
                    } else {
                        text("Ingen kommentar registrert")
                    }
                }
            }
        }
        paragraph("Signatur, ${navn(pdfInnhold.navn)}, ${pdfInnhold.dagensDatoOgTidspunkt}")
        paragraph("--------------------------------------------------")
    }
}

// TODO felles m feks søknad?
private fun jaNei(spørsmål: Boolean?) = if (spørsmål == true) "Ja" else "Nei"

// TODO felles m feks søknad?
private fun ContentBuilder.feltMedVerdi(etikett: String, verdi: String) {
    text(etikett, FontType.BOLD)
    newLine()
    text(verdi)
}

// TODO kan types beder?
private fun Innhold.kontrollseksjon(tittel: String, rader: List<Pair<String, ContentBuilder.() -> Unit>>) {
    table {
        header {
            column(tittel)
            column("")
        }
        rader.forEach { (spørsmål, svar) ->
            row {
                cell(spørsmål, FontType.BOLD)
                cell(svar)
            }
        }
    }
}

private fun svar(tekst: String): ContentBuilder.() -> Unit = { text(tekst) }

private fun datoliste(datoer: List<String>, hvisIngenTekst: String): ContentBuilder.() -> Unit = {
    val datoer = datoer.ifEmpty { listOf(hvisIngenTekst) }
    datoer.forEachIndexed { indeks, dato ->
        if (indeks > 0) newLine()
        text(dato)
    }
}

private fun navn(navn: Person.Navn): String = listOfNotNull(
    navn.fornavn,
    navn.mellomnavn,
    navn.etternavn,
).joinToString(" ")
