package no.nav.su.se.bakover.database.vedtak

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.Vedtak.Avslag
import javax.sql.DataSource

class VedtakPostgresRepo(val dataSource: DataSource) : VedtakRepo {
    override fun opprettVedtak(vedtak: Vedtak) {
        dataSource.withSession { session ->
            """
            insert into vedtak(id,opprettet,type,versjon,json) values (:id,:opprettet,:type,:versjon, to_json(:vedtak::json))
        """.oppdatering(
                mapOf(
                    "id" to vedtak.id,
                    "opprettet" to vedtak.opprettet,
                    "type" to when (vedtak) {
                        is Avslag -> "avslag"
                    },
                    "versjon" to vedtak.versjon,
                    "vedtak" to objectMapper.writeValueAsString(vedtak)
                ),
                session
            )
        }
    }
}
