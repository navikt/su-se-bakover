package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import java.util.UUID
import javax.sql.DataSource

internal class UføregrunnlagPostgresRepo(
    private val dataSource: DataSource,
) : UføregrunnlagRepo {

    internal fun lagre(behandlingId: UUID, uføregrunnlag: List<Grunnlag.Uføregrunnlag>, session: Session) {
        slettForBehandlingId(behandlingId, session)
        uføregrunnlag.forEach {
            lagre(it, behandlingId, session)
        }
    }

    override fun hentUføregrunnlag(behandlingId: UUID): List<Grunnlag.Uføregrunnlag> {
        return dataSource.withSession { session ->
            """
                select * from grunnlag_uføre where behandlingId = :behandlingId
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "behandlingId" to behandlingId,
                    ),
                    session,
                ) {
                    it.toUføregrunnlag()
                }
        }
    }

    override fun hentForUføregrunnlagId(uføregrunnlagId: UUID): Grunnlag.Uføregrunnlag? {
        return dataSource.withSession { session ->
            """ select * from grunnlag_uføre where id=:id""".trimIndent()
                .hent(
                    mapOf(
                        "id" to uføregrunnlagId,
                    ),
                    session,
                ) {
                    it.toUføregrunnlag()
                }
        }
    }

    private fun slettForBehandlingId(behandlingId: UUID, session: Session) {
        """
            delete from grunnlag_uføre where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                session,
            )
    }

    private fun Row.toUføregrunnlag(): Grunnlag.Uføregrunnlag {
        return Grunnlag.Uføregrunnlag(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            periode = Periode.create(localDate("fraOgMed"), localDate("tilOgMed")),
            uføregrad = Uføregrad.parse(int("uføregrad")),
            forventetInntekt = int("forventetInntekt"),
        )
    }

    private fun lagre(uføregrunnlag: Grunnlag.Uføregrunnlag, behandlingId: UUID, session: Session) {
        """
            insert into grunnlag_uføre
            (
                id,
                opprettet,
                behandlingId,
                fraOgMed,
                tilOgMed,
                uføregrad,
                forventetInntekt
            ) values 
            (
                :id,
                :opprettet,
                :behandlingId,
                :fraOgMed,
                :tilOgMed,
                :uforegrad,
                :forventetInntekt
            )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to uføregrunnlag.id,
                    "opprettet" to uføregrunnlag.opprettet,
                    "behandlingId" to behandlingId,
                    "fraOgMed" to uføregrunnlag.periode.fraOgMed,
                    "tilOgMed" to uføregrunnlag.periode.tilOgMed,
                    "uforegrad" to uføregrunnlag.uføregrad.value,
                    "forventetInntekt" to uføregrunnlag.forventetInntekt,
                ),
                session,
            )
    }
}
