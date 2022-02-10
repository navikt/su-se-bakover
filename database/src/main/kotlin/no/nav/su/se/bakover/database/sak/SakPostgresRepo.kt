package no.nav.su.se.bakover.database.sak

import arrow.core.NonEmptyList
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo
import no.nav.su.se.bakover.database.uuidOrNull
import no.nav.su.se.bakover.database.vedtak.VedtakPostgresRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.domain.sak.SakIdOgNummer
import no.nav.su.se.bakover.domain.sak.SakRepo
import java.util.UUID

internal class SakPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val søknadsbehandlingRepo: SøknadsbehandlingPostgresRepo,
    private val revurderingRepo: RevurderingPostgresRepo,
    private val vedtakPostgresRepo: VedtakPostgresRepo,
    private val dbMetrics: DbMetrics,
    private val klageRepo: KlageRepo,
    private val ferdigeBehandlingerRepo: FerdigeBehandlingerRepo,
) : SakRepo {

    private val åpneBehandlingerRepo = ÅpneBehandlingerRepo(
        dbMetrics = dbMetrics,
    )

    override fun hentSak(sakId: UUID): Sak? {
        return dbMetrics.timeQuery("hentSakId") {
            sessionFactory.withSessionContext {
                hentSakInternal(sakId, it)
            }
        }
    }

    override fun hentSak(fnr: Fnr): Sak? {
        return dbMetrics.timeQuery("hentSakFnr") {
            sessionFactory.withSessionContext {
                hentSakInternal(fnr, it)
            }
        }
    }

    override fun hentSak(saksnummer: Saksnummer): Sak? {

        return dbMetrics.timeQuery("hentSakNr") {
            sessionFactory.withSessionContext {
                hentSakInternal(saksnummer, it)
            }
        }
    }

    /***
     * @param personidenter Inneholder alle identer til brukeren, f.eks fnr og aktørid.
     */
    override fun hentSakIdOgNummerForIdenter(personidenter: NonEmptyList<String>): SakIdOgNummer? {
        return sessionFactory.withSession { session ->
            """
                SELECT
                    id, saksnummer
                FROM sak
                WHERE fnr = ANY (:fnrs)
            """.trimIndent().hent(
                mapOf("fnrs" to personidenter),
                session,
            ) { row ->
                row.uuidOrNull("id")?.let { id ->
                    SakIdOgNummer(
                        sakId = id,
                        saksnummer = Saksnummer(row.long("saksnummer"))
                    )
                }
            }
        }
    }

    override fun opprettSak(sak: NySak) {
        return dbMetrics.timeQuery("opprettSak") {
            sessionFactory.withSession { session ->
                """
            with inserted_sak as (insert into sak (id, fnr, opprettet) values (:sakId, :fnr, :opprettet))
            insert into søknad (id, sakId, søknadInnhold, opprettet) values (:soknadId, :sakId, to_json(:soknad::json), :opprettet)
        """.insert(
                    mapOf(
                        "sakId" to sak.id,
                        "fnr" to sak.fnr,
                        "opprettet" to sak.opprettet,
                        "soknadId" to sak.søknad.id,
                        "soknad" to objectMapper.writeValueAsString(sak.søknad.søknadInnhold),
                    ),
                    session,
                )
            }
        }
    }

    override fun hentÅpneBehandlinger(): List<Behandlingsoversikt> {
        return sessionFactory.withSession { session ->
            åpneBehandlingerRepo.hentÅpneBehandlinger(session)
        }
    }

    override fun hentFerdigeBehandlinger(): List<Behandlingsoversikt> {
        return sessionFactory.withSession { session ->
            ferdigeBehandlingerRepo.hentFerdigeBehandlinger(session)
        }
    }

    private fun hentSakInternal(fnr: Fnr, sessionContext: SessionContext): Sak? {
        return sessionContext.withSession { session ->
            "select * from sak where fnr=:fnr"
                .hent(mapOf("fnr" to fnr.toString()), session) { it.toSak(sessionContext) }
        }
    }

    private fun hentSakInternal(sakId: UUID, sessionContext: SessionContext): Sak? {
        return sessionContext.withSession { session ->
            "select * from sak where id=:sakId"
                .hent(mapOf("sakId" to sakId), session) { it.toSak(sessionContext) }
        }
    }

    private fun hentSakInternal(saksnummer: Saksnummer, sessionContext: SessionContext): Sak? {
        return sessionContext.withSession { session ->
            "select * from sak where saksnummer=:saksnummer"
                .hent(mapOf("saksnummer" to saksnummer.nummer), session) { it.toSak(sessionContext) }
        }
    }

    private fun Row.toSak(sessionContext: SessionContext): Sak {
        return sessionContext.withSession { session ->

            val sakId = UUID.fromString(string("id"))
            Sak(
                id = sakId,
                saksnummer = Saksnummer(long("saksnummer")),
                fnr = Fnr(string("fnr")),
                opprettet = tidspunkt("opprettet"),
                søknader = SøknadRepoInternal.hentSøknaderInternal(sakId, session),
                søknadsbehandlinger = søknadsbehandlingRepo.hentForSak(sakId, sessionContext),
                utbetalinger = UtbetalingInternalRepo.hentUtbetalinger(sakId, session),
                revurderinger = revurderingRepo.hentRevurderingerForSak(sakId, session),
                vedtakListe = vedtakPostgresRepo.hentForSakId(sakId, session),
                klager = klageRepo.hentKlager(sakId, sessionContext)
            )
        }
    }
}
