package common.presentation.beregning

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import common.presentation.beregning.UtenlandskInntektJson.Companion.toJson
import common.presentation.periode.toPeriodeOrResultat
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.UtenlandskInntekt

internal fun Fradragstype.Companion.UgyldigFradragstype.tilResultat(): Resultat {
    return when (this) {
        Fradragstype.Companion.UgyldigFradragstype.UspesifisertKategoriUtenBeskrivelse -> {
            HttpStatusCode.BadRequest.errorJson(
                message = "Uspesifisert kategori krever beskrivelse",
                code = "uspesifisiert_fradrag_krever_beskrivelse",
            )
        }

        Fradragstype.Companion.UgyldigFradragstype.SpesifisertKategoriMedBeskrivelse -> {
            HttpStatusCode.BadRequest.errorJson(
                message = "Spesifisert kategori skal ikke ha beskrivelse",
                code = "spesifisert_fradrag_skal_ikke_ha_beskrivelse",
            )
        }

        Fradragstype.Companion.UgyldigFradragstype.UkjentFradragstype -> {
            HttpStatusCode.BadRequest.errorJson(
                message = "Ukjent fradragstype",
                code = "ukjent_fradragstype",
            )
        }
    }
}

data class FradragRequestJson(
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
        fun List<FradragRequestJson>.toFradrag(): Either<Resultat, List<Fradrag>> {
            return either {
                this@toFradrag.map {
                    it.toFradrag().bind()
                }
            }
        }
    }
}

data class FradragResponseJson(
    val periode: PeriodeJson,
    val type: String,
    val beskrivelse: String?,
    val beløp: Double,
    val utenlandskInntekt: UtenlandskInntektJson?,
    val tilhører: String,
) {
    companion object {
        fun List<Fradrag>.toJson() =
            this.map { it.toJson() }

        fun Fradrag.toJson() =
            FradragResponseJson(
                type = fradragstype.kategori.toString(),
                beskrivelse = when (val f = fradragstype) {
                    is Fradragstype.Annet -> f.beskrivelse
                    else -> null
                },
                beløp = månedsbeløp,
                utenlandskInntekt = utenlandskInntekt?.toJson(),
                periode = periode.toJson(),
                tilhører = tilhører.toString(),
            )
    }
}
