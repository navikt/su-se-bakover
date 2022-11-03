package no.nav.su.se.bakover.database.sak

import arrow.core.NonEmptyList
import kotliquery.Row
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.common.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.tidspunkt
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo
import no.nav.su.se.bakover.database.vedtak.VedtakPostgresRepo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.sak.Behandlingsoversikt
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon.Companion.max
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdRepo
import java.util.UUID

internal class SakPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val søknadsbehandlingRepo: SøknadsbehandlingPostgresRepo,
    private val revurderingRepo: RevurderingPostgresRepo,
    private val vedtakPostgresRepo: VedtakPostgresRepo,
    private val klageRepo: KlageRepo,
    private val reguleringRepo: ReguleringRepo,
    private val avkortingsvarselRepo: AvkortingsvarselPostgresRepo,
    private val utenlandsoppholdRepo: UtenlandsoppholdRepo,
    private val hendelseRepo: HendelseRepo,
) : SakRepo {

    private val åpneBehandlingerRepo = ÅpneBehandlingerRepo(
        dbMetrics = dbMetrics,
    )

    private val ferdigeBehandlingerRepo = FerdigeBehandlingerRepo(
        dbMetrics = dbMetrics,
    )

    override fun hentSak(sakId: UUID): Sak? {
        return dbMetrics.timeQuery("hentSakForId") {
            sessionFactory.withSessionContext {
                hentSakInternal(sakId, it)
            }
        }
    }

    override fun hentSak(sakId: UUID, sessionContext: SessionContext): Sak? {
        return dbMetrics.timeQuery("hentSakForId") {
            hentSakInternal(sakId, sessionContext)
        }
    }

    override fun hentSak(fnr: Fnr, type: Sakstype): Sak? {
        return dbMetrics.timeQuery("hentSakForFnr") {
            sessionFactory.withSessionContext {
                hentSakInternal(fnr, type, it)
            }
        }
    }

    override fun hentSaker(fnr: Fnr): List<Sak> {
        return dbMetrics.timeQuery("hentSakerForFnr") {
            sessionFactory.withSessionContext {
                hentSakerInternal(fnr, it)
            }
        }
    }

    override fun hentSak(saksnummer: Saksnummer): Sak? {
        return dbMetrics.timeQuery("hentSakForSaksnummer") {
            sessionFactory.withSessionContext {
                hentSakInternal(saksnummer, it)
            }
        }
    }

    override fun hentSakForRevurdering(revurderingId: UUID): Sak {
        return dbMetrics.timeQuery("hentSakForRevurdering") {
            sessionFactory.withSessionContext { sessionContext ->
                sessionContext.withSession { session ->
                    "select s.* from sak s join revurdering r on r.sakid = s.id where r.id =:revurderingid".hent(
                        mapOf("revurderingid" to revurderingId),
                        session,
                    ) { it.toSak(sessionContext) }
                }
            }!!
        }
    }

    override fun hentSakForRevurdering(revurderingId: UUID, sessionContext: SessionContext): Sak {
        return dbMetrics.timeQuery("hentSakForRevurdering") {
            sessionContext.withSession { session ->
                "select s.* from sak s join revurdering r on r.sakid = s.id where r.id =:revurderingid"
                    .hent(mapOf("revurderingid" to revurderingId), session) { it.toSak(sessionContext) }
            }
        }!!
    }

    override fun hentSakforSøknadsbehandling(søknadsbehandlingId: UUID): Sak {
        return dbMetrics.timeQuery("hentSakForSøknadsbehandling") {
            sessionFactory.withSessionContext { sessionContext ->
                sessionContext.withSession { session ->
                    "select s.* from sak s join behandling b on s.id = b.sakid where b.id = :soknadsbehandlingId".hent(
                        mapOf("soknadsbehandlingId" to søknadsbehandlingId),
                        session,
                    ) { it.toSak(sessionContext) }!!
                }
            }
        }
    }

    override fun hentSakForSøknad(søknadId: UUID): Sak? {
        return dbMetrics.timeQuery("hentSakForSøknad") {
            sessionFactory.withSessionContext { sessionContext ->
                sessionContext.withSession { session ->
                    "select s.* from sak s join søknad ss on ss.sakid = s.id where ss.id =:soknadId".hent(
                        mapOf("soknadId" to søknadId),
                        session,
                    ) { it.toSak(sessionContext) }
                }
            }
        }
    }

    /***
     * @param personidenter Inneholder alle identer til brukeren, f.eks fnr og aktørid.
     */
    override fun hentSakInfoForIdenter(personidenter: NonEmptyList<String>): SakInfo? {
        return dbMetrics.timeQuery("hentSakIdOgNummerForIdenter") {
            sessionFactory.withSession { session ->
                """
                SELECT
                    id, saksnummer, fnr, type
                FROM sak
                WHERE fnr = ANY (:fnrs)
                """.trimIndent().hent(
                    mapOf("fnrs" to personidenter),
                    session,
                ) { row -> row.toSakInfo() }
            }
        }
    }

    override fun hentSakInfo(sakId: UUID): SakInfo? {
        return dbMetrics.timeQuery("hentSakInfoForSakId") {
            sessionFactory.withSession { session ->
                """
                SELECT
                    id, saksnummer, fnr, type
                FROM sak
                WHERE id = :id
                """.trimIndent().hent(
                    mapOf("id" to sakId),
                    session,
                ) { row -> row.toSakInfo() }
            }
        }
    }

    private fun Row.toSakInfo(): SakInfo {
        return SakInfo(
            sakId = uuid("id"),
            saksnummer = Saksnummer(long("saksnummer")),
            fnr = Fnr(string("fnr")),
            type = Sakstype.from(string("type")),
        )
    }

    override fun opprettSak(sak: NySak) {
        return dbMetrics.timeQuery("opprettSak") {
            sessionFactory.withSession { session ->
                """
                with inserted_sak as (insert into sak (id, fnr, opprettet, type) values (:sakId, :fnr, :opprettet, :type))
                insert into søknad (id, sakId, søknadInnhold, opprettet, ident) values (:soknadId, :sakId, to_json(:soknad::json), :opprettet, :ident)
                """.insert(
                    mapOf(
                        "sakId" to sak.id,
                        "fnr" to sak.fnr,
                        "opprettet" to sak.opprettet,
                        "soknadId" to sak.søknad.id,
                        "soknad" to serialize(sak.søknad.søknadInnhold),
                        "type" to sak.søknad.type.value,
                        "ident" to sak.søknad.innsendtAv.navIdent,
                    ),
                    session,
                )
            }
        }
    }

    override fun hentÅpneBehandlinger(): List<Behandlingsoversikt> {
        return dbMetrics.timeQuery("hentÅpneBehandlinger") {
            sessionFactory.withSession { session ->
                åpneBehandlingerRepo.hentÅpneBehandlinger(session)
            }
        }
    }

    override fun hentFerdigeBehandlinger(): List<Behandlingsoversikt> {
        return dbMetrics.timeQuery("hentFerdigeBehandlinger") {
            sessionFactory.withSession { session ->
                ferdigeBehandlingerRepo.hentFerdigeBehandlinger(session)
            }
        }
    }

    override fun hentSakIdSaksnummerOgFnrForAlleSaker(): List<SakInfo> = sessionFactory.withSession { session ->
        """ select id, saksnummer, fnr, type from sak
        """.trimMargin().hentListe(
            mapOf(),
            session,
        ) {
            it.toSakInfo()
        }
    }

    override fun hentSakerSomVenterPåForhåndsvarsling(): List<Saksnummer> {
        return sessionFactory.withSession { session ->
            """
                select distinct s.saksnummer
                from revurdering
                         join behandling_vedtak bv on revurdering.vedtaksomrevurderesid = bv.vedtakid
                         join sak s on bv.sakid = s.id
                where revurdering.forhåndsvarsel ->> 'type' = :type;
            """.trimIndent().hentListe(mapOf("type" to "Sendt"), session) { Saksnummer(it.long("saksnummer")) }
        }
    }

    private fun hentSakInternal(fnr: Fnr, type: Sakstype, sessionContext: SessionContext): Sak? {
        return dbMetrics.timeQuery("hentSakInternalForFnr") {
            sessionContext.withSession { session ->
                "select * from sak where fnr=:fnr and type=:type".hent(
                    mapOf(
                        "fnr" to fnr.toString(),
                        "type" to type.value,
                    ),
                    session,
                ) { it.toSak(sessionContext) }
            }
        }
    }

    private fun hentSakInternal(sakId: UUID, sessionContext: SessionContext): Sak? {
        return dbMetrics.timeQuery("hentSakInternalForSakId") {
            sessionContext.withSession { session ->
                "select * from sak where id=:sakId".hent(mapOf("sakId" to sakId), session) { it.toSak(sessionContext) }
            }
        }
    }

    private fun hentSakInternal(saksnummer: Saksnummer, sessionContext: SessionContext): Sak? {
        return dbMetrics.timeQuery("hentSakInternalForSaksnummer") {
            sessionContext.withSession { session ->
                "select * from sak where saksnummer=:saksnummer".hent(
                    mapOf("saksnummer" to saksnummer.nummer),
                    session,
                ) { it.toSak(sessionContext) }
            }
        }
    }

    private fun hentSakerInternal(fnr: Fnr, sessionContext: SessionContext): List<Sak> {
        return dbMetrics.timeQuery("hentSakerInternalForFnr") {
            sessionContext.withSession { session ->
                "select * from sak where fnr=:fnr".hentListe(mapOf("fnr" to fnr.toString()), session) {
                    it.toSak(
                        sessionContext,
                    )
                }
            }
        }
    }

    private fun Row.toSak(sessionContext: SessionContext): Sak {
        return sessionContext.withSession { session ->
            val sakId = UUID.fromString(string("id"))
            Sak(
                id = sakId,
                saksnummer = Saksnummer(long("saksnummer")),
                opprettet = tidspunkt("opprettet"),
                fnr = Fnr(string("fnr")),
                søknader = SøknadRepoInternal.hentSøknaderInternal(sakId, session),
                søknadsbehandlinger = søknadsbehandlingRepo.hentForSak(sakId, sessionContext),
                utbetalinger = UtbetalingInternalRepo.hentUtbetalinger(sakId, session),
                revurderinger = revurderingRepo.hentRevurderingerForSak(sakId, session),
                vedtakListe = vedtakPostgresRepo.hentForSakId(sakId, session),
                klager = klageRepo.hentKlager(sakId, sessionContext),
                reguleringer = reguleringRepo.hentForSakId(sakId, sessionContext),
                type = Sakstype.from(string("type")),
                uteståendeAvkorting = avkortingsvarselRepo.hentUteståendeAvkorting(sakId, session),
                utenlandsopphold = utenlandsoppholdRepo.hentForSakId(sakId, sessionContext).currentState,
                // Siden vi ikke har migrert SAK_OPPRETTET-hendelser, vil vi ikke alltid ha en hendelse knyttet til denne saken. Vi reserverer da den aller første hendelsesversjonen til SAK_OPPRETTET.
                // Det betyr at etter hvert som vi migrerer sak/søknad osv. til hendelser vil versjonene kunne være out of order. En mulighet er da og bumpe alle versjoner tilsvarende med antall hendelser vi migrerer. Dette fungerer bare så lenge ingen andre tabeller/systemer har lagret hendelsesversjonene våre.
                versjon = max(hendelseRepo.hentSisteVersjonFraEntitetId(sakId, sessionContext), Hendelsesversjon(1)),
            )
        }
    }
}
