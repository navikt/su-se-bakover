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
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.pensjon.domain.VurderingsperiodePensjon

internal class PensjonVilkårsvurderingPostgresRepo(
    private val pensjonsgrunnlagPostgresRepo: PensjonsgrunnlagPostgresRepo,
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: BehandlingsId, vilkår: PensjonsVilkår, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagreVilkårsvurderingPensjon") {
            slettForBehandlingId(behandlingId, tx)
            when (vilkår) {
                PensjonsVilkår.IkkeVurdert -> {
                    pensjonsgrunnlagPostgresRepo.lagre(behandlingId, emptyList(), tx)
                }
                is PensjonsVilkår.Vurdert -> {
                    pensjonsgrunnlagPostgresRepo.lagre(behandlingId, vilkår.grunnlag, tx)
                    vilkår.vurderingsperioder.forEach {
                        lagre(behandlingId, it, tx)
                    }
                }
            }
        }
    }

    private fun lagre(
        behandlingId: BehandlingsId,
        vurderingsperiode: VurderingsperiodePensjon,
        tx: TransactionalSession,
    ) {
        """
                insert into vilkårsvurdering_pensjon
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
                delete from vilkårsvurdering_pensjon where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId.value,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: BehandlingsId, session: Session): PensjonsVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderingPensjon") {
            """
                    select * from vilkårsvurdering_pensjon where behandlingId = :behandlingId
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
                        true -> PensjonsVilkår.Vurdert.tryCreate(
                            vurderingsperioder = it.toNonEmptyList(),

                        )
                            .getOrElse { feil ->
                                throw IllegalStateException("Kunne ikke instansiere ${PensjonsVilkår.Vurdert::class.simpleName}. Melding: $feil")
                            }

                        false -> PensjonsVilkår.IkkeVurdert
                    }
                }
        }
    }

    private fun Row.toVurderingsperiode(session: Session): VurderingsperiodePensjon {
        return VurderingsperiodePensjon.create(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            periode = Periode.create(
                fraOgMed = localDate("fraOgMed"),
                tilOgMed = localDate("tilOgMed"),
            ),
            vurdering = ResultatDto.valueOf(string("resultat")).toDomain(),
            grunnlag = pensjonsgrunnlagPostgresRepo.hent((uuid("grunnlag_id")), session)!!,
        )
    }
}
