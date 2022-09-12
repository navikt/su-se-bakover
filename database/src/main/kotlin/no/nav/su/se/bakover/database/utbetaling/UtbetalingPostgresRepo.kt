package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import java.util.UUID

internal class UtbetalingPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : UtbetalingRepo {
    override fun hentUtbetaling(utbetalingId: UUID30): Utbetaling.OversendtUtbetaling? {
        return dbMetrics.timeQuery("hentUtbetalingForId") {
            sessionFactory.withSession { session ->
                UtbetalingInternalRepo.hentUtbetalingInternal(
                    utbetalingId,
                    session,
                )
            }
        }
    }

    override fun hentUtbetaling(avstemmingsnøkkel: Avstemmingsnøkkel): Utbetaling.OversendtUtbetaling? {
        return dbMetrics.timeQuery("hentUtbetalingForAvstemmingsnøkkel") {
            sessionFactory.withSession { session ->
                "select u.*, s.saksnummer, s.type as sakstype from utbetaling u left join sak s on s.id = u.sakId where u.avstemmingsnøkkel ->> 'nøkkel' = :nokkel".hent(
                    mapOf(
                        "nokkel" to avstemmingsnøkkel.toString(),
                    ),
                    session,
                ) { it.toUtbetaling(session) }
            }
        }
    }

    override fun hentUtbetalinger(sakId: UUID): List<Utbetaling> {
        return dbMetrics.timeQuery("hentUtbetalingerForSakId") {
            sessionFactory.withSession { session -> UtbetalingInternalRepo.hentUtbetalinger(sakId, session) }
        }
    }

    override fun hentUkvitterteUtbetalinger(): List<Utbetaling.OversendtUtbetaling.UtenKvittering> {
        return dbMetrics.timeQuery("hentUkvitterteUtbetalinger") {
            sessionFactory.withSession { session ->
                "select u.*, s.saksnummer, s.type as sakstype from utbetaling u left join sak s on s.id = u.sakId where u.kvittering is null".hentListe(
                    session = session,
                ) { it.toUtbetaling(session) as Utbetaling.OversendtUtbetaling.UtenKvittering }
            }
        }
    }

    override fun oppdaterMedKvittering(utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering) {
        dbMetrics.timeQuery("oppdaterUtbetalingMedKvittering") {
            sessionFactory.withSession { session ->
                "update utbetaling set kvittering = to_json(:kvittering::json) where id = :id".oppdatering(
                    mapOf(
                        "id" to utbetaling.id,
                        "kvittering" to serialize(utbetaling.kvittering),
                    ),
                    session,
                )
            }
        }
    }

    override fun opprettUtbetaling(utbetaling: Utbetaling.OversendtUtbetaling.UtenKvittering, transactionContext: TransactionContext) {
        dbMetrics.timeQuery("opprettUtbetaling") {
            transactionContext.withTransaction { session ->
                """
            insert into utbetaling (id, opprettet, sakId, fnr, avstemmingsnøkkel, simulering, utbetalingsrequest, behandler)
            values (:id, :opprettet, :sakId, :fnr, to_json(:avstemmingsnokkel::json), to_json(:simulering::json), to_json(:utbetalingsrequest::json), :behandler)
                """.insert(
                    mapOf(
                        "id" to utbetaling.id,
                        "opprettet" to utbetaling.opprettet,
                        "sakId" to utbetaling.sakId,
                        "fnr" to utbetaling.fnr,
                        "avstemmingsnokkel" to serialize(utbetaling.avstemmingsnøkkel),
                        "simulering" to serialize(utbetaling.simulering),
                        "utbetalingsrequest" to serialize(utbetaling.utbetalingsrequest),
                        "behandler" to utbetaling.behandler.navIdent,
                    ),
                    session,
                )
                utbetaling.utbetalingslinjer.forEach { opprettUtbetalingslinje(utbetaling.id, it, session) }
            }
        }
    }

    private fun opprettUtbetalingslinje(
        utbetalingId: UUID30,
        utbetalingslinje: Utbetalingslinje,
        session: Session,
    ): Utbetalingslinje {
        val baseParams = mapOf(
            "id" to utbetalingslinje.id,
            "opprettet" to utbetalingslinje.opprettet,
            "fom" to utbetalingslinje.originalFraOgMed(),
            "tom" to utbetalingslinje.originalTilOgMed(),
            "utbetalingId" to utbetalingId,
            "forrigeUtbetalingslinjeId" to utbetalingslinje.forrigeUtbetalingslinjeId,
            "belop" to utbetalingslinje.beløp,
            "uforegrad" to utbetalingslinje.uføregrad?.value,
            "kjoreplan" to when (utbetalingslinje.utbetalingsinstruksjonForEtterbetalinger) {
                UtbetalingsinstruksjonForEtterbetalinger.SammenMedNestePlanlagteUtbetaling -> true
                UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig -> false
            },
        )

        val params = when (utbetalingslinje) {
            is Utbetalingslinje.Endring -> {
                baseParams.plus(
                    mapOf(
                        "status" to utbetalingslinje.linjeStatus,
                        "statusFraOgMed" to utbetalingslinje.periode.fraOgMed,
                        "statusTilOgMed" to utbetalingslinje.periode.tilOgMed,
                    ),
                )
            }
            is Utbetalingslinje.Ny -> baseParams
        }
        """
            insert into utbetalingslinje (id, opprettet, fom, tom, utbetalingId, forrigeUtbetalingslinjeId, beløp, status, statusFraOgMed, statusTilOgMed, uføregrad, kjøreplan)
            values (:id, :opprettet, :fom, :tom, :utbetalingId, :forrigeUtbetalingslinjeId, :belop, :status, :statusFraOgMed, :statusTilOgMed, :uforegrad, :kjoreplan)
        """.insert(params, session)

        return utbetalingslinje
    }

    override fun defaultTransactionContext(): TransactionContext = sessionFactory.newTransactionContext()
}
