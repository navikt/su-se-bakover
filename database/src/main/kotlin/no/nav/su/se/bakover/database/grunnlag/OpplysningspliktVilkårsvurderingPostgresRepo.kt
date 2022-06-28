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
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import java.util.UUID

internal class OpplysningspliktVilkårsvurderingPostgresRepo(
    private val opplysningspliktGrunnlagRepo: OpplysningspliktGrunnlagPostgresRepo,
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(
        behandlingId: UUID,
        vilkår: OpplysningspliktVilkår,
        tx: TransactionalSession,
    ) {
        dbMetrics.timeQuery("lagreVilkårsvurderingOpplysningsplikt") {
            slettForBehandlingId(behandlingId, tx)
            when (vilkår) {
                OpplysningspliktVilkår.IkkeVurdert -> {
                    opplysningspliktGrunnlagRepo.lagre(behandlingId, emptyList(), tx)
                }
                is OpplysningspliktVilkår.Vurdert -> {
                    opplysningspliktGrunnlagRepo.lagre(behandlingId, vilkår.grunnlag, tx)
                    vilkår.vurderingsperioder.forEach {
                        lagre(behandlingId, it, tx)
                    }
                }
            }
        }
    }

    private fun lagre(
        behandlingId: UUID,
        vurderingsperiode: VurderingsperiodeOpplysningsplikt,
        tx: TransactionalSession,
    ) {
        """
                insert into vilkårsvurdering_opplysningsplikt
                (
                    id,
                    opprettet,
                    behandlingId,
                    grunnlag_id,
                    resultat,
                    fraOgMed,
                    tilOgMed
                ) values 
                (
                    :id,
                    :opprettet,
                    :behandlingId,
                    :grunnlag_id,
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
                    "grunnlag_id" to vurderingsperiode.grunnlag.id,
                    "resultat" to vurderingsperiode.vurdering.toDto(),
                    "fraOgMed" to vurderingsperiode.periode.fraOgMed,
                    "tilOgMed" to vurderingsperiode.periode.tilOgMed,
                ),
                tx,
            )
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
                delete from vilkårsvurdering_opplysningsplikt where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: UUID, session: Session): OpplysningspliktVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderingOpplysningsplikt") {
            """
                    select * from vilkårsvurdering_opplysningsplikt where behandlingId = :behandlingId
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
                        true -> OpplysningspliktVilkår.Vurdert.tryCreate(vurderingsperioder = Nel.fromListUnsafe(it))
                            .getOrHandle { feil ->
                                throw IllegalStateException("Kunne ikke instansiere ${OpplysningspliktVilkår.Vurdert::class.simpleName}. Melding: $feil")
                            }
                        false -> OpplysningspliktVilkår.IkkeVurdert
                    }
                }
        }
    }

    private fun Row.toVurderingsperiode(session: Session): VurderingsperiodeOpplysningsplikt {
        return VurderingsperiodeOpplysningsplikt.create(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            grunnlag = opplysningspliktGrunnlagRepo.hent(uuid("grunnlag_id"), session)!!,
            periode = Periode.create(
                fraOgMed = localDate("fraOgMed"),
                tilOgMed = localDate("tilOgMed"),
            ),
        )
    }
}
