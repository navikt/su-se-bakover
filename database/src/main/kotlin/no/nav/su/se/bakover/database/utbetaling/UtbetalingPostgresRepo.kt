package no.nav.su.se.bakover.database.utbetaling

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.simulering.serializeSimulering
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.Utbetalinger
import økonomi.domain.utbetaling.UtbetalingsinstruksjonForEtterbetalinger
import økonomi.domain.utbetaling.Utbetalingslinje
import java.util.UUID

internal class UtbetalingPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : UtbetalingRepo {
    override fun hentOversendtUtbetalingForUtbetalingId(
        utbetalingId: UUID30,
        sessionContext: SessionContext?,
    ): Utbetaling.OversendtUtbetaling? {
        return dbMetrics.timeQuery("hentUtbetalingForId") {
            (sessionContext ?: sessionFactory.newSessionContext()).withSession { session ->
                UtbetalingInternalRepo.hentOversendtUtbetaling(
                    utbetalingId,
                    session,
                )
            }
        }
    }

    override fun hentOversendtUtbetalingForAvstemmingsnøkkel(
        avstemmingsnøkkel: Avstemmingsnøkkel,
    ): Utbetaling.OversendtUtbetaling? {
        return dbMetrics.timeQuery("hentUtbetalingForAvstemmingsnøkkel") {
            sessionFactory.withSession { session ->
                "select u.*, s.saksnummer, s.type as sakstype from utbetaling u join sak s on s.id = u.sakId where u.avstemmingsnøkkel ->> 'nøkkel' = :nokkel order by u.opprettet".hent(
                    mapOf(
                        "nokkel" to avstemmingsnøkkel.toString(),
                    ),
                    session,
                ) { it.toUtbetaling(session) }
            }
        }
    }

    override fun hentOversendteUtbetalinger(
        sakId: UUID,
        disableSessionCounter: Boolean,
    ): Utbetalinger {
        return dbMetrics.timeQuery("hentUtbetalingerForSakId") {
            sessionFactory.withSession(disableSessionCounter = disableSessionCounter) { session -> UtbetalingInternalRepo.hentOversendteUtbetalinger(sakId, session) }
        }.let { Utbetalinger(it) }
    }

    override fun hentUkvitterteUtbetalinger(): List<Utbetaling.OversendtUtbetaling.UtenKvittering> {
        return dbMetrics.timeQuery("hentUkvitterteUtbetalinger") {
            sessionFactory.withSession { session ->
                "select u.*, s.saksnummer, s.type as sakstype from utbetaling u join sak s on s.id = u.sakId where u.kvittering is null order by u.opprettet".hentListe(
                    session = session,
                ) { it.toUtbetaling(session) as Utbetaling.OversendtUtbetaling.UtenKvittering }
            }
        }
    }

    override fun oppdaterMedKvittering(
        utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
        sessionContext: SessionContext?,
    ) {
        dbMetrics.timeQuery("oppdaterUtbetalingMedKvittering") {
            (sessionContext ?: sessionFactory.newSessionContext()).withSession { session ->
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

    /**
     * Støtter både med og uten kvittering, førstnevte for enklere testing.
     */
    override fun opprettUtbetaling(
        utbetaling: Utbetaling.OversendtUtbetaling,
        transactionContext: TransactionContext,
    ) {
        dbMetrics.timeQuery("opprettUtbetaling") {
            transactionContext.withTransaction { session ->
                """
            insert into utbetaling (id, opprettet, sakId, fnr, avstemmingsnøkkel, simulering, utbetalingsrequest, behandler, kvittering)
            values (:id, :opprettet, :sakId, :fnr, to_json(:avstemmingsnokkel::json), to_json(:simulering::json), to_json(:utbetalingsrequest::json), :behandler, to_json(:kvittering::json))
                """.insert(
                    mapOf(
                        "id" to utbetaling.id,
                        "opprettet" to utbetaling.opprettet,
                        "sakId" to utbetaling.sakId,
                        "fnr" to utbetaling.fnr,
                        "avstemmingsnokkel" to serialize(utbetaling.avstemmingsnøkkel),
                        "simulering" to utbetaling.simulering.serializeSimulering(),
                        "utbetalingsrequest" to serialize(utbetaling.utbetalingsrequest),
                        "behandler" to utbetaling.behandler.navIdent,
                        "kvittering" to (utbetaling as? Utbetaling.OversendtUtbetaling.MedKvittering)?.kvittering?.let { serialize(it) },
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
            "rekkefolge" to utbetalingslinje.rekkefølge.value,
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
            insert into utbetalingslinje (id, opprettet, fom, tom, utbetalingId, forrigeUtbetalingslinjeId, beløp, status, statusFraOgMed, statusTilOgMed, uføregrad, kjøreplan, rekkefølge)
            values (:id, :opprettet, :fom, :tom, :utbetalingId, :forrigeUtbetalingslinjeId, :belop, :status, :statusFraOgMed, :statusTilOgMed, :uforegrad, :kjoreplan, :rekkefolge)
        """.insert(params, session)

        return utbetalingslinje
    }

    override fun defaultTransactionContext(): TransactionContext = sessionFactory.newTransactionContext()
}
