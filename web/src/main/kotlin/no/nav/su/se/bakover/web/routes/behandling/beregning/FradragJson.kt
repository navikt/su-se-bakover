package no.nav.su.se.bakover.web.routes.behandling.beregning

import arrow.core.Either
import arrow.core.extensions.either.applicative.applicative
import arrow.core.extensions.list.traverse.traverse
import arrow.core.fix
import arrow.core.getOrHandle
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.UtenlandskInntektJson.Companion.toJson

internal data class FradragJson(
    val periode: PeriodeJson?,
    val type: String,
    val beløp: Double,
    val utenlandskInntekt: UtenlandskInntektJson?,
    val tilhører: String
) {
    internal fun toFradrag(beregningsperiode: Periode): Either<Resultat, Fradrag> {
        val utenlandskInntekt: UtenlandskInntekt? = this.utenlandskInntekt?.toUtlandskInntekt()?.getOrHandle {
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
            tilhører = FradragTilhører.valueOf(tilhører)
        ).right()
    }

    companion object {
        fun List<FradragJson>.toFradrag(beregningsperiode: Periode): Either<Resultat, List<Fradrag>> {
            return this.map {
                it.toFradrag(beregningsperiode)
            }.traverse(Either.applicative(), ::identity).fix().map {
                it.fix()
            }
        }

        fun List<Fradrag>.toJson(): List<FradragJson> {
            return this.map {
                FradragJson(
                    type = it.getFradragstype().toString(),
                    beløp = it.getMånedsbeløp(),
                    utenlandskInntekt = it.getUtenlandskInntekt()?.toJson(),
                    periode = it.getPeriode().toJson(),
                    tilhører = it.getTilhører().toString()
                )
            }
        }
    }
}
