package db.migration

import arrow.core.Nel
import arrow.core.NonEmptyList
import db.migration.UtbetalingRequestForMigrering.Oppdragslinje.KodeStatusLinje.Companion.tilKjøreplan
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.common.xmlMapper
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Statement
import java.sql.Types
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class V179__bump_tidspunkt_for_utbetalingslinjer : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val statement = context.connection.createStatement()

        val utbetalingslinjer = hentUtbetalingslinjer(statement)
        val utbetalinger = hentUtbetalinger(statement, utbetalingslinjer)
        require(utbetalingslinjer.size == utbetalinger.map { it.utbetalingslinjer }.size)

        utbetalinger.forEach {
            bumpTidspunkt(statement, it)
        }
    }
}

private fun Utbetalingslinje.bumpTidspunktMed1(): Utbetalingslinje {
    return when (this) {
        is Utbetalingslinje.Endring.Opphør -> this.copy(opprettet = this.opprettet.plus(1, ChronoUnit.MICROS))
        is Utbetalingslinje.Endring.Reaktivering -> this.copy(opprettet = this.opprettet.plus(1, ChronoUnit.MICROS))
        is Utbetalingslinje.Endring.Stans -> this.copy(opprettet = this.opprettet.plus(1, ChronoUnit.MICROS))
        is Utbetalingslinje.Ny -> this.copy(opprettet = this.opprettet.plus(1, ChronoUnit.MICROS))
    }
}

private fun bumpTidspunkt(statement: Statement, utbetaling: LocalUtbetaling) {
    utbetaling.request.oppdragRequest.oppdragslinjer.forEach { oppdragslinje ->
        utbetaling.utbetalingslinjer.filter { utbetalingslinje ->
            utbetalingslinje.id.toString() == oppdragslinje.delytelseId &&
                utbetalingslinje.forrigeUtbetalingslinjeId?.toString() == oppdragslinje.refDelytelseId &&
                utbetalingslinje.beløp.toString() == oppdragslinje.sats &&
                utbetalingslinje.uføregrad?.value == oppdragslinje.grad?.grad &&
                utbetalingslinje.utbetalingsinstruksjonForEtterbetalinger.let {
                when (it) {
                    UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling -> "J"
                    UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig -> "N"
                }
            } == oppdragslinje.brukKjoreplan.value &&
                utbetalingslinje.periode.fraOgMed.toString() == oppdragslinje.datoVedtakFom &&
                utbetalingslinje.periode.tilOgMed.toString() == oppdragslinje.datoVedtakTom
        }.let { filtrertUtbetalingslinjer ->
            //TODO - tidspunktet må bumpes

            if (filtrertUtbetalingslinjer.size == 1) {
                statement.connection.prepareStatement(
                    """
                        insert into utbetalingslinje (id, opprettet, fom, tom, utbetalingId, forrigeUtbetalingslinjeId, beløp, status, statusFraOgMed, statusTilOgMed, uføregrad, kjøreplan)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).let {
                    it.setString(1, filtrertUtbetalingslinjer.first().id.toString())
                    it.setString(2, filtrertUtbetalingslinjer.first().opprettet.toString())
                    it.setString(3, filtrertUtbetalingslinjer.first().periode.fraOgMed.toString())
                    it.setString(4, filtrertUtbetalingslinjer.first().periode.tilOgMed.toString())
                    it.setString(5, utbetaling.utbetalingsId.value)
                    it.setString(6, filtrertUtbetalingslinjer.first().forrigeUtbetalingslinjeId?.toString())
                    it.setInt(7, filtrertUtbetalingslinjer.first().beløp)
                    when (val x = filtrertUtbetalingslinjer.first()) {
                        is Utbetalingslinje.Endring -> it.setString(8, x.linjeStatus.toString())
                        is Utbetalingslinje.Ny -> it.setNull(8, Types.VARCHAR)
                    }
                    // status fra og med
                    when (val x = filtrertUtbetalingslinjer.first()) {
                        is Utbetalingslinje.Endring -> it.setString(8, x.periode.fraOgMed.toString())
                        is Utbetalingslinje.Ny -> it.setNull(8, Types.VARCHAR)
                    }
                    // status til og med
                    when (val x = filtrertUtbetalingslinjer.first()) {
                        is Utbetalingslinje.Endring -> it.setString(8, x.periode.tilOgMed.toString())
                        is Utbetalingslinje.Ny -> it.setNull(8, Types.VARCHAR)
                    }
                    it.setObject(11, filtrertUtbetalingslinjer.first().uføregrad?.value)
                    it.setString(12, filtrertUtbetalingslinjer.first().tilKjøreplan().value)
                }
            }
            TODO("håndter hvis det finnes flere")
        }
    }
}

private data class UtbetalingslinjeMedUtbetalingsId(
    val utbetalingsId: UUID30,
    val linje: Utbetalingslinje,
    val internLinjeId: UUID,
)

private fun hentUtbetalingslinjer(statement: Statement): NonEmptyList<UtbetalingslinjeMedUtbetalingsId> {
    val rs = statement.executeQuery("""select * from utbetalingslinje""".trimIndent())
    val utbetalingslinjer = mutableListOf<UtbetalingslinjeMedUtbetalingsId>()
    while (rs.next()) {
        val status = rs.getString("status")
        val statusFraOgMed = rs.getString("statusFraOgMed")?.let { LocalDate.parse(it) }
        val statusTilOgMed = rs.getString("statusTilOgMed")?.let { LocalDate.parse(it) }
        val kjøreplan = when (rs.getBoolean("kjøreplan")) {
            true -> UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling
            false -> UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig
        }
        val internId = UUID.fromString(rs.getString("internId"))
        val utbetalingsId = UUID30.fromString(rs.getString("utbetalingId"))
        val linje = Utbetalingslinje.Ny(
            id = UUID30.fromString(rs.getString("id")),
            fraOgMed = LocalDate.parse(rs.getString("fom")),
            tilOgMed = LocalDate.parse(rs.getString("tom")),
            opprettet = rs.getTimestamp("opprettet")!!.toInstant().toTidspunkt(),
            forrigeUtbetalingslinjeId = rs.getString("forrigeUtbetalingslinjeId")
                ?.let { UUID30.fromString(it) },
            beløp = rs.getInt("beløp"),
            uføregrad = rs.getString("uføregrad")?.let { Uføregrad.parse(it.toInt()) },
            utbetalingsinstruksjonForEtterbetalinger = when (rs.getBoolean("kjøreplan")) {
                true -> UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling
                false -> UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig
            },
        )

        if (status != null && statusFraOgMed != null && statusTilOgMed != null) {
            when (Utbetalingslinje.Endring.LinjeStatus.valueOf(status)) {
                Utbetalingslinje.Endring.LinjeStatus.OPPHØR -> utbetalingslinjer.add(
                    UtbetalingslinjeMedUtbetalingsId(
                        internLinjeId = internId,
                        utbetalingsId = utbetalingsId,
                        linje = Utbetalingslinje.Endring.Opphør(
                            id = linje.id,
                            opprettet = linje.opprettet,
                            fraOgMed = linje.originalFraOgMed(),
                            tilOgMed = linje.originalTilOgMed(),
                            forrigeUtbetalingslinjeId = linje.forrigeUtbetalingslinjeId,
                            beløp = linje.beløp,
                            virkningsperiode = Periode.create(statusFraOgMed, statusTilOgMed),
                            uføregrad = linje.uføregrad,
                            utbetalingsinstruksjonForEtterbetalinger = kjøreplan,
                        ),
                    ),
                )

                Utbetalingslinje.Endring.LinjeStatus.STANS -> utbetalingslinjer.add(
                    UtbetalingslinjeMedUtbetalingsId(
                        internLinjeId = internId,
                        utbetalingsId = utbetalingsId,
                        linje = Utbetalingslinje.Endring.Stans(
                            id = linje.id,
                            opprettet = linje.opprettet,
                            fraOgMed = linje.originalFraOgMed(),
                            tilOgMed = linje.originalTilOgMed(),
                            forrigeUtbetalingslinjeId = linje.forrigeUtbetalingslinjeId,
                            beløp = linje.beløp,
                            virkningsperiode = Periode.create(statusFraOgMed, statusTilOgMed),
                            uføregrad = linje.uføregrad,
                            utbetalingsinstruksjonForEtterbetalinger = kjøreplan,
                        ),
                    ),
                )

                Utbetalingslinje.Endring.LinjeStatus.REAKTIVERING -> utbetalingslinjer.add(
                    UtbetalingslinjeMedUtbetalingsId(
                        internLinjeId = internId,
                        utbetalingsId = utbetalingsId,
                        linje = Utbetalingslinje.Endring.Reaktivering(
                            id = linje.id,
                            opprettet = linje.opprettet,
                            fraOgMed = linje.originalFraOgMed(),
                            tilOgMed = linje.originalTilOgMed(),
                            forrigeUtbetalingslinjeId = linje.forrigeUtbetalingslinjeId,
                            beløp = linje.beløp,
                            virkningsperiode = Periode.create(statusFraOgMed, statusTilOgMed),
                            uføregrad = linje.uføregrad,
                            utbetalingsinstruksjonForEtterbetalinger = kjøreplan,
                        ),
                    ),
                )
            }
        } else {
            utbetalingslinjer.add(
                UtbetalingslinjeMedUtbetalingsId(
                    internLinjeId = internId,
                    utbetalingsId = utbetalingsId,
                    linje = linje,
                ),
            )
        }
    }

    return utbetalingslinjer.toNonEmptyList()
}

private data class LocalUtbetaling(
    val utbetalingsId: UUID30,
    val request: UtbetalingRequestForMigrering,
    val utbetalingslinjer: Nel<Utbetalingslinje>,
    val opprettet: Tidspunkt,
)

private fun hentUtbetalinger(
    statement: Statement,
    utbetalingslinjer: NonEmptyList<UtbetalingslinjeMedUtbetalingsId>,
): List<LocalUtbetaling> {
    val listeAvUtbetalinger = mutableListOf<LocalUtbetaling>()
    val rs = statement.executeQuery("""select id, opprettet, utbetalingsrequest from utbetaling""".trimIndent())

    while (rs.next()) {
        val utbetalingId = UUID30.fromString(rs.getString("id"))
        val opprettet = rs.getTimestamp("opprettet")!!.toInstant().toTidspunkt()
        val utbetalingsrequest =
            xmlMapper.readValue(rs.getString("utbetalingsrequest"), UtbetalingRequestForMigrering::class.java)

        listeAvUtbetalinger.add(
            LocalUtbetaling(
                utbetalingslinjer = utbetalingslinjer
                    .filter { it.utbetalingsId == utbetalingId }
                    .map { it.linje }
                    .toNonEmptyList(),
                utbetalingsId = utbetalingId,
                request = utbetalingsrequest,
                opprettet = opprettet,
            ),
        )
    }
    return listeAvUtbetalinger
}
