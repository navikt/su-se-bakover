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

val kontrollnotat = { tittel: String, pdfInnhold: KontrollnotatPdfInnholdNy ->
    dokument(tittel, personalia = null) {
        table {
            header {
                column("Kontrollnotat")
                column("")
            }
            row {
                cell { feltMedVerdi("Navn:", fulltNavn(pdfInnhold.navn)) }
                cell { feltMedVerdi("Saksnummer:", pdfInnhold.saksnummer.toString()) }
            }
            row {
                cell { feltMedVerdi("Dato og tidspunkt:", pdfInnhold.dagensDatoOgTidspunkt) }
                cell("")
            }
        }
        kontrollseksjon(
            tittel = "Personlig oppmøte",
            rader = listOf(
                SpørsmålRad(
                    spørsmål = "Har bruker møtt personlig?",
                    svar = svar(jaNei(pdfInnhold.personligOppmøte)),
                ),
            ),
        )
        kontrollseksjon(
            tittel = "Fullmakt og legeerklæring",
            rader = listOf(
                SpørsmålRad(
                    spørsmål = "Foreligger det fullmakt og legeerklæring som bekrefter at sykdom gjør at bruker ikke kan møte personlig?",
                    svar = svar(jaNei(pdfInnhold.fullmaktOgLegeerklæring)),
                ),
            ),
        )
        kontrollseksjon(
            tittel = "Kontroll av reisedokumenter",
            rader = listOf(
                SpørsmålRad(
                    spørsmål = "Har bruker framvist originalt pass og eventuelle andre originale reisedokumenter?",
                    svar = svar(jaNei(pdfInnhold.originalPass)),
                ),
                SpørsmålRad(
                    spørsmål = "Er det nye stempler i passet siden forrige møte?",
                    svar = svar(jaNei(pdfInnhold.gyldigPass)),
                ),
            ),
        )
        kontrollseksjon(
            tittel = "Reise til utlandet",
            rader = buildList {
                add(
                    SpørsmålRad(
                        spørsmål = "Har bruker vært utenlands siden siste møte?",
                        svar = svar(jaNei(pdfInnhold.harVærtUtenlands)),
                    ),
                )
                if (pdfInnhold.harVærtUtenlands) {
                    add(
                        SpørsmålRad(
                            spørsmål = "Registrerte utenlandsopphold",
                            svar = datoliste(
                                datoer = pdfInnhold.utenlandsoppholdDatoer,
                                hvisIngenTekst = "Ingen registrerte utenlandsopphold",
                            ),
                        ),
                    )
                }
                add(
                    SpørsmålRad(
                        spørsmål = "Har bruker planer om å reise utenlands?",
                        svar = svar(jaNei(pdfInnhold.harPlanerOmUtenlandsreise)),
                    ),
                )
                if (pdfInnhold.harPlanerOmUtenlandsreise) {
                    add(
                        SpørsmålRad(
                            spørsmål = "Planlagte reisedatoer",
                            svar = datoliste(
                                datoer = pdfInnhold.planlagteUtenlandsreiseDatoer,
                                hvisIngenTekst = "Ingen datoer registrert",
                            ),
                        ),
                    )
                    add(
                        SpørsmålRad(
                            spørsmål = "Har bruker fått beskjed om at reisedokumenter, billetter/boardingpass må tas vare på?",
                            svar = svar(jaNei(pdfInnhold.reiseDokumentasjon)),
                        ),
                    )
                }
            },
        )
        kontrollseksjon(
            tittel = "Økonomisk situasjon",
            rader = listOf(
                SpørsmålRad(
                    spørsmål = "Er det endringer i den økonomiske situasjonen for bruker eller ektefelle/partner/samboer?",
                    svar = svar(jaNei(pdfInnhold.økonomiskSituasjon)),
                ),
            ),
        )
        kontrollseksjon(
            "Andre forhold",
            listOf(
                SpørsmålRad(
                    spørsmål = "Er det endringer i andre forhold av betydning for stønaden (f.eks. sivilstand, institusjonsopphold, oppholdstillatelse)?",
                    svar = svar(jaNei(pdfInnhold.andreForhold)),
                ),
            ),
        )
        kontrollseksjon(
            "Skatteopplysninger",
            listOf(
                SpørsmålRad(
                    spørsmål = "Er siste skattemelding og grunnlag for skatt for bruker og eventuell ektefelle/partner/samboer levert?",
                    svar = svar(jaNei(pdfInnhold.skatteOpplysninger)),
                ),
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
        paragraph("Signatur, ${fulltNavn(pdfInnhold.navn)}, ${pdfInnhold.dagensDatoOgTidspunkt}")
        paragraph("--------------------------------------------------")
    }
}

private fun jaNei(spørsmål: Boolean?) = if (spørsmål == true) "Ja" else "Nei"

private fun ContentBuilder.feltMedVerdi(etikett: String, verdi: String) {
    text(etikett, FontType.BOLD)
    newLine()
    text(verdi)
}

private data class SpørsmålRad(
    val spørsmål: String,
    val svar: ContentBuilder.() -> Unit,
)

private fun Innhold.kontrollseksjon(tittel: String, rader: List<SpørsmålRad>) {
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

private fun fulltNavn(navn: Person.Navn): String = listOfNotNull(
    navn.fornavn,
    navn.mellomnavn,
    navn.etternavn,
).joinToString(" ")
