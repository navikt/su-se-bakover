package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.regulering.Reguleringssupplement
import no.nav.su.se.bakover.domain.regulering.ReguleringssupplementFor
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate

fun Map<String, List<SupplementInnholdAsCsv>>.toReguleringssupplementInnhold(): Either<Resultat, List<ReguleringssupplementFor>> {
    return this.map { (stringFnr, csv) ->
        val fnr = Fnr.tryCreate(stringFnr) ?: return HttpStatusCode.BadRequest.errorJson(
            "Feil ved parsing av fnr",
            "feil_ved_parsing_av_fnr",
        ).left()
        val alleFradragGruppert = csv.groupBy { it.type }

        val alleFradragPerType = alleFradragGruppert.map { (fradragstype, csv) ->
            val type = try {
                val kategori = Fradragstype.Kategori.valueOf(fradragstype)
                Fradragstype.from(kategori, null)
            } catch (e: Exception) {
                return HttpStatusCode.BadRequest.errorJson(
                    "Feil ved parsing av fradragstype $fradragstype",
                    "feil_ved_parsing_av_fradragstype",
                ).left()
            }
            ReguleringssupplementFor.PerType(
                fradragsperioder = csv.map { csvInnslag ->
                    ReguleringssupplementFor.PerType.Fradragsperiode(
                        periode = Periode.create(
                            fraOgMed = LocalDate.parse(csvInnslag.fom),
                            tilOgMed = LocalDate.parse(csvInnslag.tom),
                        ),
                        beløp = csvInnslag.beløp.toInt(),
                    )
                }.toNonEmptyList(),
                type = type,
            )
        }.toNonEmptyList()

        ReguleringssupplementFor(fnr = fnr, perType = alleFradragPerType)
    }.right()
}

fun parseCSVFromFile(csv: String): Either<Resultat, Reguleringssupplement> = parseCSV(csv.split("\r\n"))

fun parseCSVFromText(csv: String): Either<Resultat, Reguleringssupplement> = parseCSV(csv.split("\n"))

private fun parseCSV(csv: List<String>): Either<Resultat, Reguleringssupplement> = csv.map {
    val (fnr, fom, tom, type, beløp) = it.split(";")
    SupplementInnholdAsCsv(fnr, fom, tom, type, beløp)
}.groupBy { it.fnr }
    .toReguleringssupplementInnhold()
    .map { Reguleringssupplement(it) }
    .mapLeft { it }
