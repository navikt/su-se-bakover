package no.nav.su.se.bakover.web.routes.regulering.uttrekk.pesys

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.tid.periode.PeriodeMedOptionalTilOgMed
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import no.nav.su.se.bakover.domain.regulering.supplement.Eksternvedtak
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class PesysUtrekkFromCsv(
    /** FNR: Eksempel: 12345678901 - MERK fnr kan komme inn som 10 tegn når man kopierer inn til excel */
    val fnr: String,
    /** K_SAK_T: Eksempel: UFOREP/GJENLEV/ALDER */
    val sakstype: String,
    /** K_VEDTAK_T: Eksempel: ENDRING/REGULERING, ... */
    val vedtakstype: String,
    /** FOM_DATO: Eksempel: 01.04.2024 */
    val fraOgMed: String,
    /** TOM_DATO: Eksempel: 30.04.2024 */
    val tilOgMed: String?,
    /**
     * Summen av alle brutto_yk per vedtak
     * BRUTTO: Eksempel: 27262
     */
    val bruttoYtelse: String,
    /**
     * Summen av alle netto_yk per vedtak
     * NETTO: Eksempel: 28261
     */
    val nettoYtelse: String,
    /** K_YTELSE_KOMP_T: Eksempel: UT_ORDINER, UT_GJT, UT_TSB, ... */
    val ytelseskomponenttype: String,
    /** BRUTTO_YK: Eksempel: 2199 */
    val bruttoYtelseskomponent: String,
    /** NETTO_YK: Eksempel: 26062 */
    val nettoYtelseskomponent: String,
) {
    val fradragskategori: Fradragstype.Kategori? = when (sakstype) {
        "UFOREP" -> Fradragstype.Uføretrygd.kategori
        "ALDER" -> Fradragstype.Alderspensjon.kategori
        else -> null
    }

    fun vedtakstypeToDomain(): ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype? = when (vedtakstype) {
        "REGULERING" -> ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering
        "ENDRING" -> ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring
        else -> null
    }

    fun fraOgMedToDomain(): LocalDate = LocalDate.parse(fraOgMed, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    fun tilOgMedToDomain(): LocalDate? = tilOgMed?.let {
        LocalDate.parse(it, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    }
}

fun List<PesysUtrekkFromCsv>.toDomain(): Either<Resultat, List<ReguleringssupplementFor>> {
    return this.groupBy { it.fnr }.toReguleringssupplementInnhold()
}

/**
 * Fnr som vi får inn fra supplementet kan muligens inneholde fnr som har 10 tegn.
 * Dette skjer når man kopierer inn til excel. Excel fjerner det første '0' tegnet. Her ønsker vi å legge den tilbake
 */
private fun prepend0ToFnrsWith10Digits(fnr: String): String = if (fnr.length == 10) "0$fnr" else fnr

private fun Map<String, List<PesysUtrekkFromCsv>>.toReguleringssupplementInnhold(): Either<Resultat, List<ReguleringssupplementFor>> {
    return this.map { (stringFnr, csv) ->
        val fnr = Fnr.tryCreate(prepend0ToFnrsWith10Digits(stringFnr)) ?: return HttpStatusCode.BadRequest.errorJson(
            "Feil ved parsing av fnr",
            "feil_ved_parsing_av_fnr",
        ).left()

        csv.grupperPåFradragstype().map { (fradragskategori, gruppertPåFradragstype) ->
            gruppertPåFradragstype.grupperPåVedtakstype().map { (vedtakstype, gruppertPåVedtak) ->
                gruppertPåVedtak.grupperPåPeriode().map { (periode, gruppertPåPeriode) ->
                    gruppertPåPeriode.toEksternvedtak(
                        vedtakstype = vedtakstype,
                        fraOgMed = periode.first,
                        tilOgMed = periode.second,
                    )
                }
            }.flatten().let {
                ReguleringssupplementFor.PerType(
                    kategori = fradragskategori,
                    vedtak = it.toNonEmptyList(),
                )
            }
        }.let {
            ReguleringssupplementFor(fnr, it.toNonEmptyList())
        }
    }.right()
}

private fun List<PesysUtrekkFromCsv>.grupperPåFradragstype(): Map<Fradragstype.Kategori, List<PesysUtrekkFromCsv>> =
    this.groupBy { it.fradragskategori }.filterKeys { it != null }.mapKeys { it.key!! }

private fun List<PesysUtrekkFromCsv>.grupperPåVedtakstype(): Map<ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype, List<PesysUtrekkFromCsv>> =
    this.groupBy { it.vedtakstypeToDomain() }.filterKeys { it != null }.mapKeys { it.key!! }

private fun List<PesysUtrekkFromCsv>.grupperPåPeriode(): Map<Pair<LocalDate, LocalDate?>, List<PesysUtrekkFromCsv>> =
    this.groupBy { Pair(it.fraOgMedToDomain(), it.tilOgMedToDomain()) }

private fun List<PesysUtrekkFromCsv>.toEksternvedtak(
    vedtakstype: ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype,
    fraOgMed: LocalDate,
    tilOgMed: LocalDate?,
): Eksternvedtak {
    return when (vedtakstype) {
        ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering -> Eksternvedtak.Regulering(
            periode = PeriodeMedOptionalTilOgMed(
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
            ),
            fradrag = this.toFradragsperiode().toNonEmptyList(),
            beløp = this.first().nettoYtelse.toInt(),
        )

        ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring -> Eksternvedtak.Endring(
            måned = Periode.create(fraOgMed = fraOgMed, tilOgMed = tilOgMed!!).tilMåned(),
            fradrag = this.toFradragsperiode().toNonEmptyList(),
            beløp = this.first().nettoYtelse.toInt(),
        )
    }
}

private fun List<PesysUtrekkFromCsv>.toFradragsperiode(): List<ReguleringssupplementFor.PerType.Fradragsperiode> {
    return this.map { it.toFradragsperiode() }
}

private fun PesysUtrekkFromCsv.toFradragsperiode(): ReguleringssupplementFor.PerType.Fradragsperiode {
    return ReguleringssupplementFor.PerType.Fradragsperiode(
        fraOgMed = LocalDate.parse(this.fraOgMed, DateTimeFormatter.ofPattern("dd.MM.yyyy")),
        tilOgMed = this.tilOgMed?.let {
            LocalDate.parse(it, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        },
        beløp = this.nettoYtelse.toInt(),
        vedtakstype = this.vedtakstypeToDomain()!!,
        eksterndata = ReguleringssupplementFor.PerType.Fradragsperiode.Eksterndata(
            fnr = this.fnr,
            sakstype = this.sakstype,
            vedtakstype = this.vedtakstype,
            fraOgMed = this.fraOgMed,
            tilOgMed = this.tilOgMed,
            bruttoYtelse = this.bruttoYtelse,
            nettoYtelse = this.nettoYtelse,
            ytelseskomponenttype = this.ytelseskomponenttype,
            bruttoYtelseskomponent = this.bruttoYtelseskomponent,
            nettoYtelseskomponent = this.nettoYtelseskomponent,
        ),
    )
}
