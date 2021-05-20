package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.Either
import arrow.core.extensions.either.applicative.applicative
import arrow.core.extensions.list.traverse.traverse
import arrow.core.fix
import arrow.core.getOrHandle
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.UtenlandskInntektJson.Companion.toJson

internal data class FradragJson(
    val periode: PeriodeJson?,
    val type: String,
    val beløp: Double,
    val utenlandskInntekt: UtenlandskInntektJson?,
    val tilhører: String,
) {
    internal fun toFradrag(beregningsperiode: Periode): Either<Resultat, Fradrag> {
        val utenlandskInntekt: UtenlandskInntekt? = this.utenlandskInntekt?.toUtenlandskInntekt()?.getOrHandle {
            return it.left()
        }
        val periode: Periode = this.periode?.toPeriode()?.getOrHandle {
            return it.left()
        } ?: beregningsperiode
        return FradragFactory.ny(
            type = Fradragstype.valueOf(type),
            månedsbeløp = beløp,
            periode = periode,
            utenlandskInntekt = utenlandskInntekt,
            tilhører = FradragTilhører.valueOf(tilhører),
        ).right()
    }

    internal fun toFradrag(): Either<Resultat, Fradrag> {
        return if (this.periode == null) HttpStatusCode.BadRequest.message("Fradrag mangler periode").left()
        else
            toFradrag(this.periode.toPeriode().getOrHandle { return it.left() })
    }

    companion object {
        fun List<FradragJson>.toFradrag(beregningsperiode: Periode): Either<Resultat, List<Fradrag>> {
            return this.map {
                it.toFradrag(beregningsperiode)
            }.traverse(Either.applicative(), ::identity).fix().map {
                it.fix()
            }
        }

        fun List<FradragJson>.toFradrag(): Either<Resultat, List<Fradrag>> {
            return this.map {
                it.toFradrag()
            }.traverse(Either.applicative(), ::identity).fix().map {
                it.fix()
            }
        }

        fun List<Fradrag>.toJson(): List<FradragJson> {
            return this.map {
                FradragJson(
                    type = it.fradragstype.toString(),
                    beløp = it.månedsbeløp,
                    utenlandskInntekt = it.utenlandskInntekt?.toJson(),
                    periode = it.periode.toJson(),
                    tilhører = it.tilhører.toString(),
                )
            }
        }
    }
}
