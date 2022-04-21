package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.sequence
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragskategori
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragskategoriWrapper
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.UtenlandskInntektJson.Companion.toJson

internal data class FradragskategoriWrapperJson(
    val kategori: String,
    val spesifisertKategori: String? = null,
) {
    init {
        if (kategori == Fradragskategori.Annet.toString() && spesifisertKategori == null) throw IllegalArgumentException("Typen må spesifiseres")
        if (kategori != Fradragskategori.Annet.toString() && spesifisertKategori != null) throw IllegalArgumentException("Typen skal kun spesifieres dersom den er 'Annet'")
    }
}

internal data class FradragJson(
    val periode: PeriodeJson?,
    val fradragskategoriWrapper: FradragskategoriWrapperJson,
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
            type = FradragskategoriWrapper(
                kategori = Fradragskategori.valueOf(fradragskategoriWrapper.kategori),
                spesifisertKategori = fradragskategoriWrapper.spesifisertKategori,
            ),
            månedsbeløp = beløp,
            periode = periode,
            utenlandskInntekt = utenlandskInntekt,
            tilhører = FradragTilhører.valueOf(tilhører),
        ).right()
    }

    internal fun toFradrag(): Either<Resultat, Fradrag> {
        return if (this.periode == null) HttpStatusCode.BadRequest.errorJson(
            "Fradrag mangler periode",
            "fradrag_mangler_periode"
        )
            .left()
        else
            toFradrag(this.periode.toPeriode().getOrHandle { return it.left() })
    }

    companion object {
        fun List<FradragJson>.toFradrag(): Either<Resultat, List<Fradrag>> {
            return this.map {
                it.toFradrag()
            }.sequence()
        }

        fun List<Fradrag>.toJson() =
            this.map { it.toJson() }

        fun Fradrag.toJson() =
            FradragJson(
                fradragskategoriWrapper = FradragskategoriWrapperJson(
                    kategori = fradragskategoriWrapper.kategori.toString(),
                    spesifisertKategori = fradragskategoriWrapper.spesifisertKategori,
                ),
                beløp = månedsbeløp,
                utenlandskInntekt = utenlandskInntekt?.toJson(),
                periode = periode.toJson(),
                tilhører = tilhører.toString(),
            )
    }
}
