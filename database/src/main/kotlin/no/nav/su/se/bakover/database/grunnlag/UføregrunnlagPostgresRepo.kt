package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.Session
import no.nav.su.se.bakover.common.persistence.TransactionalSession
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.common.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.oppdatering
import no.nav.su.se.bakover.common.persistence.tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import java.util.UUID

internal class UføregrunnlagPostgresRepo(
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: UUID, uføregrunnlag: List<Grunnlag.Uføregrunnlag>, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagreUføregrunnlag") {
            slettForBehandlingId(behandlingId, tx)
            uføregrunnlag.forEach {
                lagre(it, behandlingId, tx)
            }
        }
    }

    internal fun hentUføregrunnlagForBehandlingId(behandlingId: UUID, session: Session): List<Grunnlag.Uføregrunnlag> {
        return dbMetrics.timeQuery("hentUføregrunnlagForBehandlingId") {
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

    internal fun hentForUføregrunnlagForId(uføregrunnlagId: UUID, session: Session): Grunnlag.Uføregrunnlag? {
        return dbMetrics.timeQuery("hentForUføregrunnlagForId") {
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

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
            delete from grunnlag_uføre where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
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

    private fun lagre(uføregrunnlag: Grunnlag.Uføregrunnlag, behandlingId: UUID, tx: TransactionalSession) {
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
                tx,
            )
    }
}
