package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.regulering.ReguleringssupplementFor
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class PesysUtrekkFromCsv(
    /** FNR: Eksempel: 12345678901 */
    val fnr: String,
    /** K_SAK_T: Eksempel: UFOREP/GJENLEV/ALDER */
    val sakstype: String,
    /** K_VEDTAK_T: Eksempel: ENDRING/REGULERING */
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
    fun toDomain() {
    }

    val fradragstype: Fradragstype? = when (sakstype) {
        "UFOREP" -> Fradragstype.Uføretrygd
        "ALDER" -> Fradragstype.Alderspensjon
        "GJENLEV" -> Fradragstype.Gjenlevendepensjon
        else -> null
    }

    fun vedtakstypeToDomain(): ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype? = when (vedtakstype) {
        "REGULERING" -> ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Regulering
        "ENDRING" -> ReguleringssupplementFor.PerType.Fradragsperiode.Vedtakstype.Endring
        else -> null
    }
}

fun List<PesysUtrekkFromCsv>.toDomain(): Either<Resultat, List<ReguleringssupplementFor>> {
    return this.groupBy { it.fnr }.toReguleringssupplementInnhold()
}

private fun Map<String, List<PesysUtrekkFromCsv>>.toReguleringssupplementInnhold(): Either<Resultat, List<ReguleringssupplementFor>> {
    return this.map { (stringFnr, csv) ->
        val fnr = Fnr.tryCreate(stringFnr) ?: return HttpStatusCode.BadRequest.errorJson(
            "Feil ved parsing av fnr",
            "feil_ved_parsing_av_fnr",
        ).left()

        val alleFradragGruppert = csv.groupBy { it.fradragstype }.filterKeys { it != null }.mapKeys { it.key!! }

        val alleFradragPerType = alleFradragGruppert.map { (fradragstype, csv) ->

            ReguleringssupplementFor.PerType(
                fradragsperioder = csv.mapNotNull { csvInnslag ->
                    val vedtakstype = csvInnslag.vedtakstypeToDomain() ?: return@mapNotNull null
                    ReguleringssupplementFor.PerType.Fradragsperiode(
                        fraOgMed = LocalDate.parse(csvInnslag.fraOgMed, DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                        tilOgMed = csvInnslag.tilOgMed?.let {
                            LocalDate.parse(it, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                        },
                        beløp = csvInnslag.nettoYtelse.toInt(),
                        vedtakstype = vedtakstype,
                        eksterndata = ReguleringssupplementFor.PerType.Fradragsperiode.Eksterndata(
                            fnr = csvInnslag.fnr,
                            sakstype = csvInnslag.sakstype,
                            vedtakstype = csvInnslag.vedtakstype,
                            fraOgMed = csvInnslag.fraOgMed,
                            tilOgMed = csvInnslag.tilOgMed,
                            bruttoYtelse = csvInnslag.bruttoYtelse,
                            nettoYtelse = csvInnslag.nettoYtelse,
                            ytelseskomponenttype = csvInnslag.ytelseskomponenttype,
                            bruttoYtelseskomponent = csvInnslag.bruttoYtelseskomponent,
                            nettoYtelseskomponent = csvInnslag.nettoYtelseskomponent,
                        ),
                    )
                }.toNonEmptyList(),
                type = fradragstype,
            )
        }.toNonEmptyList()

        ReguleringssupplementFor(fnr = fnr, perType = alleFradragPerType)
    }.right()
}
