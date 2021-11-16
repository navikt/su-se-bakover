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
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuidOrNull
import no.nav.su.se.bakover.domain.vilkår.OppholdIUtlandetVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOppholdIUtlandet
import java.util.UUID

internal class UtlandsoppholdVilkårsvurderingPostgresRepo(
    private val utlandsoppholdgrunnlagRepo: UtlandsoppholdgrunnlagPostgresRepo,
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: UUID, vilkår: OppholdIUtlandetVilkår, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagreVilkårsvurderingUtlandsopphold") {
            slettForBehandlingId(behandlingId, tx)
            when (vilkår) {
                OppholdIUtlandetVilkår.IkkeVurdert -> Unit
                is OppholdIUtlandetVilkår.Vurdert -> {
                    utlandsoppholdgrunnlagRepo.lagre(behandlingId, vilkår.grunnlag, tx)
                    vilkår.vurderingsperioder.forEach {
                        lagre(behandlingId, it, tx)
                    }
                }
            }
        }
    }

    private fun lagre(
        behandlingId: UUID,
        vurderingsperiode: VurderingsperiodeOppholdIUtlandet,
        tx: TransactionalSession,
    ) {
        """
                insert into vilkårsvurdering_utland
                (
                    id,
                    opprettet,
                    behandlingId,
                    grunnlag_utland_id,
                    resultat,
                    begrunnelse,
                    fraOgMed,
                    tilOgMed
                ) values 
                (
                    :id,
                    :opprettet,
                    :behandlingId,
                    :grunnlag_utland_id,
                    :resultat,
                    :begrunnelse,
                    :fraOgMed,
                    :tilOgMed
                )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to vurderingsperiode.id,
                    "opprettet" to vurderingsperiode.opprettet,
                    "behandlingId" to behandlingId,
                    "grunnlag_utland_id" to vurderingsperiode.grunnlag?.id,
                    "resultat" to vurderingsperiode.resultat.toDto().toString(),
                    "begrunnelse" to vurderingsperiode.begrunnelse,
                    "fraOgMed" to vurderingsperiode.periode.fraOgMed,
                    "tilOgMed" to vurderingsperiode.periode.tilOgMed,
                ),
                tx,
            )
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
                delete from vilkårsvurdering_utland where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: UUID, session: Session): OppholdIUtlandetVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderingUtlandsopphold") {
            """
                    select * from vilkårsvurdering_utland where behandlingId = :behandlingId
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "behandlingId" to behandlingId,
                    ),
                    session,
                ) {
                    it.toVurderingsperiode(session)
                }.let {
                    when (it.isNotEmpty()) {
                        true -> OppholdIUtlandetVilkår.Vurdert.tryCreate(vurderingsperioder = Nel.fromListUnsafe(it))
                            .getOrHandle { feil ->
                                throw IllegalStateException("Kunne ikke instansiere ${OppholdIUtlandetVilkår.Vurdert::class.simpleName}. Melding: $feil")
                            }
                        false -> OppholdIUtlandetVilkår.IkkeVurdert
                    }
                }
        }
    }

    private fun Row.toVurderingsperiode(session: Session): VurderingsperiodeOppholdIUtlandet {
        return VurderingsperiodeOppholdIUtlandet.create(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            resultat = ResultatDto.valueOf(string("resultat")).toDomain(),
            grunnlag = uuidOrNull("grunnlag_utland_id")?.let {
                utlandsoppholdgrunnlagRepo.hentForUtlandsoppholdgrunnlagId(it, session)
            },
            begrunnelse = stringOrNull("begrunnelse"),
            periode = Periode.create(
                fraOgMed = localDate("fraOgMed"),
                tilOgMed = localDate("tilOgMed"),
            ),
        )
    }
}
