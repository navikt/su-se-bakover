package no.nav.su.se.bakover.database.beregning

import beregning.domain.Beregning
import beregning.domain.BeregningMedFradragBeregnetMånedsvis
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
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
    /** @param erAvbrutt brukes for å bestemme om vi skal logge mismatch i satsene */
    fun toBeregning(
        satsFactory: SatsFactoryForSupplerendeStønad,
        sakstype: Sakstype,
        saksnummer: Saksnummer,
        erAvbrutt: Boolean?,
    ): BeregningMedFradragBeregnetMånedsvis {
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
                    erAvbrutt = erAvbrutt,
                )
            }.toNonEmptyList(),
        )
    }
}

/** @param erAvbrutt brukes for å bestemme om vi skal logge mismatch i satsene */
internal fun String.deserialiserBeregning(
    satsFactory: SatsFactoryForSupplerendeStønad,
    sakstype: Sakstype,
    saksnummer: Saksnummer,
    erAvbrutt: Boolean?,
): BeregningMedFradragBeregnetMånedsvis {
    return deserialize<PersistertBeregning>(this).toBeregning(satsFactory, sakstype, saksnummer, erAvbrutt)
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
