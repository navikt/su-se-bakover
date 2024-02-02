package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.behandling.BehandlingsId
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.uføre.domain.Uføregrad
import vilkår.uføre.domain.Uføregrunnlag
import java.util.UUID

internal class UføregrunnlagPostgresRepo(
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: BehandlingsId, uføregrunnlag: List<Uføregrunnlag>, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagreUføregrunnlag") {
            slettForBehandlingId(behandlingId, tx)
            uføregrunnlag.forEach {
                lagre(it, behandlingId, tx)
            }
        }
    }

    internal fun hentUføregrunnlagForBehandlingId(behandlingId: UUID, session: Session): List<Uføregrunnlag> {
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

    internal fun hentForUføregrunnlagForId(uføregrunnlagId: UUID, session: Session): Uføregrunnlag? {
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

    private fun slettForBehandlingId(behandlingId: BehandlingsId, tx: TransactionalSession) {
        """
            delete from grunnlag_uføre where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId.value,
                ),
                tx,
            )
    }

    private fun Row.toUføregrunnlag(): Uføregrunnlag {
        return Uføregrunnlag(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            periode = Periode.create(localDate("fraOgMed"), localDate("tilOgMed")),
            uføregrad = Uføregrad.parse(int("uføregrad")),
            forventetInntekt = int("forventetInntekt"),
        )
    }

    private fun lagre(uføregrunnlag: Uføregrunnlag, behandlingId: BehandlingsId, tx: TransactionalSession) {
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
                    "behandlingId" to behandlingId.value,
                    "fraOgMed" to uføregrunnlag.periode.fraOgMed,
                    "tilOgMed" to uføregrunnlag.periode.tilOgMed,
                    "uforegrad" to uføregrunnlag.uføregrad.value,
                    "forventetInntekt" to uføregrunnlag.forventetInntekt,
                ),
                tx,
            )
    }
}
