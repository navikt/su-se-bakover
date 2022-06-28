package no.nav.su.se.bakover.database.grunnlag

import arrow.core.Nel
import arrow.core.getOrHandle
import kotliquery.Row
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUføre
import java.util.UUID

internal class UføreVilkårsvurderingPostgresRepo(
    private val uføregrunnlagRepo: UføregrunnlagPostgresRepo,
    private val dbMetrics: DbMetrics,
) {
    internal fun lagre(behandlingId: UUID, vilkår: UføreVilkår, tx: TransactionalSession) {
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

    private fun lagre(behandlingId: UUID, vurderingsperiode: VurderingsperiodeUføre, tx: TransactionalSession) {
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
                    "behandlingId" to behandlingId,
                    "ufore_grunnlag_id" to vurderingsperiode.grunnlag?.id,
                    "vurdering" to "MANUELL",
                    "resultat" to vurderingsperiode.vurdering.toDto(),
                    "fraOgMed" to vurderingsperiode.periode.fraOgMed,
                    "tilOgMed" to vurderingsperiode.periode.tilOgMed,
                ),
                tx,
            )
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
                delete from vilkårsvurdering_uføre where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: UUID, session: Session): UføreVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderingUføre") {
            """
                    select * from vilkårsvurdering_uføre where behandlingId = :behandlingId
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
                        true -> UføreVilkår.Vurdert.tryCreate(vurderingsperioder = Nel.fromListUnsafe(it))
                            .getOrHandle {
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
