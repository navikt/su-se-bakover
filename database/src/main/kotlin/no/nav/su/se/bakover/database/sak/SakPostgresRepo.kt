package no.nav.su.se.bakover.database.sak

import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepoInternal
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.utbetaling.UtbetalingInternalRepo
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import java.util.UUID
import javax.sql.DataSource

internal class SakPostgresRepo(
    private val dataSource: DataSource,
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val revurderingRepo: RevurderingPostgresRepo
) : SakRepo {
    override fun hentSak(sakId: UUID) = dataSource.withSession { hentSakInternal(sakId, it) }
    override fun hentSak(fnr: Fnr) = dataSource.withSession { hentSakInternal(fnr, it) }
    override fun hentSak(saksnummer: Saksnummer) = dataSource.withSession { hentSakInternal(saksnummer, it) }


    override fun opprettSak(sak: NySak) {
        dataSource.withSession { session ->
            """
            with inserted_sak as (insert into sak (id, fnr, opprettet) values (:sakId, :fnr, :opprettet))
            insert into søknad (id, sakId, søknadInnhold, opprettet) values (:soknadId, :sakId, to_json(:soknad::json), :opprettet)
        """.oppdatering(
                mapOf(
                    "sakId" to sak.id,
                    "fnr" to sak.fnr,
                    "opprettet" to sak.opprettet,
                    "soknadId" to sak.søknad.id,
                    "soknad" to objectMapper.writeValueAsString(sak.søknad.søknadInnhold)
                ),
                session
            )
        }
    }

    internal fun hentSakInternal(fnr: Fnr, session: Session): Sak? = "select * from sak where fnr=:fnr"
        .hent(mapOf("fnr" to fnr.toString()), session) { it.toSak(session) }

    internal fun hentSakInternal(sakId: UUID, session: Session): Sak? = "select * from sak where id=:sakId"
        .hent(mapOf("sakId" to sakId), session) { it.toSak(session) }

    internal fun hentSakInternal(saksnummer: Saksnummer, session: Session): Sak? = "select * from sak where saksnummer=:saksnummer"
        .hent(mapOf("saksnummer" to saksnummer.nummer), session) { it.toSak(session) }

    internal fun Row.toSak(session: Session): Sak {
        val sakId = UUID.fromString(string("id"))
        return Sak(
            id = sakId,
            saksnummer = Saksnummer(long("saksnummer")),
            fnr = Fnr(string("fnr")),
            opprettet = tidspunkt("opprettet"),
            søknader = SøknadRepoInternal.hentSøknaderInternal(sakId, session),
            behandlinger = søknadsbehandlingRepo.hentForSak(sakId, session),
            utbetalinger = UtbetalingInternalRepo.hentUtbetalinger(sakId, session),
            revurderinger = revurderingRepo.hentRevurderingerForSak(sakId, session)
        )
    }
}
