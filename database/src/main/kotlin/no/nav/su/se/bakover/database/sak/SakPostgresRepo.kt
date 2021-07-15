package no.nav.su.se.bakover.database.sak

import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo
import no.nav.su.se.bakover.database.vedtak.VedtakPosgresRepo
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Restans
import java.util.UUID
import javax.sql.DataSource

internal class SakPostgresRepo(
    private val dataSource: DataSource,
    private val søknadsbehandlingRepo: SøknadsbehandlingPostgresRepo,
    private val revurderingRepo: RevurderingPostgresRepo,
    private val vedtakPosgresRepo: VedtakPosgresRepo,
    private val dbMetrics: DbMetrics,
) : SakRepo {
    private val sakRestansRepo = SakRestansRepo(
        dataSource = dataSource,
        dbMetrics = dbMetrics,
    )

    override fun hentSak(sakId: UUID): Sak? {
        return dbMetrics.timeQuery("hentSakId") {
            dataSource.withSession { hentSakInternal(sakId, it) }
        }
    }

    override fun hentSak(fnr: Fnr): Sak? {
        return dbMetrics.timeQuery("hentSakFnr") {
            dataSource.withSession { hentSakInternal(fnr, it) }
        }
    }

    override fun hentSak(saksnummer: Saksnummer): Sak? {
        return dbMetrics.timeQuery("hentSakNr") {
            dataSource.withSession { hentSakInternal(saksnummer, it) }
        }
    }

    override fun opprettSak(sak: NySak) {
        return dbMetrics.timeQuery("opprettSak") {
            dataSource.withSession { session ->
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

    override fun hentRestanser(): List<Restans> {
        return sakRestansRepo.hentRestanser()
    }

    private fun hentSakInternal(fnr: Fnr, session: Session): Sak? = "select * from sak where fnr=:fnr"
        .hent(mapOf("fnr" to fnr.toString()), session) { it.toSak(session) }

    private fun hentSakInternal(sakId: UUID, session: Session): Sak? = "select * from sak where id=:sakId"
        .hent(mapOf("sakId" to sakId), session) { it.toSak(session) }

    private fun hentSakInternal(saksnummer: Saksnummer, session: Session): Sak? =
        "select * from sak where saksnummer=:saksnummer"
            .hent(mapOf("saksnummer" to saksnummer.nummer), session) { it.toSak(session) }

    private fun Row.toSak(session: Session): Sak {
        val sakId = UUID.fromString(string("id"))
        return Sak(
            id = sakId,
            saksnummer = Saksnummer(long("saksnummer")),
            fnr = Fnr(string("fnr")),
            opprettet = tidspunkt("opprettet"),
            søknader = SøknadRepoInternal.hentSøknaderInternal(sakId, session),
            behandlinger = søknadsbehandlingRepo.hentForSak(sakId, session),
            utbetalinger = UtbetalingInternalRepo.hentUtbetalinger(sakId, session),
            revurderinger = revurderingRepo.hentRevurderingerForSak(sakId, session),
            vedtakListe = vedtakPosgresRepo.hentForSakId(sakId, session),
        )
    }
}
