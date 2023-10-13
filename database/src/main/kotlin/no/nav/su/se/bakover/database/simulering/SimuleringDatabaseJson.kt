package no.nav.su.se.bakover.database.simulering

import com.fasterxml.jackson.annotation.JsonAlias
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import no.nav.su.se.bakover.database.simulering.SimuleringDatabaseJson.Companion.toDatabaseJson
import no.nav.su.se.bakover.database.simulering.SimuleringDatabaseJson.Periode.Companion.toDatabaseJson
import no.nav.su.se.bakover.database.simulering.SimuleringDatabaseJson.Periode.Utbetaling.Companion.toDatabaseJson
import no.nav.su.se.bakover.database.simulering.SimuleringDatabaseJson.Periode.Utbetaling.Detaljer.Companion.toDatabaseJson
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimulertDetaljer
import økonomi.domain.simulering.SimulertMåned
import økonomi.domain.simulering.SimulertUtbetaling
import java.time.LocalDate

/**
 * Deserialiserer en simulering fra database json.
 */
fun String.deserializeSimulering(): Simulering {
    return deserialize<SimuleringDatabaseJson>(this).toDomain()
}

internal fun String?.deserializeNullableSimulering(): Simulering? {
    return this?.deserializeSimulering()
}

/**
 * Serialiserer en simulering til database json.
 */
internal fun Simulering.serializeSimulering(): String {
    return serialize(this.toDatabaseJson())
}

internal fun Simulering?.serializeNullableSimulering(): String? {
    return this?.serializeSimulering()
}

internal data class SimuleringDatabaseJson(
    val gjelderId: String,
    val gjelderNavn: String,
    val datoBeregnet: String,
    val nettoBeløp: Int,
    val periodeList: List<Periode>,
    val rawResponse: String = "Simuleringen utført før feltet rawResponse ble lagt til",
) {
    fun toDomain(): Simulering {
        return Simulering(
            gjelderId = Fnr(gjelderId),
            gjelderNavn = gjelderNavn,
            datoBeregnet = LocalDate.parse(datoBeregnet),
            nettoBeløp = nettoBeløp,
            måneder = periodeList.flatMap { it.toDomain() },
            rawResponse = rawResponse,
        )
    }

    companion object {
        fun Simulering.toDatabaseJson(): SimuleringDatabaseJson {
            return SimuleringDatabaseJson(
                gjelderId = this.gjelderId.toString(),
                gjelderNavn = this.gjelderNavn,
                datoBeregnet = this.datoBeregnet.toString(),
                nettoBeløp = this.nettoBeløp,
                periodeList = this.måneder.map { it.toDatabaseJson() },
                rawResponse = this.rawResponse,
            )
        }
    }

    data class Periode(
        @JsonAlias("fraOgMed", "fom")
        val fraOgMed: String,
        @JsonAlias("tilOgMed", "tom")
        val tilOgMed: String,
        val utbetaling: List<Utbetaling>,
    ) {
        fun toDomain(): List<SimulertMåned> {
            val nullableUtbetaling = utbetaling.map { it.toDomain() }.let {
                when {
                    it.isEmpty() -> null
                    it.size == 1 -> it.first()
                    else -> {
                        val saksnummer = it.first().fagSystemId
                        throw IllegalStateException("Simulering inneholder flere utbetalinger for samme sak $saksnummer. Se sikkerlogg for flere detaljer og feilmelding.").also {
                            sikkerLogg.error("Simulering inneholder flere utbetalinger for samme sak $saksnummer. Se vanlig logg for stacktrace. json-periode: $this")
                        }
                    }
                }
            }
            // Vi har fram til juni 2023 slått samme hele simuleringsperioden til et Periode-objekt. Etter dette vil vi lagre en liste med måneder (en periode er maks en måned)
            val periode = no.nav.su.se.bakover.common.tid.periode.Periode.create(
                LocalDate.parse(fraOgMed),
                LocalDate.parse(tilOgMed),
            )
            return if (periode.getAntallMåneder() == 1) {
                listOf(
                    SimulertMåned(
                        måned = periode.tilMåned(),
                        utbetaling = nullableUtbetaling,
                    ),
                )
            } else {
                check(nullableUtbetaling == null) {
                    "En utbetaling går på tvers av flere måneder. Dette skal ikke skje."
                }
                SimulertMåned.create(periode)
            }
        }

        companion object {
            fun SimulertMåned.toDatabaseJson(): Periode {
                return Periode(
                    fraOgMed = this.måned.fraOgMed.toString(),
                    tilOgMed = this.måned.tilOgMed.toString(),
                    utbetaling = this.utbetaling?.let { listOf(it.toDatabaseJson()) } ?: emptyList(),
                )
            }
        }

        data class Utbetaling(
            val fagSystemId: String,
            val utbetalesTilId: String,
            val utbetalesTilNavn: String,
            val forfall: String,
            val feilkonto: Boolean,
            val detaljer: List<Detaljer>,
        ) {
            fun toDomain(): SimulertUtbetaling {
                return SimulertUtbetaling(
                    fagSystemId = fagSystemId,
                    utbetalesTilId = Fnr(utbetalesTilId),
                    utbetalesTilNavn = utbetalesTilNavn,
                    forfall = LocalDate.parse(forfall),
                    feilkonto = feilkonto,
                    detaljer = detaljer.map { it.toDomain() },
                )
            }

            companion object {
                fun SimulertUtbetaling.toDatabaseJson(): Utbetaling {
                    return Utbetaling(
                        fagSystemId = this.fagSystemId,
                        utbetalesTilId = this.utbetalesTilId.toString(),
                        utbetalesTilNavn = this.utbetalesTilNavn,
                        forfall = this.forfall.toString(),
                        feilkonto = this.feilkonto,
                        detaljer = this.detaljer.map { it.toDatabaseJson() },
                    )
                }
            }

            data class Detaljer(
                @JsonAlias("faktiskFraOgMed", "faktiskFom")
                val faktiskFraOgMed: String,
                @JsonAlias("faktiskTilOgMed", "faktiskTom")
                val faktiskTilOgMed: String,
                val konto: String,
                val belop: Int,
                val tilbakeforing: Boolean,
                val sats: Int,
                val typeSats: String,
                val antallSats: Int,
                val uforegrad: Int,
                val klassekode: String,
                val klassekodeBeskrivelse: String,
                val klasseType: String,
            ) {
                fun toDomain(): SimulertDetaljer {
                    return SimulertDetaljer(
                        faktiskFraOgMed = LocalDate.parse(faktiskFraOgMed),
                        faktiskTilOgMed = LocalDate.parse(faktiskTilOgMed),
                        konto = konto,
                        belop = belop,
                        tilbakeforing = tilbakeforing,
                        sats = sats,
                        typeSats = typeSats,
                        antallSats = antallSats,
                        uforegrad = uforegrad,
                        klassekode = klassekode.toKlasseKode(),
                        klassekodeBeskrivelse = klassekodeBeskrivelse,
                        klasseType = klasseType.toKlasseType(),
                    )
                }

                companion object {
                    fun SimulertDetaljer.toDatabaseJson(): Detaljer {
                        return Detaljer(
                            faktiskFraOgMed = this.faktiskFraOgMed.toString(),
                            faktiskTilOgMed = this.faktiskTilOgMed.toString(),
                            konto = this.konto,
                            belop = this.belop,
                            tilbakeforing = this.tilbakeforing,
                            sats = this.sats,
                            typeSats = this.typeSats,
                            antallSats = this.antallSats,
                            uforegrad = this.uforegrad,
                            klassekode = this.klassekode.toDatabaseString(),
                            klassekodeBeskrivelse = this.klassekodeBeskrivelse,
                            klasseType = this.klasseType.toDatabaseString(),
                        )
                    }

                    private fun KlasseType.toDatabaseString(): String {
                        return when (this) {
                            KlasseType.YTEL -> "YTEL"
                            KlasseType.FEIL -> "FEIL"
                            KlasseType.MOTP -> "MOTP"
                            // Domenemessig forventer vi ikke denne, men det er ikke databaselaget sin jobb å validere det.
                            KlasseType.SKAT -> "SKAT"
                        }
                    }

                    private fun String.toKlasseType(): KlasseType {
                        return when (this) {
                            "YTEL" -> KlasseType.YTEL
                            "FEIL" -> KlasseType.FEIL
                            "MOTP" -> KlasseType.MOTP
                            "SKAT" -> KlasseType.SKAT
                            else -> throw IllegalArgumentException("KlasseType $this er ikke støttet")
                        }
                    }

                    private fun KlasseKode.toDatabaseString(): String {
                        return when (this) {
                            KlasseKode.SUUFORE -> "SUUFORE"
                            KlasseKode.KL_KODE_FEIL_INNT -> "KL_KODE_FEIL_INNT"
                            KlasseKode.TBMOTOBS -> "TBMOTOBS"
                            KlasseKode.UFOREUT -> "UFOREUT"

                            // Forhåndsreservert av su-alder
                            KlasseKode.SUALDER -> "SUALDER"
                            KlasseKode.KL_KODE_FEIL -> "KL_KODE_FEIL"

                            // Domenemessig forventer vi ikke denne, men det er ikke databaselaget sin jobb å validere det.
                            KlasseKode.FSKTSKAT -> "FSKTSKAT"
                        }
                    }

                    private fun String.toKlasseKode(): KlasseKode {
                        return when (this) {
                            "SUUFORE" -> KlasseKode.SUUFORE
                            "KL_KODE_FEIL_INNT" -> KlasseKode.KL_KODE_FEIL_INNT
                            "TBMOTOBS" -> KlasseKode.TBMOTOBS
                            "UFOREUT" -> KlasseKode.UFOREUT
                            "SUALDER" -> KlasseKode.SUALDER
                            "KL_KODE_FEIL" -> KlasseKode.KL_KODE_FEIL
                            "FSKTSKAT" -> KlasseKode.FSKTSKAT
                            else -> throw IllegalArgumentException("KlasseKode $this er ikke støttet")
                        }
                    }
                }
            }
        }
    }
}
