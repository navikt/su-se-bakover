package no.nav.su.se.bakover.database.vedtak.snapshot

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot.Avslag
import javax.sql.DataSource

class VedtakssnapshotPostgresRepo(val dataSource: DataSource) : VedtakssnapshotRepo {
    override fun opprettVedtakssnapshot(vedtakssnapshot: Vedtakssnapshot) {
        dataSource.withSession { session ->
            """
            insert into vedtakssnapshot(id,opprettet,vedtakstype,json) values (:id,:opprettet,:vedtakstype, to_json(:vedtak::json))
        """.oppdatering(
                mapOf(
                    "id" to vedtakssnapshot.id,
                    "opprettet" to vedtakssnapshot.opprettet,
                    "vedtakstype" to when (vedtakssnapshot) {
                        is Avslag -> "avslag"
                    },
                    "vedtak" to objectMapper.writeValueAsString(vedtakssnapshot)
                ),
                session
            )
        }
    }
}
