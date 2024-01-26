package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.formue.domain.FormueVilkår
import vilkår.formue.domain.VurderingsperiodeFormue
import java.util.UUID

internal class FormueVilkårsvurderingPostgresRepo(
    private val formuegrunnlagPostgresRepo: FormuegrunnlagPostgresRepo,
    private val dbMetrics: DbMetrics,
) {
    internal fun lagre(behandlingId: UUID, vilkår: FormueVilkår, tx: TransactionalSession) {
        slettForBehandlingId(behandlingId, tx)
        when (vilkår) {
            is FormueVilkår.IkkeVurdert -> {
                formuegrunnlagPostgresRepo.lagreFormuegrunnlag(behandlingId, emptyList(), tx)
            }
            is FormueVilkår.Vurdert -> {
                formuegrunnlagPostgresRepo.lagreFormuegrunnlag(
                    behandlingId = behandlingId,
                    formuegrunnlag = vilkår.grunnlag,
                    tx = tx,
                )
                vilkår.vurderingsperioder.forEach {
                    lagre(behandlingId, it, tx)
                }
            }
        }
    }

    private fun lagre(behandlingId: UUID, vurderingsperiode: VurderingsperiodeFormue, tx: TransactionalSession) {
        """
                insert into vilkårsvurdering_formue
                (
                    id,
                    opprettet,
                    behandlingId,
                    formue_grunnlag_id,
                    vurdering,
                    resultat,
                    fraOgMed,
                    tilOgMed
                ) values
                (
                    :id,
                    :opprettet,
                    :behandlingId,
                    :formue_grunnlag_id,
                    :vurdering,
                    :resultat,
                    :fraOgMed,
                    :tilOgMed
                )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to vurderingsperiode.id,
                    "opprettet" to vurderingsperiode.opprettet,
                    "behandlingId" to behandlingId,
                    "formue_grunnlag_id" to vurderingsperiode.grunnlag.id,
                    "vurdering" to "AUTOMATISK",
                    "resultat" to vurderingsperiode.vurdering.toDto(),
                    "fraOgMed" to vurderingsperiode.periode.fraOgMed,
                    "tilOgMed" to vurderingsperiode.periode.tilOgMed,
                ),
                tx,
            )
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
                delete from vilkårsvurdering_formue where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: UUID, session: Session): FormueVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderingFormue") {
            """
                select * from vilkårsvurdering_formue where behandlingId = :behandlingId
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "behandlingId" to behandlingId,
                    ),
                    session,
                ) {
                    it.toVurderingsperioder(session)
                }.let {
                    when (it.isNotEmpty()) {
                        true -> FormueVilkår.Vurdert.createFromVilkårsvurderinger(
                            vurderingsperioder = it.toNonEmptyList(),
                        )
                        false -> FormueVilkår.IkkeVurdert
                    }
                }
        }
    }

    private fun Row.toVurderingsperioder(session: Session): VurderingsperiodeFormue {
        val periode = Periode.create(
            fraOgMed = localDate("fraOgMed"),
            tilOgMed = localDate("tilOgMed"),
        )
        return VurderingsperiodeFormue.create(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            vurdering = ResultatDto.valueOf(string("resultat")).toDomain(),
            grunnlag = uuid("formue_grunnlag_id").let {
                formuegrunnlagPostgresRepo.hentFormuegrunnlag(it, session)!!
            },
            periode = periode,
        )
    }
}
