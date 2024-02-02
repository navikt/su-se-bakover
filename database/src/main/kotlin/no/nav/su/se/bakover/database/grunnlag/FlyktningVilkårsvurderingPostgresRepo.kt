package no.nav.su.se.bakover.database.grunnlag

import arrow.core.getOrElse
import kotliquery.Row
import no.nav.su.se.bakover.behandling.BehandlingsId
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.flyktning.domain.VurderingsperiodeFlyktning

internal class FlyktningVilkårsvurderingPostgresRepo(
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: BehandlingsId, vilkår: FlyktningVilkår, tx: TransactionalSession) {
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
        behandlingId: BehandlingsId,
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
                    "behandlingId" to behandlingId.value,
                    "resultat" to vurderingsperiode.vurdering.toDto(),
                    "fraOgMed" to vurderingsperiode.periode.fraOgMed,
                    "tilOgMed" to vurderingsperiode.periode.tilOgMed,
                ),
                tx,
            )
    }

    private fun slettForBehandlingId(behandlingId: BehandlingsId, tx: TransactionalSession) {
        """
                delete from vilkårsvurdering_flyktning where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId.value,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: BehandlingsId, session: Session): FlyktningVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderingFlyktning") {
            """
                    select * from vilkårsvurdering_flyktning where behandlingId = :behandlingId
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "behandlingId" to behandlingId.value,
                    ),
                    session,
                ) {
                    it.toVurderingsperiode()
                }.let {
                    when (it.isNotEmpty()) {
                        true -> FlyktningVilkår.Vurdert.tryCreate(
                            vurderingsperioder = it.toNonEmptyList(),

                        )
                            .getOrElse { feil ->
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
            vurdering = ResultatDto.valueOf(string("resultat")).toDomain(),
        )
    }
}
