package no.nav.su.se.bakover.database.statistikk

import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.tid.toTidspunkt
import no.nav.su.se.bakover.domain.statistikk.DatapakkeSøknad
import no.nav.su.se.bakover.domain.statistikk.DatapakkeSøknadstype
import no.nav.su.se.bakover.domain.statistikk.SøknadStatistikkRepo
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class SøknadStatistikkRepoImpl(
    private val sessionFactory: PostgresSessionFactory,
) : SøknadStatistikkRepo {
    override fun hentSøknaderAvType(): List<DatapakkeSøknad> {
        return sessionFactory.withTransaction { tx ->
            //language=SQL
            """
            select
                id, 
                opprettet, 
                (søknadinnhold -> 'forNav' ->> 'type') as type,
                (søknadinnhold -> 'forNav' ->> 'mottaksdatoForSøknad') as mottaksdato
             from 
                søknad
            """.trimIndent()
                .hentListe(session = tx) { row ->
                    val opprettet = row.sqlTimestamp("opprettet").toInstant().toTidspunkt()
                    DatapakkeSøknad(
                        id = UUID.fromString(row.string("id")),
                        opprettet = opprettet,
                        type = DatapakkeSøknadstype.stringToSøknadstype(row.string("type")),
                        mottaksdato = row.stringOrNull("mottaksdato")?.let { LocalDate.parse(it) }
                            ?: opprettet.toLocalDate(ZoneId.of("Europe/Oslo")),
                    )
                }
        }
    }
}
