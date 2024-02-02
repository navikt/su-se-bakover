package no.nav.su.se.bakover.database.grunnlag

import arrow.core.getOrElse
import kotliquery.Row
import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.opplysningsplikt.domain.VurderingsperiodeOpplysningsplikt

internal class OpplysningspliktVilkårsvurderingPostgresRepo(
    private val opplysningspliktGrunnlagRepo: OpplysningspliktGrunnlagPostgresRepo,
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(
        behandlingId: BehandlingsId,
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
        behandlingId: BehandlingsId,
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
                    "behandlingId" to behandlingId.value,
                    "grunnlag_id" to vurderingsperiode.grunnlag.id,
                    "resultat" to vurderingsperiode.vurdering.toDto(),
                    "fraOgMed" to vurderingsperiode.periode.fraOgMed,
                    "tilOgMed" to vurderingsperiode.periode.tilOgMed,
                ),
                tx,
            )
    }

    private fun slettForBehandlingId(behandlingId: BehandlingsId, tx: TransactionalSession) {
        """
                delete from vilkårsvurdering_opplysningsplikt where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId.value,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: BehandlingsId, session: Session): OpplysningspliktVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderingOpplysningsplikt") {
            """
                    select * from vilkårsvurdering_opplysningsplikt where behandlingId = :behandlingId
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "behandlingId" to behandlingId.value,
                    ),
                    session,
                ) {
                    it.toVurderingsperiode(session)
                }.let {
                    when (it.isNotEmpty()) {
                        true -> OpplysningspliktVilkår.Vurdert.tryCreate(
                            vurderingsperioder = it.toNonEmptyList(),

                        )
                            .getOrElse { feil ->
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
