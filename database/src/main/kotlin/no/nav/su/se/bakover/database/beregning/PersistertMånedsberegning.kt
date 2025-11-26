package no.nav.su.se.bakover.database.beregning

import beregning.domain.BeregningForMåned
import beregning.domain.BeregningForMånedRegelspesifisert
import beregning.domain.Merknader
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.MånedJson
import no.nav.su.se.bakover.common.infrastructure.MånedJson.Companion.toJson
import no.nav.su.se.bakover.common.tid.Tidspunkt
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import satser.domain.Satskategori
import java.math.RoundingMode

/**
 * @param benyttetGrunnbeløp Bare relevant for uføre.
 * @param periode Siden denne serialiseres/deserialiseres kan man ikke rename periode uten migrering eller annotasjoner.
 */
internal data class PersistertMånedsberegning(
    val sumYtelse: Int,
    val sumFradrag: Double,
    val benyttetGrunnbeløp: Int?,
    val sats: Satskategori,
    val satsbeløp: Double,
    val fradrag: List<PersistertFradrag>,
    val periode: MånedJson,
    val fribeløpForEps: Double,
    val merknader: List<PersistertMerknad.Beregning> = emptyList(),
    val benyttetRegel: RegelspesifiseringJson?,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /** @param erAvbrutt brukes for å bestemme om vi skal logge mismatch i satsene */
    fun toMånedsberegning(
        satsFactory: SatsFactory,
        sakstype: Sakstype,
        saksnummer: Saksnummer,
        erAvbrutt: Boolean?,
    ): BeregningForMånedRegelspesifisert {
        val måned = periode.tilMåned()
        return BeregningForMåned(
            måned = måned,
            fullSupplerendeStønadForMåned = when (sakstype) {
                Sakstype.ALDER -> {
                    satsFactory.forSatskategoriAlder(
                        måned = måned,
                        satskategori = sats,
                    ).also {
                        if (!satsbeløp.isEqualToTwoDecimals(it.satsForMånedAsDouble) && erAvbrutt != true) {
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

                Sakstype.UFØRE -> {
                    satsFactory.forSatskategoriUføre(
                        måned = måned,
                        satskategori = sats,
                    ).also {
                        if (benyttetGrunnbeløp != it.grunnbeløp.grunnbeløpPerÅr && erAvbrutt != true) {
                            log.warn(
                                "Saksnummer $saksnummer: Hentet benyttetGrunnbeløp: $benyttetGrunnbeløp fra databasen, mens den utleda verdien for grunnbeløp var: ${it.grunnbeløp.grunnbeløpPerÅr}",
                                RuntimeException("Genererer en stacktrace for enklere debugging."),
                            )
                        }
                        if (!satsbeløp.isEqualToTwoDecimals(it.satsForMånedAsDouble) && erAvbrutt != true) {
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
            merknader = Merknader.Beregningsmerknad(merknader.mapNotNull { it.toDomain() }.toMutableList()),
            sumYtelse = sumYtelse,
            sumFradrag = sumFradrag,
        ).let {
            BeregningForMånedRegelspesifisert(
                verdi = it,
                benyttetRegel = benyttetRegel?.toDomain() ?: Regelspesifisering.BeregnetUtenSpesifisering,
            )
        }
    }
}

/** Database-representasjon til serialisering */
internal fun BeregningForMånedRegelspesifisert.toJson(): PersistertMånedsberegning {
    return with(verdi) {
        PersistertMånedsberegning(
            sumYtelse = getSumYtelse(),
            sumFradrag = getSumFradrag(),
            benyttetGrunnbeløp = getBenyttetGrunnbeløp(),
            sats = getSats(),
            satsbeløp = getSatsbeløp(),
            fradrag = getFradrag().map { it.toJson() },
            periode = måned.toJson(),
            fribeløpForEps = getFribeløpForEps(),
            merknader = getMerknader().toSnapshot(),
            benyttetRegel = benyttetRegel.toJson(),
        )
    }
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = RegelspesifiseringJson.Beregning::class, name = "Beregning"),
    JsonSubTypes.Type(value = RegelspesifiseringJson.Grunnlag::class, name = "Grunnlag"),
)
sealed interface RegelspesifiseringJson {

    fun toDomain(): Regelspesifisering

    data class Beregning(
        val kode: String,
        val versjon: String,
        val benyttetTidspunkt: Tidspunkt,
        val verdi: String,
        val avhengigeRegler: List<RegelspesifiseringJson>,
    ) : RegelspesifiseringJson {
        override fun toDomain() = Regelspesifisering.Beregning(
            kode = kode,
            versjon = versjon,
            benyttetTidspunkt = benyttetTidspunkt,
            verdi = verdi,
            avhengigeRegler = avhengigeRegler.map {
                it.toDomain()
            },
        )
    }

    data class Grunnlag(
        val kode: String,
        val versjon: String,
        val benyttetTidspunkt: Tidspunkt,
        val verdi: String,
        val kilde: String,
    ) : RegelspesifiseringJson {
        override fun toDomain() = Regelspesifisering.Grunnlag(
            kode = kode,
            versjon = versjon,
            benyttetTidspunkt = benyttetTidspunkt,
            verdi = verdi,
            kilde = kilde,
        )
    }
}

internal fun Regelspesifisering.toJson(): RegelspesifiseringJson {
    return when (this) {
        is Regelspesifisering.Beregning -> RegelspesifiseringJson.Beregning(
            kode = kode,
            versjon = versjon,
            benyttetTidspunkt = benyttetTidspunkt,
            verdi = verdi,
            avhengigeRegler = avhengigeRegler.map {
                it.toJson()
            },
        )

        is Regelspesifisering.Grunnlag -> RegelspesifiseringJson.Grunnlag(
            kode = kode,
            versjon = versjon,
            benyttetTidspunkt = benyttetTidspunkt,
            verdi = verdi,
            kilde = kilde,
        )

        Regelspesifisering.BeregnetUtenSpesifisering -> TODO()
    }
}

private fun Double.isEqualToTwoDecimals(other: Double): Boolean {
    return this.toBigDecimal().setScale(2, RoundingMode.HALF_UP) == other.toBigDecimal()
        .setScale(2, RoundingMode.HALF_UP)
}
