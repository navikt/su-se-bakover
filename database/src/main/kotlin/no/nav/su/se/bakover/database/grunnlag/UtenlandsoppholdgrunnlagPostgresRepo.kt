package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import java.util.UUID

internal class UtenlandsoppholdgrunnlagPostgresRepo {

    internal fun lagre(behandlingId: UUID, grunnlag: List<Utenlandsoppholdgrunnlag>, tx: TransactionalSession) {
        slettForBehandlingId(behandlingId, tx)
        grunnlag.forEach {
            lagre(it, behandlingId, tx)
        }
    }

    internal fun hentForUtenlandsoppholdgrunnlagId(id: UUID, session: Session): Utenlandsoppholdgrunnlag? {
        return """ select * from grunnlag_utland where id=:id""".trimIndent()
            .hent(
                mapOf(
                    "id" to id,
                ),
                session,
            ) {
                it.toUtenlandsoppholdgrunnlag()
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

    private fun Row.toUtenlandsoppholdgrunnlag(): Utenlandsoppholdgrunnlag {
        return Utenlandsoppholdgrunnlag(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            periode = Periode.create(localDate("fraOgMed"), localDate("tilOgMed")),
        )
    }

    private fun lagre(grunnlag: Utenlandsoppholdgrunnlag, behandlingId: UUID, tx: TransactionalSession) {
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
                    "id" to grunnlag.id,
                    "opprettet" to grunnlag.opprettet,
                    "behandlingId" to behandlingId,
                    "fraOgMed" to grunnlag.periode.fraOgMed,
                    "tilOgMed" to grunnlag.periode.tilOgMed,
                ),
                tx,
            )
    }
}
