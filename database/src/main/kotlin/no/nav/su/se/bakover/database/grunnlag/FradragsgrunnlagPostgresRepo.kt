package no.nav.su.se.bakover.database.grunnlag

import arrow.core.getOrElse
import beregning.domain.fradrag.FradragForPeriode
import beregning.domain.fradrag.FradragTilhører
import beregning.domain.fradrag.Fradragstype
import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.serializeNullable
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

internal class FradragsgrunnlagPostgresRepo(
    private val dbMetrics: DbMetrics,
) {
    internal fun lagreFradragsgrunnlag(
        behandlingId: UUID,
        fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
        tx: TransactionalSession,
    ) {
        dbMetrics.timeQuery("lagreFradragsgrunnlag") {
            slettForBehandlingId(behandlingId, tx)
            fradragsgrunnlag.forEach {
                lagre(it, behandlingId, tx)
            }
        }
    }

    internal fun hentFradragsgrunnlag(behandlingId: UUID, session: Session): List<Grunnlag.Fradragsgrunnlag> {
        return dbMetrics.timeQuery("hentFradragsgrunnlag") {
            """
                select * from grunnlag_fradrag where behandlingId = :behandlingId
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "behandlingId" to behandlingId,
                    ),
                    session,
                ) {
                    it.toFradragsgrunnlag()
                }
        }
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
            delete from grunnlag_fradrag where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    private fun Row.toFradragsgrunnlag(): Grunnlag.Fradragsgrunnlag {
        return Grunnlag.Fradragsgrunnlag.tryCreate(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            fradrag = FradragForPeriode(
                fradragstype = Fradragstype.tryParse(
                    value = string("fradragstype"),
                    beskrivelse = stringOrNull("beskrivelse"),
                ).getOrElse { throw IllegalArgumentException("$it") },
                månedsbeløp = double("månedsbeløp"),
                utenlandskInntekt = stringOrNull("utenlandskInntekt")?.let { deserialize(it) },
                periode = Periode.create(fraOgMed = localDate("fraOgMed"), tilOgMed = localDate("tilOgMed")),
                tilhører = FradragTilhører.valueOf(string("tilhører")),
            ),
        ).getOrNull()!!
    }

    private fun lagre(fradragsgrunnlag: Grunnlag.Fradragsgrunnlag, behandlingId: UUID, tx: TransactionalSession) {
        """
            insert into grunnlag_fradrag
            (
                id,
                opprettet,
                behandlingId,
                fraOgMed,
                tilOgMed,
                fradragstype,
                beskrivelse,
                månedsbeløp,
                utenlandskInntekt,
                tilhører
            ) values
            (
                :id,
                :opprettet,
                :behandlingId,
                :fraOgMed,
                :tilOgMed,
                :fradragstype,
                :beskrivelse,
                :manedsbelop,
                to_json(:utenlandskInntekt::json),
                :tilhorer
            )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to fradragsgrunnlag.id,
                    "opprettet" to fradragsgrunnlag.opprettet,
                    "behandlingId" to behandlingId,
                    "fraOgMed" to fradragsgrunnlag.fradrag.periode.fraOgMed,
                    "tilOgMed" to fradragsgrunnlag.fradrag.periode.tilOgMed,
                    "fradragstype" to fradragsgrunnlag.fradrag.fradragstype.kategori,
                    "beskrivelse" to when (val f = fradragsgrunnlag.fradragstype) {
                        is Fradragstype.Annet -> f.beskrivelse
                        else -> null
                    },
                    "manedsbelop" to fradragsgrunnlag.fradrag.månedsbeløp,
                    "utenlandskInntekt" to serializeNullable(fradragsgrunnlag.fradrag.utenlandskInntekt),
                    "tilhorer" to fradragsgrunnlag.fradrag.tilhører,
                ),
                tx,
            )
    }
}
