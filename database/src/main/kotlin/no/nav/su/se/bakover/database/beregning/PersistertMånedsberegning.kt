package no.nav.su.se.bakover.database.beregning

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.MånedJson
import no.nav.su.se.bakover.common.periode.MånedJson.Companion.toJson
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.beregning.BeregningForMåned
import no.nav.su.se.bakover.domain.beregning.Merknader
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.satser.Satskategori

internal data class PersistertMånedsberegning(
    val sumYtelse: Int,
    val sumFradrag: Double,
    val benyttetGrunnbeløp: Int,
    val sats: Satskategori,
    val satsbeløp: Double,
    val fradrag: List<PersistertFradrag>,
    val periode: MånedJson,
    val fribeløpForEps: Double,
    val merknader: List<PersistertMerknad.Beregning> = emptyList(),
) {
    fun toMånedsberegning(satsFactory: SatsFactory, opprettet: Tidspunkt): BeregningForMåned {
        val måned = periode.tilMåned()
        return BeregningForMåned(
            måned = måned,
            fullSupplerendeStønadForMåned = satsFactory
                /**
                 * For å håndtere at grunnbeløpet kan ha endret seg siden beregningen ble gjort, må vi bruke
                 * satsfactory slik den så ut ved opprettelse av beregningen for å få hentet ut korrekt data.
                 */
                .gjeldende(påDato = opprettet.toLocalDate(zoneIdOslo))
                .forSatskategori(
                    måned = måned,
                    satskategori = sats,
                ).also {
                    assert(benyttetGrunnbeløp == it.grunnbeløp.grunnbeløpPerÅr) {
                        "Hentet benyttetGrunnbeløp: $benyttetGrunnbeløp fra databasen, mens den utleda verdien for grunnbeløp var: ${it.grunnbeløp.grunnbeløpPerÅr}"
                    }
                    assert(satsbeløp == it.satsForMånedAsDouble)
                    assert(sats == it.satskategori)
                },
            fradrag = fradrag.map { it.toFradragForMåned() },
            fribeløpForEps = fribeløpForEps,
            merknader = Merknader.Beregningsmerknad(merknader.map { it.toDomain() }.toMutableList()),
            sumYtelse = sumYtelse,
            sumFradrag = sumFradrag,
        )
    }
}

/** Database-representasjon til serialisering */
internal fun Månedsberegning.toJson(): PersistertMånedsberegning {
    return PersistertMånedsberegning(
        sumYtelse = getSumYtelse(),
        sumFradrag = getSumFradrag(),
        benyttetGrunnbeløp = getBenyttetGrunnbeløp(),
        sats = getSats(),
        satsbeløp = getSatsbeløp(),
        fradrag = getFradrag().map { it.toJson() },
        periode = måned.toJson(),
        fribeløpForEps = getFribeløpForEps(),
        merknader = getMerknader().toSnapshot(),
    )
}
