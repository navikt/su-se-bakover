package no.nav.su.se.bakover.database.beregning

import arrow.core.Nel
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningMedFradragBeregnetMånedsvis
import no.nav.su.se.bakover.domain.satser.SatsFactory
import java.util.UUID

/**
 * JSON-modell for å representere en beregning i databasen
 */
private data class PersistertBeregning(
    val id: UUID,
    val opprettet: Tidspunkt,
    val månedsberegninger: List<PersistertMånedsberegning>,
    val fradrag: List<PersistertFradrag>,
    val sumYtelse: Int,
    val sumFradrag: Double,
    val periode: PeriodeJson,
    val begrunnelse: String?,
) {
    fun toBeregning(satsFactory: SatsFactory, sakstype: Sakstype): BeregningMedFradragBeregnetMånedsvis {
        return BeregningMedFradragBeregnetMånedsvis(
            id = id,
            opprettet = opprettet,
            periode = periode.toPeriode(),
            fradrag = fradrag.map { it.toFradragForPeriode() },
            begrunnelse = begrunnelse,
            sumYtelse = sumYtelse,
            sumFradrag = sumFradrag,
            månedsberegninger = Nel.fromListUnsafe(
                månedsberegninger.map {
                    it.toMånedsberegning(
                        satsFactory = satsFactory,
                        sakstype = sakstype,
                    )
                },
            ),
        )
    }
}

internal fun String.deserialiserBeregning(satsFactory: SatsFactory, sakstype: Sakstype): BeregningMedFradragBeregnetMånedsvis {
    return deserialize<PersistertBeregning>(this).toBeregning(satsFactory, sakstype)
}

/** Serialiserer til json-struktur til persistering */
internal fun Beregning.serialiser(): String {
    return PersistertBeregning(
        id = getId(),
        opprettet = getOpprettet(),
        månedsberegninger = getMånedsberegninger().map { it.toJson() },
        fradrag = getFradrag().map { it.toJson() },
        sumYtelse = getSumYtelse(),
        sumFradrag = getSumFradrag(),
        periode = periode.toJson(),
        begrunnelse = getBegrunnelse(),
    ).let {
        serialize(it)
    }
}
