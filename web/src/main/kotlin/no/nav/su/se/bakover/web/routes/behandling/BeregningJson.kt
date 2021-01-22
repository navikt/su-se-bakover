package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.domain.beregning.fradrag.getEpsFribeløp
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.UgyldigFradrag.Companion.toResultat
import no.nav.su.se.bakover.web.routes.behandling.beregning.UtenlandskInntektJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.UtenlandskInntektJson.Companion.toJson
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal data class BeregningJson(
    val id: String,
    val opprettet: String,
    val fraOgMed: String,
    val tilOgMed: String,
    val sats: String,
    val månedsberegninger: List<MånedsberegningJson> = emptyList(),
    val fradrag: List<FradragJson> = emptyList()
)

internal fun Beregning.toJson(): BeregningJson {
    val epsInputFradragMap = getFradrag()
        .filter { it.getTilhører() == FradragTilhører.EPS }
        .flatMap { FradragFactory.periodiser(it) }
        .groupBy { it.getPeriode() }

    return BeregningJson(
        id = getId().toString(),
        opprettet = getOpprettet().toString(),
        fraOgMed = getPeriode().getFraOgMed().format(DateTimeFormatter.ISO_DATE),
        tilOgMed = getPeriode().getTilOgMed().format(DateTimeFormatter.ISO_DATE),
        sats = getSats().name,
        månedsberegninger = getMånedsberegninger().map {
            it.toJson(
                getEpsFribeløp(getFradragStrategyName(), it.getPeriode()),
                epsInputFradrag = epsInputFradragMap[it.getPeriode()] ?: emptyList()
            )
        }, // TODO show fradrag/month
        fradrag = getFradrag().map { it.toJson() }
    )
}

internal fun Fradrag.toJson() =
    FradragJson(
        type = getFradragstype().toString(),
        beløp = getMånedsbeløp(),
        utenlandskInntektJson = getUtenlandskInntekt()?.toJson(),
        periode = getPeriode().toJson(),
        tilhører = getTilhører().toString()
    )

internal data class UgyldigFradrag(
    val resultat: Resultat
) {
    companion object {
        fun UtenlandskInntekt.UgyldigUtenlandskInntekt.toResultat(): UgyldigFradrag {
            return when (this) {
                UtenlandskInntekt.UgyldigUtenlandskInntekt.BeløpKanIkkeVæreNegativ -> HttpStatusCode.BadRequest.message("Beløpet kan ikke være negativt")
                UtenlandskInntekt.UgyldigUtenlandskInntekt.ValutaMåFyllesUt -> HttpStatusCode.BadRequest.message("Valuta må fylles ut")
                UtenlandskInntekt.UgyldigUtenlandskInntekt.KursKanIkkeVæreNegativ -> HttpStatusCode.BadRequest.message("Kursen kan ikke være negativ")
            }.let {
                UgyldigFradrag(it)
            }
        }

        fun Periode.UgyldigPeriode.toResultat(): UgyldigFradrag {
            return when (this) {
                Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden -> HttpStatusCode.BadRequest.message("Perioder kan kun starte på første dag i måneden")
                Periode.UgyldigPeriode.TilOgMedDatoMåVæreSisteDagIMåneden -> HttpStatusCode.BadRequest.message("Perioder kan kun avsluttes siste dag i måneden")
                Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørTilOgMedDato -> HttpStatusCode.BadRequest.message("Startmåned må være tidligere eller lik sluttmåned")
                Periode.UgyldigPeriode.PeriodeKanIkkeVæreLengreEnn12Måneder -> HttpStatusCode.BadRequest.message("En stønadsperiode kan være maks 12 måneder")
                Periode.UgyldigPeriode.FraOgMedDatoKanIkkeVæreFør2020 -> HttpStatusCode.BadRequest.message("En stønadsperiode kan ikke starte før 2020")
            }.let {
                UgyldigFradrag(it)
            }
        }
    }
}

internal data class FradragJson(
    val periode: PeriodeJson?,
    val type: String,
    val beløp: Double,
    val utenlandskInntektJson: UtenlandskInntektJson?,
    val tilhører: String
) {
    fun toFradrag(beregningsperiode: Periode): Either<UgyldigFradrag, Fradrag> {
        val utenlandskInntekt: UtenlandskInntekt? = this.utenlandskInntektJson?.toUtlandskInntekt()?.getOrHandle {
            return it.toResultat().left()
        }
        val periode: Periode = this.periode?.toPeriode()?.getOrHandle {
            return it.toResultat().left()
        } ?: beregningsperiode
        return FradragFactory.ny(
            type = Fradragstype.valueOf(type),
            månedsbeløp = beløp,
            periode = periode,
            utenlandskInntekt = utenlandskInntekt,
            tilhører = FradragTilhører.valueOf(tilhører)
        ).right()
    }
}

internal data class PeriodeJson(
    val fraOgMed: String,
    val tilOgMed: String
) {
    fun toPeriode(): Either<Periode.UgyldigPeriode, Periode> {
        return Periode.tryCreate(LocalDate.parse(fraOgMed), LocalDate.parse(tilOgMed))
    }
}

internal fun Periode.toJson() =
    PeriodeJson(getFraOgMed().format(DateTimeFormatter.ISO_DATE), getTilOgMed().format(DateTimeFormatter.ISO_DATE))
