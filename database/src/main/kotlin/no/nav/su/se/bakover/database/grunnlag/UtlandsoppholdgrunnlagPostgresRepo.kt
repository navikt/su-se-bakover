package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.grunnlag.OppholdIUtlandetGrunnlag
import java.util.UUID

internal class UtlandsoppholdgrunnlagPostgresRepo {

    internal fun lagre(behandlingId: UUID, utlandsoppholdgrunnlag: List<OppholdIUtlandetGrunnlag>, tx: TransactionalSession) {
        slettForBehandlingId(behandlingId, tx)
        utlandsoppholdgrunnlag.forEach {
            lagre(it, behandlingId, tx)
        }
    }

    internal fun hentUtlandsoppholdgrunnlag(behandlingId: UUID, session: Session): List<OppholdIUtlandetGrunnlag> {
        return """
                select * from grunnlag_utland where behandlingId = :behandlingId
        """.trimIndent()
            .hentListe(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                session,
            ) {
                it.toUtlandsoppholdgrunnlag()
            }
    }

    internal fun hentForUtlandsoppholdgrunnlagId(utlandsoppholdgrunnlagId: UUID, session: Session): OppholdIUtlandetGrunnlag? {
        return """ select * from grunnlag_utland where id=:id""".trimIndent()
            .hent(
                mapOf(
                    "id" to utlandsoppholdgrunnlagId,
                ),
                session,
            ) {
                it.toUtlandsoppholdgrunnlag()
            }
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
            delete from grunnlag_utland where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    private fun Row.toUtlandsoppholdgrunnlag(): OppholdIUtlandetGrunnlag {
        return OppholdIUtlandetGrunnlag(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            periode = Periode.create(localDate("fraOgMed"), localDate("tilOgMed")),
        )
    }

    private fun lagre(utlandgrunnlag: OppholdIUtlandetGrunnlag, behandlingId: UUID, tx: TransactionalSession) {
        """
            insert into grunnlag_utland
            (
                id,
                opprettet,
                behandlingId,
                fraOgMed,
                tilOgMed
            ) values 
            (
                :id,
                :opprettet,
                :behandlingId,
                :fraOgMed,
                :tilOgMed
            )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to utlandgrunnlag.id,
                    "opprettet" to utlandgrunnlag.opprettet,
                    "behandlingId" to behandlingId,
                    "fraOgMed" to utlandgrunnlag.periode.fraOgMed,
                    "tilOgMed" to utlandgrunnlag.periode.tilOgMed,
                ),
                tx,
            )
    }
}
