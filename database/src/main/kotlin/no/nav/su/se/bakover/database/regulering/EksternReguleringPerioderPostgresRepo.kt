package no.nav.su.se.bakover.database.regulering

import kotliquery.Row
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.regulering.EksternKilde
import no.nav.su.se.bakover.domain.regulering.EksternPeriode
import no.nav.su.se.bakover.domain.regulering.EksternReguleringPerioder
import no.nav.su.se.bakover.domain.regulering.EksternReguleringPerioderRepo
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.util.UUID

class EksternReguleringPerioderPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : EksternReguleringPerioderRepo {

    override fun lagre(perioder: List<EksternReguleringPerioder>) {
        if (perioder.isEmpty()) return
        dbMetrics.timeQuery("lagreEksternReguleringPerioder") {
            sessionFactory.withSession { session ->
                val sql = """
                            insert into ekstern_regulering_perioder (
                            id,
                            kjoering_id,
                            saksnummer,
                            tilhoerer,
                            ekstern_kilde,
                            perioder,
                            feilkoder
                        ) values (
                            :id,
                            :kjoering_id,
                            :saksnummer,
                            :tilhoerer,
                            :ekstern_kilde,
                            to_jsonb(:perioder::jsonb),
                            to_jsonb(:feilkoder::jsonb)
                        )
                """.trimIndent()

                val rows = perioder.map { periode ->
                    mapOf(
                        "id" to UUID.randomUUID(),
                        "kjoering_id" to periode.kjøringId,
                        "saksnummer" to periode.saksnummer.nummer,
                        "tilhoerer" to periode.tilhører.name,
                        "ekstern_kilde" to periode.eksternKilde.name,
                        "perioder" to serialize(periode.perioder),
                        "feilkoder" to serialize(periode.feilkoder),
                    )
                }

                session.batchPreparedNamedStatement(sql, rows)
            }
        }
    }

    override fun hentForKjøring(kjøringId: UUID): List<EksternReguleringPerioder> {
        return sessionFactory.withSession { session ->
            """
                select * from ekstern_regulering_perioder where kjoering_id = :kjoering_id
            """.trimIndent().hentListe(
                params = mapOf("kjoering_id" to kjøringId),
                session = session,
            ) { row -> row.toEksternReguleringPerioder() }
        }
    }
}

private fun Row.toEksternReguleringPerioder(): EksternReguleringPerioder = EksternReguleringPerioder(
    kjøringId = uuid("kjoering_id"),
    saksnummer = Saksnummer(long("saksnummer")),
    tilhører = FradragTilhører.valueOf(string("tilhoerer")),
    eksternKilde = EksternKilde.valueOf(string("ekstern_kilde")),
    perioder = string("perioder").deserializeList<EksternPeriode>(),
    feilkoder = string("feilkoder").deserializeList<String>(),
)
