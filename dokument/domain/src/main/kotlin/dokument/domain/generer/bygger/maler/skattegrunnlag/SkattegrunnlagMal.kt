package dokument.domain.generer.bygger.maler.skattegrunnlag

import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.bygger.Innhold
import dokument.domain.generer.bygger.dokument
import dokument.domain.generer.bygger.fritekst
import dokument.domain.generer.bygger.maler.seksjon
import no.nav.brev.brevbaker.markup.dsl.cell
import no.nav.brev.brevbaker.markup.dsl.column
import no.nav.brev.brevbaker.markup.dsl.header
import no.nav.brev.brevbaker.markup.dsl.paragraph
import no.nav.brev.brevbaker.markup.dsl.row
import no.nav.brev.brevbaker.markup.dsl.table
import no.nav.brev.brevbaker.markup.dsl.title2
import no.nav.brev.brevbaker.markup.dsl.title3
import no.nav.brev.brevbaker.markup.dsl.title4
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.format.DateTimeFormatter
import java.util.UUID

data class SkattegrunnlagsPdfInnhold(
    val saksnummer: String?,
    val behandlingstype: BehandlingstypeForSkattemelding,
    override val sakstype: Sakstype,
    val behandlingsId: UUID?,
    val vedtaksId: UUID?,
    val hentet: Tidspunkt,
    val opprettet: Tidspunkt,
    val søkers: SkattPdfDataJson?,
    val eps: SkattPdfDataJson?,
    val begrunnelse: String?,
) : PdfInnhold {
    override val dokumentMal = DokumentMal.Skattegrunnlag(
        nyTittel = if (søkers == null && eps != null) "Skattegrunnlag - EPS" else null,
    )
}

val skattegrunnlag = { _: String, pdfInnhold: SkattegrunnlagsPdfInnhold ->
    dokument("", personalia = null) {
        pdfInnhold.søkers?.let { søkers ->
            title2("Skattegrunnlag for ${søkers.fnr}")
            skattemeldingForNav(pdfInnhold)
            skattemeldingPersonopplysninger(søkers)
            årsgrunnlag(søkers)
        }
        pdfInnhold.eps?.let { eps ->
            title2("Skattegrunnlag for ${eps.fnr} (EPS)")
            skattemeldingPersonopplysninger(eps)
            årsgrunnlag(eps)
        }
        paragraph("Beskrivelse av teknisk navn kan man finne hos Skatteetaten")
        if (!pdfInnhold.begrunnelse.isNullOrEmpty()) {
            title4("Begrunnelse")
            fritekst(pdfInnhold.begrunnelse)
        }
    }
}

private fun Innhold.skattemeldingForNav(pdfInnhold: SkattegrunnlagsPdfInnhold) = seksjon("For Nav") {
    rad("Saksnummer:", pdfInnhold.saksnummer ?: "")
    rad("Behandlingstype:", pdfInnhold.behandlingstype.name)
    pdfInnhold.behandlingsId?.let { rad("behandling-id:", it.toString()) }
    pdfInnhold.vedtaksId?.let { rad("Vedtaksid-id:", it.toString()) }
    rad("Skattegrunnlag hentet:", pdfInnhold.hentet.tilDatoOgKlokkeslett())
    rad("Pdf generert:", pdfInnhold.opprettet.tilDatoOgKlokkeslett())
}

private fun Tidspunkt.tilDatoOgKlokkeslett(): String =
    toLocalDateTime(zoneIdOslo).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))

private fun Innhold.skattemeldingPersonopplysninger(person: SkattPdfDataJson) = seksjon("Personopplysninger") {
    rad("Fødselsnummer:", person.fnr.toString())
    rad("Navn:", listOfNotNull(person.navn.fornavn, person.navn.mellomnavn, person.navn.etternavn).joinToString(" "))
}

private fun Innhold.årsgrunnlag(person: SkattPdfDataJson) {
    person.årsgrunnlag.forEach { år ->
        title3("${år.år} - ${år.stadie.name}")
        when (år) {
            is SamletÅrsgrunnlagPdfJson.HarSkattegrunnlagForÅrOgStadie -> {
                if (år.stadie == ÅrsgrunnlagStadie.Oppgjør) {
                    paragraph("Oppgjørsdato: ${år.oppgjørsdato?.ddMMyyyy() ?: "-"}")
                }
                skattedel("Summert formue", år.formue, medKategori = false)
                skattedel("Summert formuesfradrag", år.formuesfradrag, medKategori = false)
                skattedel("Summert inntekt", år.inntekt, medKategori = false)
                skattedel("Summert inntektsfradrag", år.inntektsfradrag, medKategori = false)
                skattedel(
                    "Summert verdsettingsrabatts som gir gjeldsreduksjon",
                    år.verdsettingsrabattSomGirGjeldsreduksjon,
                    medKategori = false,
                )
                skattedel("Summert oppjustering av eierinntekter", år.oppjusteringAvEierinntekter, medKategori = false)
                skattedel("Summert annet", år.annet, medKategori = true)
                spesifisertKjøretøy(år.kjøretøy)
            }

            is SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie -> paragraph(år.grunn.tekst())
        }
    }
}

private fun Innhold.spesifisertKjøretøy(kjøretøy: List<SpesifiseringPdfJson.KjøretøyJson>) {
    if (kjøretøy.isEmpty()) return
    title4("Spesifisert kjøretøy")
    kjøretøy.forEach { k ->
        table {
            header {
                column("")
                column("")
            }
            listOf(
                "Beløp:" to k.beløp,
                "Registreringsnummer:" to k.registreringsnummer,
                "Fabrikatnavn:" to k.fabrikatnavn,
                "År for førstegangsregistrering:" to k.årForFørstegangsregistrering,
                "Formuesverdi:" to k.formuesverdi,
                "Antatt verdi som nytt:" to k.antattVerdiSomNytt,
                "Antatt markedsverdi:" to k.antattMarkedsverdi,
            ).forEach { (tekst, verdi) ->
                row {
                    cell(tekst)
                    cell(verdi.ellerStrek())
                }
            }
        }
    }
}

private fun Innhold.skattedel(tittel: String, grunnlag: List<SkattegrunnlagPdfJson>, medKategori: Boolean) {
    if (grunnlag.isEmpty()) return
    title4(tittel)
    table {
        header {
            column("Teknisk navn", span = 2)
            column("Beløp")
            if (medKategori) column("Kategori")
        }
        grunnlag.forEach { rad ->
            row {
                cell(brytbar(rad.tekniskNavn))
                cell(rad.beløp)
                // SkattegrunnlagPdfJson har ikke kategori.
                if (medKategori) cell("-")
            }
        }
    }
}

private fun String?.ellerStrek(): String = if (isNullOrEmpty()) "-" else this

internal fun brytbar(tekst: String): String = tekst.replace(kamelpukkel, "\u200B")

private val kamelpukkel = Regex("(?<=\\p{Ll})(?=\\p{Lu})")

private fun SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie.HarIkkeSkattegrunnlagFordi.tekst(): String =
    when (this) {
        SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie.HarIkkeSkattegrunnlagFordi.NETTVERKSFEIL ->
            "Har ikke skattegrunnlag på grunn av nettverksfeil"
        SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie.HarIkkeSkattegrunnlagFordi.FINNES_IKKE ->
            "Skattegrunnlaget finnes ikke for denne personen, i dette året, for dette stadiet"
        SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie.HarIkkeSkattegrunnlagFordi.UKJENT_FEIL ->
            "Ukjent feil ved henting av skattegrunnlag"
        SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie.HarIkkeSkattegrunnlagFordi.MANGLER_RETTIGHETER ->
            "Mangler rettigheter for at skattegrunnlaget skal hentes"
        SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie.HarIkkeSkattegrunnlagFordi.OPPSLAGET_INNEHOLDT_UGYLDIG_DATA ->
            "Oppslaget inneholdt ugyldig data"
    }
