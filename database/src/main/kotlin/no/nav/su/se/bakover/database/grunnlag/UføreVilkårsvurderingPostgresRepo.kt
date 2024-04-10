package no.nav.su.se.bakover.database.grunnlag

import arrow.core.getOrElse
import kotliquery.Row
import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.VurderingsperiodeUføre

internal class UføreVilkårsvurderingPostgresRepo(
    private val uføregrunnlagRepo: UføregrunnlagPostgresRepo,
    private val dbMetrics: DbMetrics,
) {
    internal fun lagre(behandlingId: BehandlingsId, vilkår: UføreVilkår, tx: TransactionalSession) {
        slettForBehandlingId(behandlingId, tx)
        when (vilkår) {
            UføreVilkår.IkkeVurdert -> {
                uføregrunnlagRepo.lagre(behandlingId, emptyList(), tx)
            }
            is UføreVilkår.Vurdert -> {
                uføregrunnlagRepo.lagre(behandlingId, vilkår.grunnlag, tx)
                vilkår.vurderingsperioder.forEach {
                    lagre(behandlingId, it, tx)
                }
            }
        }
    }

    private fun lagre(behandlingId: BehandlingsId, vurderingsperiode: VurderingsperiodeUføre, tx: TransactionalSession) {
        """
                insert into vilkårsvurdering_uføre
                (
                    id,
                    opprettet,
                    behandlingId,
                    uføre_grunnlag_id,
                    vurdering,
                    resultat,
                    fraOgMed,
                    tilOgMed
                ) values
                (
                    :id,
                    :opprettet,
                    :behandlingId,
                    :ufore_grunnlag_id,
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
                    "behandlingId" to behandlingId.value,
                    "ufore_grunnlag_id" to vurderingsperiode.grunnlag?.id,
                    "vurdering" to "MANUELL",
                    "resultat" to vurderingsperiode.vurdering.toDto(),
                    "fraOgMed" to vurderingsperiode.periode.fraOgMed,
                    "tilOgMed" to vurderingsperiode.periode.tilOgMed,
                ),
                tx,
            )
    }

    private fun slettForBehandlingId(behandlingId: BehandlingsId, tx: TransactionalSession) {
        """
                delete from vilkårsvurdering_uføre where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId.value,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: BehandlingsId, session: Session): UføreVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderingUføre") {
            """
                    select * from vilkårsvurdering_uføre where behandlingId = :behandlingId
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "behandlingId" to behandlingId.value,
                    ),
                    session,
                ) {
                    it.toVurderingsperioder(session)
                }.let {
                    when (it.isNotEmpty()) {
                        true -> UføreVilkår.Vurdert.tryCreate(
                            vurderingsperioder = it.toNonEmptyList(),

                        )
                            .getOrElse {
                                throw IllegalArgumentException("Kunne ikke instansiere ${UføreVilkår.Vurdert::class.simpleName}. Melding: $it")
                            }

                        false -> UføreVilkår.IkkeVurdert
                    }
                }
        }
    }

    private fun Row.toVurderingsperioder(session: Session): VurderingsperiodeUføre {
        return VurderingsperiodeUføre.create(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            vurdering = ResultatDto.valueOf(string("resultat")).toDomain(),
            grunnlag = uuidOrNull("uføre_grunnlag_id")?.let {
                uføregrunnlagRepo.hentForUføregrunnlagForId(it, session)
            },
            periode = Periode.create(
                fraOgMed = localDate("fraOgMed"),
                tilOgMed = localDate("tilOgMed"),
            ),
        )
    }
}
