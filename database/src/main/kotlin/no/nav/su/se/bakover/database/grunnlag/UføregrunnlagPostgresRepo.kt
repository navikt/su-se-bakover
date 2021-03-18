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
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import java.util.UUID
import javax.sql.DataSource

internal class UføregrunnlagPostgresRepo(
    private val dataSource: DataSource
) : UføregrunnlagRepo {

    override fun lagre(behandlingId: UUID, uføregrunnlag: List<Grunnlag.Uføregrunnlag>) {
        dataSource.withTransaction { tx ->
            slettForBehandlingId(behandlingId)
            uføregrunnlag.forEach {
                lagre(it, tx)
                koble(behandlingId, it.id, tx)
            }
        }
    }

    override fun hent(behandlingId: UUID): List<Grunnlag.Uføregrunnlag> {
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

    private fun slettForBehandlingId(behandlingId: UUID) {
        dataSource.withSession { session ->
            """
                delete from grunnlag_uføre gu 
                using behandling_grunnlag bg 
                where bg.uføre_grunnlag_id=gu.id 
                and bg.behandlingId = :behandlingId
            """.trimIndent()
                .oppdatering(
                    mapOf(
                        "behandlingId" to behandlingId
                    ),
                    session
                )
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

    private fun Row.toUføregrunnlag(): Grunnlag.Uføregrunnlag {
        return Grunnlag.Uføregrunnlag(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            periode = Periode.create(localDate("fom"), localDate("tom")),
            uføregrad = Uføregrad.parse(int("uføregrad")),
            forventetInntekt = int("forventetInntekt")
        )
    }

    private fun lagre(uføregrunnlag: Grunnlag.Uføregrunnlag, session: Session) {
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
                    "fom" to uføregrunnlag.getPeriode().getFraOgMed(),
                    "tom" to uføregrunnlag.getPeriode().getTilOgMed(),
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
