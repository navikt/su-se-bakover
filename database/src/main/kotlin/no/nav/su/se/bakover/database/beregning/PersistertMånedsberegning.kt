package no.nav.su.se.bakover.database.beregning

import no.nav.su.se.bakover.common.infrastructure.MånedJson
import no.nav.su.se.bakover.common.infrastructure.MånedJson.Companion.toJson
import no.nav.su.se.bakover.domain.beregning.BeregningForMåned
import no.nav.su.se.bakover.domain.beregning.Merknader
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.satser.Satskategori
import org.slf4j.LoggerFactory

internal data class PersistertMånedsberegning(
    val sumYtelse: Int,
    val sumFradrag: Double,
    val benyttetGrunnbeløp: Int?, // bare relevant for uføre
    val sats: Satskategori,
    val satsbeløp: Double,
    val fradrag: List<PersistertFradrag>,
    // Siden denne serialiseres/deserialiseres kan man ikke rename periode uten migrering eller annotasjoner.
    val periode: MånedJson,
    val fribeløpForEps: Double,
    val merknader: List<PersistertMerknad.Beregning> = emptyList(),
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun toMånedsberegning(satsFactory: SatsFactory, sakstype: Sakstype, saksnummer: Saksnummer): BeregningForMåned {
        val måned = periode.tilMåned()
        return BeregningForMåned(
            måned = måned,
            fullSupplerendeStønadForMåned = when (sakstype) {
                Sakstype.ALDER -> {
                    satsFactory.forSatskategoriAlder(
                        måned = måned,
                        satskategori = sats,
                    ).also {
                        check(satsbeløp == it.satsForMånedAsDouble)
                        check(sats == it.satskategori)
                    }
                }

                Sakstype.UFØRE -> {
                    satsFactory.forSatskategoriUføre(
                        måned = måned,
                        satskategori = sats,
                    ).also {
                        // TODO jah: Disse skulle egentlig være checks, men siden vi har beregninger med satser i prod får vi prøve finne og fikse dem etterhvert som de dukker opp.
                        if (benyttetGrunnbeløp != it.grunnbeløp.grunnbeløpPerÅr) {
                            log.warn(
                                "Saksnummer $saksnummer: Hentet benyttetGrunnbeløp: $benyttetGrunnbeløp fra databasen, mens den utleda verdien for grunnbeløp var: ${it.grunnbeløp.grunnbeløpPerÅr}",
                                RuntimeException("Genererer en stacktrace for enklere debugging."),
                            )
                        }
                        if (satsbeløp != it.satsForMånedAsDouble) {
                            log.warn(
                                "Saksnummer $saksnummer: Hentet satsbeløp $satsbeløp fra databasen, mens den utleda verdien for satsForMånedAsDouble var: ${it.satsForMånedAsDouble}",
                                RuntimeException("Genererer en stacktrace for enklere debugging."),
                            )
                        }
                        require(sats == it.satskategori) {
                            "Hentet sats $sats fra databasen, mens den utleda verdien for satskategori var: ${it.satskategori}"
                        }
                    }
                }
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
