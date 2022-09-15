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
import no.nav.su.se.bakover.domain.vilkår.FamiliegjenforeningVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFamiliegjenforening
import java.util.UUID

internal class FamiliegjenforeningVilkårsvurderingPostgresRepo(
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: UUID, vilkår: FamiliegjenforeningVilkår, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagreVilkårsvurderingFamiliegjenforening") {
            slettForBehandlingId(behandlingId, tx)
            when (vilkår) {
                FamiliegjenforeningVilkår.IkkeVurdert -> {}
                is FamiliegjenforeningVilkår.Vurdert -> {
                    vilkår.vurderingsperioder.forEach {
                        lagre(behandlingId, it, tx)
                    }
                }
            }
        }
    }

    private fun lagre(
        behandlingId: UUID,
        vurderingsperiode: VurderingsperiodeFamiliegjenforening,
        tx: TransactionalSession,
    ) {
        """
                insert into vilkårsvurdering_familiegjenforening
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
                delete from vilkårsvurdering_familiegjenforening where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: UUID, session: Session): FamiliegjenforeningVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderingFamiliegjenforening") {
            """
                    select * from vilkårsvurdering_familiegjenforening where behandlingId = :behandlingId
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
                        true -> FamiliegjenforeningVilkår.Vurdert.create(vurderingsperioder = Nel.fromListUnsafe(it))
                            .getOrHandle { feil ->
                                throw IllegalStateException("Kunne ikke instansiere ${FamiliegjenforeningVilkår.Vurdert::class.simpleName}. Melding: $feil")
                            }
                        false -> FamiliegjenforeningVilkår.IkkeVurdert
                    }
                }
        }
    }

    private fun Row.toVurderingsperiode(): VurderingsperiodeFamiliegjenforening {
        return VurderingsperiodeFamiliegjenforening.create(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            vurdering = ResultatDto.valueOf(string("resultat")).toDomain(),
            periode = Periode.create(
                fraOgMed = localDate("fraOgMed"),
                tilOgMed = localDate("tilOgMed"),
            ),
        )
    }
}
