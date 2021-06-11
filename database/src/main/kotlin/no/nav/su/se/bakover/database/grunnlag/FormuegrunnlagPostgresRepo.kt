package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import java.util.UUID
import javax.sql.DataSource

internal class FormuegrunnlagPostgresRepo(
    private val dataSource: DataSource,
) {
    internal fun lagreFormuegrunnlag(
        behandlingId: UUID,
        formuegrunnlag: List<Formuegrunnlag>,
        tx: TransactionalSession,
    ) {
        slettForBehandlingId(behandlingId, tx)
        formuegrunnlag.forEach {
            lagre(it, behandlingId, tx)
        }
    }

    internal fun hentForFormuegrunnlagId(formuegrunnlagId: UUID, session: Session): Formuegrunnlag? {
        return """
                select * from grunnlag_formue where id = :id
        """.trimIndent()
            .hent(
                mapOf(
                    "id" to formuegrunnlagId,
                ),
                session,
            ) {
                it.toFormuegrunnlag()
            }
    }

    private fun slettForBehandlingId(behandlingId: UUID, session: Session) {
        """
            delete from grunnlag_formue where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                session,
            )
    }

    private fun Row.toFormuegrunnlag(): Formuegrunnlag {
        return Formuegrunnlag(
            id = uuid("id"),
            periode = Periode.create(fraOgMed = localDate("fraOgMed"), tilOgMed = localDate("tilOgMed")),
            opprettet = tidspunkt("opprettet"),
            epsFormue = stringOrNull("epsFormue")?.let { deserialize(it) },
            søkersFormue = deserialize(string("søkerFormue")),
            begrunnelse = "begrunnelse",
        )
    }

    private fun lagre(formuegrunnlag: Formuegrunnlag, behandlingId: UUID, session: Session) {
        """
            insert into grunnlag_formue
            (
                id,
                opprettet,
                behandlingId,
                fraOgMed,
                tilOgMed,
                tilhører,
                epsFormue,
                søkerFormue,
                begrunnelse
            ) values
            (
                :id,
                :opprettet,
                :behandlingId,
                :fraOgMed,
                :tilOgMed,
                :tilhører,
                to_jsonb(:epsFormue::json),
                to_jsonb(:søkerFormue::json),
                :begrunnelse
            )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to formuegrunnlag.id,
                    "opprettet" to formuegrunnlag.opprettet,
                    "behandlingId" to behandlingId,
                    "fraOgMed" to formuegrunnlag.periode.fraOgMed,
                    "tilOgMed" to formuegrunnlag.periode.tilOgMed,
                    "epsFormue" to objectMapper.writeValueAsString(formuegrunnlag.epsFormue),
                    "søkerFormue" to objectMapper.writeValueAsString(formuegrunnlag.søkersFormue),
                    "begrunnelse" to formuegrunnlag.begrunnelse,
                ),
                session,
            )
    }
}
