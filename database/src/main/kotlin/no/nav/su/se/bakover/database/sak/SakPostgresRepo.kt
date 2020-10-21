package no.nav.su.se.bakover.database.sak

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.sak.SakRepoInternal.hentSakInternal
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import java.util.UUID
import javax.sql.DataSource

internal class SakPostgresRepo(
    private val dataSource: DataSource
) : SakRepo {
    override fun hentSak(sakId: UUID) = dataSource.withSession { hentSakInternal(sakId, it) }
    override fun hentSak(fnr: Fnr) = dataSource.withSession { hentSakInternal(fnr, it) }

    override fun opprettSak(sak: NySak) {
        dataSource.withSession { session ->
            """
            with inserted_sak as (insert into sak (id, fnr, opprettet) values (:sakId, :fnr, :opprettet))
            , inserted_oppdrag as (insert into oppdrag (id, opprettet, sakId) values (:oppdragId, :opprettet, :sakId)) 
            insert into søknad (id, sakId, søknadInnhold, opprettet) values (:soknadId, :sakId, to_json(:soknad::json), :opprettet)
        """.oppdatering(
                mapOf(
                    "sakId" to sak.id,
                    "fnr" to sak.fnr,
                    "opprettet" to sak.opprettet,
                    "oppdragId" to sak.oppdrag.id,
                    "soknadId" to sak.søknad.id,
                    "soknad" to objectMapper.writeValueAsString(sak.søknad.søknadInnhold)
                ),
                session
            )
        }
    }
}
