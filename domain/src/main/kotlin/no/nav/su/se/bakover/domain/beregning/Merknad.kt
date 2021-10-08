package no.nav.su.se.bakover.domain.beregning

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.UtenlandskInntekt
import java.time.LocalDate

sealed class Merknad {

    data class EndringGrunnbeløp(
        val gammeltGrunnbeløp: Detalj,
        val nyttGrunnbeløp: Detalj,
    ) : Merknad() {

        data class Detalj(
            val dato: LocalDate,
            val grunnbeløp: Int,
        ) {
            companion object {
                fun forDato(dato: LocalDate): Detalj {
                    Grunnbeløp.`1G`.let {
                        return Detalj(
                            dato = dato,
                            grunnbeløp = it.heltallPåDato(dato),
                        )
                    }
                }
            }
        }
    }

    data class NyYtelse(
        val benyttetBeregning: MerknadMånedsberegning,
    ) : Merknad() {
        companion object {
            fun from(benyttetBeregning: Månedsberegning) = NyYtelse(
                benyttetBeregning = benyttetBeregning.toMerknadMånedsberegning(),
            )
        }
    }

    data class ØktYtelse(
        val benyttetBeregning: MerknadMånedsberegning,
        val forkastetBeregning: MerknadMånedsberegning,
    ) : Merknad() {
        companion object {
            fun from(benyttetBeregning: Månedsberegning, forkastetBeregning: Månedsberegning) = ØktYtelse(
                benyttetBeregning = benyttetBeregning.toMerknadMånedsberegning(),
                forkastetBeregning = forkastetBeregning.toMerknadMånedsberegning(),
            )
        }
    }

    data class RedusertYtelse(
        val benyttetBeregning: MerknadMånedsberegning,
        val forkastetBeregning: MerknadMånedsberegning,
    ) : Merknad() {
        companion object {
            fun from(benyttetBeregning: Månedsberegning, forkastetBeregning: Månedsberegning) = RedusertYtelse(
                benyttetBeregning = benyttetBeregning.toMerknadMånedsberegning(),
                forkastetBeregning = forkastetBeregning.toMerknadMånedsberegning(),
            )
        }
    }

    data class EndringUnderTiProsent(
        val benyttetBeregning: MerknadMånedsberegning,
        val forkastetBeregning: MerknadMånedsberegning,
    ) : Merknad() {
        companion object {
            fun from(benyttetBeregning: Månedsberegning, forkastetBeregning: Månedsberegning) = EndringUnderTiProsent(
                benyttetBeregning = benyttetBeregning.toMerknadMånedsberegning(),
                forkastetBeregning = forkastetBeregning.toMerknadMånedsberegning(),
            )
        }
    }

    data class MerknadMånedsberegning(
        val periode: Periode,
        val sats: Sats,
        val grunnbeløp: Int,
        val beløp: Int,
        val fradrag: List<MerknadFradrag>,
        val satsbeløp: Double,
        val fribeløpForEps: Double,
    )

    data class MerknadFradrag(
        val periode: Periode,
        val fradragstype: Fradragstype,
        val månedsbeløp: Double,
        val utenlandskInntekt: UtenlandskInntekt?,
        val tilhører: FradragTilhører,
    )
}

fun Månedsberegning.toMerknadMånedsberegning() = Merknad.MerknadMånedsberegning(
    periode = periode,
    sats = getSats(),
    grunnbeløp = getBenyttetGrunnbeløp(),
    beløp = getSumYtelse(),
    fradrag = getFradrag().map { it.toMerknadFradrag() },
    satsbeløp = getSatsbeløp(),
    fribeløpForEps = getFribeløpForEps(),
)

fun Fradrag.toMerknadFradrag() = Merknad.MerknadFradrag(
    periode = periode,
    fradragstype = fradragstype,
    månedsbeløp = månedsbeløp,
    utenlandskInntekt = utenlandskInntekt,
    tilhører = tilhører,
)
