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
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFlyktning
import java.util.UUID

internal class FlyktningVilkårsvurderingPostgresRepo(
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: UUID, vilkår: FlyktningVilkår, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagreVilkårsvurderingFlyktning") {
            slettForBehandlingId(behandlingId, tx)
            when (vilkår) {
                FlyktningVilkår.IkkeVurdert -> {}
                is FlyktningVilkår.Vurdert -> {
                    vilkår.vurderingsperioder.forEach {
                        lagre(behandlingId, it, tx)
                    }
                }
            }
        }
    }

    private fun lagre(
        behandlingId: UUID,
        vurderingsperiode: VurderingsperiodeFlyktning,
        tx: TransactionalSession,
    ) {
        """
                insert into vilkårsvurdering_flyktning
                (
                    id,
                    opprettet,
                    behandlingId,
                    resultat,
                    fraOgMed,
                    tilOgMed
                ) values 
                (
                    :id,
                    :opprettet,
                    :behandlingId,               
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
                    "resultat" to vurderingsperiode.vurdering.toDto(),
                    "fraOgMed" to vurderingsperiode.periode.fraOgMed,
                    "tilOgMed" to vurderingsperiode.periode.tilOgMed,
                ),
                tx,
            )
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
                delete from vilkårsvurdering_flyktning where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: UUID, session: Session): FlyktningVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderingFlyktning") {
            """
                    select * from vilkårsvurdering_flyktning where behandlingId = :behandlingId
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "behandlingId" to behandlingId,
                    ),
                    session,
                ) {
                    it.toVurderingsperiode()
                }.let {
                    when (it.isNotEmpty()) {
                        true -> FlyktningVilkår.Vurdert.tryCreate(vurderingsperioder = Nel.fromListUnsafe(it))
                            .getOrHandle { feil ->
                                throw IllegalStateException("Kunne ikke instansiere ${FlyktningVilkår.Vurdert::class.simpleName}. Melding: $feil")
                            }
                        false -> FlyktningVilkår.IkkeVurdert
                    }
                }
        }
    }

    private fun Row.toVurderingsperiode(): VurderingsperiodeFlyktning {
        return VurderingsperiodeFlyktning.create(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            periode = Periode.create(
                fraOgMed = localDate("fraOgMed"),
                tilOgMed = localDate("tilOgMed"),
            ),
            vurdering = ResultatDto.valueOf(string("resultat")).toDomain()
        )
    }
}
