package no.nav.su.se.bakover.web.routes.regulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import common.presentation.beregning.UtenlandskInntektJson
import common.presentation.beregning.tilResultat
import common.presentation.periode.toPeriodeOrResultat
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt

/**
 * Eldre variant av [FradragRequestJson] som fortsatt aksepterer [Fradragstype.Annet].
 *
 * Skal KUN brukes på flyter som videresender historiske fradrag fra eksisterende beregninger
 * (f.eks. manuell regulering), der vi ikke kan endre allerede registrerte data.
 *
 * Alle nye flyter (søknadsbehandling, revurdering m.m.) skal bruke [FradragRequestJson],
 * som blokkerer kategori `Annet`.
 */
data class LegacyFradragRequestJson(
    val periode: PeriodeJson?,
    val type: String,
    val beskrivelse: String?,
    val beløp: Double,
    val utenlandskInntekt: UtenlandskInntektJson?,
    val tilhører: String,
) {
    fun toFradrag(beregningsperiode: Periode): Either<Resultat, Fradrag> {
        val utenlandskInntekt: UtenlandskInntekt? = this.utenlandskInntekt?.toUtenlandskInntekt()?.getOrElse {
            return it.left()
        }
        val periode: Periode = this.periode?.toPeriodeOrResultat()?.getOrElse {
            return it.left()
        } ?: beregningsperiode
        return FradragFactory.nyFradragsperiode(
            fradragstype = Fradragstype.tryParse(type, beskrivelse).getOrElse { return it.tilResultat().left() },
            månedsbeløp = beløp,
            periode = periode,
            utenlandskInntekt = utenlandskInntekt,
            tilhører = FradragTilhører.valueOf(tilhører),
        ).right()
    }

    fun toFradrag(): Either<Resultat, Fradrag> {
        return if (this.periode == null) {
            HttpStatusCode.BadRequest.errorJson(
                "Fradrag mangler periode",
                "fradrag_mangler_periode",
            ).left()
        } else {
            toFradrag(this.periode.toPeriodeOrResultat().getOrElse { return it.left() })
        }
    }

    companion object {
        fun List<LegacyFradragRequestJson>.toFradrag(): Either<Resultat, List<Fradrag>> {
            return either {
                this@toFradrag.map {
                    it.toFradrag().bind()
                }
            }
        }
    }
}
