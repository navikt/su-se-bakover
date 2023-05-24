@file:Suppress("unused", "ktlint")

package db.migration

import arrow.core.Nel
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.RekkefølgeGenerator
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.common.infrastructure.xml.xmlMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory
import java.sql.Statement
import java.util.UUID

val xmlMapperForTest: ObjectMapper = xmlMapper
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

internal class V180__utbetalingslinje_legg_på_rekkefølge : BaseJavaMigration() {
    private val log = LoggerFactory.getLogger(this::class.java)
    override fun migrate(context: Context) {
        log.info("utbetalingslinje-rekkefølge-migrering starter")
        val statement = context.connection.createStatement()

        val utbetalingslinjer = hentUtbetalingslinjer(statement)
        val utbetalinger = hentUtbetalinger(statement, utbetalingslinjer)
        require(utbetalingslinjer.size == utbetalinger.flatMap { it.utbetalingslinjer }.size)

        utbetalinger.forEach {
            leggTilRekkefølgePåUtbetalingslinjene(statement, it)
        }
        log.info("utbetalingslinje-rekkefølge-migrering er ferdig")
    }
}

private fun leggTilRekkefølgePåUtbetalingslinjene(
    statement: Statement,
    utbetaling: LocalUtbetaling,
) {
    val ubrukteUtbetalingslinjer = utbetaling.utbetalingslinjer.toMutableList()
    val rekkefølgeGenerator = RekkefølgeGenerator()
    utbetaling.request.oppdragRequest.oppdragslinjer.forEach { oppdragslinje ->
        ubrukteUtbetalingslinjer.filter { utbetalingslinje ->
            val kodeEndringLinje = when (utbetalingslinje.status) {
                null -> UtbetalingRequestForMigrering.Oppdragslinje.KodeEndringLinje.NY
                "STANS", "OPPHØR", "REAKTIVERING" -> UtbetalingRequestForMigrering.Oppdragslinje.KodeEndringLinje.ENDRING
                else -> throw IllegalStateException("Ukjent status: ${utbetalingslinje.status}")
            }
            val erEndring = kodeEndringLinje == UtbetalingRequestForMigrering.Oppdragslinje.KodeEndringLinje.ENDRING
            oppdragslinje == UtbetalingRequestForMigrering.Oppdragslinje(
                kodeEndringLinje = kodeEndringLinje,
                kodeStatusLinje = when (utbetalingslinje.status) {
                    null -> null
                    "STANS" -> UtbetalingRequestForMigrering.Oppdragslinje.KodeStatusLinje.HVIL
                    "OPPHØR" -> UtbetalingRequestForMigrering.Oppdragslinje.KodeStatusLinje.OPPHØR
                    "REAKTIVERING" -> UtbetalingRequestForMigrering.Oppdragslinje.KodeStatusLinje.REAKTIVER
                    else -> throw IllegalStateException("Ukjent status: ${utbetalingslinje.status}")
                },
                datoStatusFom = utbetalingslinje.statusFraOgMed,
                delytelseId = utbetalingslinje.id,
                kodeKlassifik = "SUUFORE",
                datoVedtakFom = utbetalingslinje.fraOgMed,
                datoVedtakTom = utbetalingslinje.tilOgMed,
                sats = utbetalingslinje.beløp,
                fradragTillegg = UtbetalingRequestForMigrering.Oppdragslinje.FradragTillegg.TILLEGG,
                typeSats = UtbetalingRequestForMigrering.Oppdragslinje.TypeSats.MND,
                brukKjoreplan = utbetalingslinje.kjøreplan.let {
                    when (it) {
                        UtbetalingRequestForMigrering.Oppdragslinje.Kjøreplan.JA -> UtbetalingRequestForMigrering.Oppdragslinje.Kjøreplan.JA
                        UtbetalingRequestForMigrering.Oppdragslinje.Kjøreplan.NEI -> UtbetalingRequestForMigrering.Oppdragslinje.Kjøreplan.NEI
                    }
                },
                saksbehId = "SU",
                utbetalesTilId = utbetaling.fnr,
                /* Kalt henvisning i selve requesten; denne ble lagt på, på et senere tidspunkt */
                utbetalingId = if(oppdragslinje.utbetalingId != null) utbetalingslinje.utbetalingId else null,
                refDelytelseId = if (!erEndring) utbetalingslinje.forrigeUtbetalingslinjeId else null,
                refFagsystemId = if (!erEndring) utbetalingslinje.forrigeUtbetalingslinjeId?.let { utbetaling.saksnummer } else null,
                grad = utbetalingslinje.uføregrad?.let {
                    UtbetalingRequestForMigrering.Oppdragslinje.Grad(
                        typeGrad = UtbetalingRequestForMigrering.Oppdragslinje.TypeGrad.UFOR,
                        grad = it,
                    )
                },
                attestant = listOf(UtbetalingRequestForMigrering.Oppdragslinje.Attestant(utbetaling.behandler)),
            )
        }.let { filtrertUtbetalingslinjer ->
            when {
                filtrertUtbetalingslinjer.isEmpty() -> {
                    sikkerLogg.error("Fant ikke igjen oppdragslinje: $oppdragslinje")
                    throw IllegalStateException("Fant ikke igjen oppdragslinje, se sikkerlogg.")
                }

                filtrertUtbetalingslinjer.size == 1 -> {
                    lagreRekkefølge(
                        statement, filtrertUtbetalingslinjer.first().internId, rekkefølgeGenerator.neste(),
                    )
                }

                else -> {
                    // Vi henter den tidligste opprettet linjen, hvis ikke henter vi bare den første i rekkefølgen vi fikk fra databasen (det vil sannsynligvis være den rekkefølgen det er insertet i)
                    val linje = filtrertUtbetalingslinjer.minByOrNull { it.opprettet.instant }!!
                    ubrukteUtbetalingslinjer.remove(linje)
                    lagreRekkefølge(statement, linje.internId, rekkefølgeGenerator.neste())
                }
            }
        }
    }
}

private fun lagreRekkefølge(
    statement: Statement,
    internId: String,
    rekkefølge: Rekkefølge,
) {
    statement.connection.prepareStatement(
        "update utbetalingslinje set rekkefølge = ? where internid = ?",
    ).let {
        it.setLong(1, rekkefølge.value)
        it.setObject(2, UUID.fromString(internId))
        it.execute()
    }
}

private fun hentUtbetalingslinjer(statement: Statement): List<LocalUtbetalingslinje> {
    val rs = statement.executeQuery("""select * from utbetalingslinje""".trimIndent())
    val utbetalingslinjer = mutableListOf<LocalUtbetalingslinje>()
    while (rs.next()) {
        val linje = LocalUtbetalingslinje(
            id = rs.getString("id"),
            internId = rs.getString("internId"),
            utbetalingId = rs.getString("utbetalingId"),
            fraOgMed = rs.getString("fom"),
            tilOgMed = rs.getString("tom"),
            // statusFraOgMed og statusTilOgMed er er kun satt ved endring
            statusFraOgMed = rs.getString("statusFraOgMed"),
            statusTilOgMed = rs.getString("statusTilOgMed"),
            opprettet = rs.getTimestamp("opprettet")!!.toInstant().toTidspunkt(),
            forrigeUtbetalingslinjeId = rs.getString("forrigeUtbetalingslinjeId"),
            beløp = rs.getString("beløp"),
            uføregrad = rs.getString("uføregrad")?.toInt(),
            kjøreplan = when (rs.getBoolean("kjøreplan")) {
                true -> UtbetalingRequestForMigrering.Oppdragslinje.Kjøreplan.JA
                false -> UtbetalingRequestForMigrering.Oppdragslinje.Kjøreplan.NEI
            },
            status = rs.getString("status"),
        )
        utbetalingslinjer.add(linje)
    }

    return utbetalingslinjer
}

private data class LocalUtbetaling(
    val utbetalingsId: UUID30,
    val request: UtbetalingRequestForMigrering,
    val utbetalingslinjer: Nel<LocalUtbetalingslinje>,
    val opprettet: Tidspunkt,
    val saksnummer: String,
    val fnr: String,
    val behandler: String,
)

/**
 * Forenklet versjon av [no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje] som mangler rekkefølge.
 */
private data class LocalUtbetalingslinje(
    val id: String,
    val utbetalingId: String,
    val internId: String,
    val fraOgMed: String,
    val tilOgMed: String,
    val opprettet: Tidspunkt,
    val forrigeUtbetalingslinjeId: String?,
    val beløp: String,
    val uføregrad: Int?,
    val kjøreplan: UtbetalingRequestForMigrering.Oppdragslinje.Kjøreplan,
    val status: String?,
    val statusFraOgMed: String?,
    val statusTilOgMed: String?,
)

private fun hentUtbetalinger(
    statement: Statement,
    utbetalingslinjer: List<LocalUtbetalingslinje>,
): List<LocalUtbetaling> {
    val listeAvUtbetalinger = mutableListOf<LocalUtbetaling>()
    val rs = statement.executeQuery(
        "select u.id, u.opprettet, u.behandler, u.utbetalingsrequest->>'value' as utbetalingsrequest, s.saksnummer, u.fnr from utbetaling u join sak s on u.sakid = s.id",
    )

    while (rs.next()) {
        val utbetalingId = UUID30.fromString(rs.getString("id"))

        listeAvUtbetalinger.add(
            LocalUtbetaling(
                utbetalingslinjer = utbetalingslinjer.filter { it.utbetalingId == utbetalingId.value }.toNonEmptyList(),
                utbetalingsId = utbetalingId,
                request = xmlMapperForTest.readValue(
                    rs.getString("utbetalingsrequest"),
                ),
                opprettet = rs.getTimestamp("opprettet")!!.toInstant().toTidspunkt(),
                saksnummer = rs.getString("saksnummer"),
                fnr = rs.getString("fnr"),
                behandler = rs.getString("behandler"),
            ),
        )
    }
    return listeAvUtbetalinger
}