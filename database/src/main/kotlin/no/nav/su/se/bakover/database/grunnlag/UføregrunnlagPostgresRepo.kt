package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.BehandlingUføregrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.Uføregrad
import java.util.UUID
import javax.sql.DataSource

internal class UføregrunnlagPostgresRepo(
    private val dataSource: DataSource
) : UføregrunnlagRepo {

    override fun lagre(behandlingId: UUID, uføregrunnlag: List<BehandlingUføregrunnlag>) {
        dataSource.withTransaction { tx ->
            uføregrunnlag.forEach {
                lagre(it, tx)
                koble(behandlingId, it.id, tx)
            }
        }
    }

    override fun hent(behandlingId: UUID): List<BehandlingUføregrunnlag> {
        return dataSource.withSession { sesison ->
            """
                select * from grunnlag_uføre gu
                join behandling_grunnlag bg
                    on bg.uføre_grunnlag_id = gu.id
                    and bg.behandlingId = :behandlingId
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "behandlingId" to behandlingId
                    ),
                    sesison
                ) {
                    it.toUføregrunnlag()
                }
        }
    }

    override fun slett(uføregrunnlagId: UUID) {
        dataSource.withSession { session ->
            """
                delete from grunnlag_uføre where id = :id
            """.trimIndent()
                .oppdatering(
                    mapOf(
                        "id" to uføregrunnlagId
                    ),
                    session
                )
        }
    }

    private fun Row.toUføregrunnlag(): BehandlingUføregrunnlag {
        return BehandlingUføregrunnlag(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            periode = Periode.create(localDate("fom"), localDate("tom")),
            uføregrad = Uføregrad.parse(int("uføregrad")),
            forventetInntekt = int("forventetInntekt")
        )
    }

    private fun lagre(uføregrunnlag: BehandlingUføregrunnlag, session: Session) {
        """
            insert into grunnlag_uføre
            (
                id,
                opprettet,
                fom,
                tom,
                uføregrad,
                forventetInntekt
            ) values 
            (
                :id,
                :opprettet,
                :fom,
                :tom,
                :uforegrad,
                :forventetInntekt
            )
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to uføregrunnlag.id,
                    "opprettet" to uføregrunnlag.opprettet,
                    "fom" to uføregrunnlag.periode.getFraOgMed(),
                    "tom" to uføregrunnlag.periode.getTilOgMed(),
                    "uforegrad" to uføregrunnlag.uføregrad.value,
                    "forventetInntekt" to uføregrunnlag.forventetInntekt
                ),
                session
            )
    }

    private fun koble(behandlingId: UUID, uføregrunnlagId: UUID, session: Session) {
        """
            insert into behandling_grunnlag
            (
                behandlingId,
                uføre_grunnlag_id
            ) values 
            (
                :behandlingId,
                :ufore_grunnlag_id
            )
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                    "ufore_grunnlag_id" to uføregrunnlagId
                ),
                session
            )
    }
}
