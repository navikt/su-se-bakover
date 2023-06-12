package no.nav.su.se.bakover.database.beregning

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningMedFradragBeregnetMånedsvis
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
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
    fun toBeregning(satsFactory: SatsFactoryForSupplerendeStønad, sakstype: Sakstype, saksnummer: Saksnummer): BeregningMedFradragBeregnetMånedsvis {
        return BeregningMedFradragBeregnetMånedsvis(
            id = id,
            opprettet = opprettet,
            periode = periode.toPeriode(),
            fradrag = fradrag.map { it.toFradragForPeriode() },
            begrunnelse = begrunnelse,
            sumYtelse = sumYtelse,
            sumFradrag = sumFradrag,
            månedsberegninger = månedsberegninger.map {
                it.toMånedsberegning(
                    satsFactory = satsFactory.gjeldende(opprettet),
                    sakstype = sakstype,
                    saksnummer = saksnummer,
                )
            }.toNonEmptyList(),
        )
    }
}

internal fun String.deserialiserBeregning(
    satsFactory: SatsFactoryForSupplerendeStønad,
    sakstype: Sakstype,
    saksnummer: Saksnummer,
): BeregningMedFradragBeregnetMånedsvis {
    return deserialize<PersistertBeregning>(this).toBeregning(satsFactory, sakstype, saksnummer)
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
