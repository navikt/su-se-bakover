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
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.lovligopphold.domain.VurderingsperiodeLovligOpphold

internal class LovligOppholdVilkårsvurderingPostgresRepo(
    private val dbMetrics: DbMetrics,
    private val lovligOppholdGrunnlagPostgresRepo: LovligOppholdgrunnlagPostgresRepo,
) {

    internal fun lagre(behandlingId: BehandlingsId, vilkår: LovligOppholdVilkår, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagreVilkårsvurderingLovligOpphold") {
            slettForBehandlingId(behandlingId, tx)
            when (vilkår) {
                LovligOppholdVilkår.IkkeVurdert -> {
                    lovligOppholdGrunnlagPostgresRepo.lagre(behandlingId, emptyList(), tx)
                }
                is LovligOppholdVilkår.Vurdert -> {
                    lovligOppholdGrunnlagPostgresRepo.lagre(behandlingId, vilkår.grunnlag, tx)
                    vilkår.vurderingsperioder.forEach {
                        lagre(behandlingId, it, tx)
                    }
                }
            }
        }
    }

    private fun lagre(
        behandlingId: BehandlingsId,
        vurderingsperiode: VurderingsperiodeLovligOpphold,
        tx: TransactionalSession,
    ) {
        """
                insert into vilkårsvurdering_lovligopphold
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
                    "grunnlag_id" to vurderingsperiode.grunnlag?.id,
                    "resultat" to vurderingsperiode.vurdering.toDto(),
                    "fraOgMed" to vurderingsperiode.periode.fraOgMed,
                    "tilOgMed" to vurderingsperiode.periode.tilOgMed,
                ),
                tx,
            )
    }

    private fun slettForBehandlingId(behandlingId: BehandlingsId, tx: TransactionalSession) {
        """
                delete from vilkårsvurdering_lovligopphold where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId.value,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: BehandlingsId, session: Session): LovligOppholdVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderinglovligopphold") {
            """
                    select * from vilkårsvurdering_lovligopphold where behandlingId = :behandlingId
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
                        true -> LovligOppholdVilkår.Vurdert.tryCreate(
                            vurderingsperioder = it.toNonEmptyList(),

                        )
                            .getOrElse { feil ->
                                throw IllegalStateException("Kunne ikke instansiere ${LovligOppholdVilkår.Vurdert::class.simpleName}. Melding: $feil")
                            }

                        false -> LovligOppholdVilkår.IkkeVurdert
                    }
                }
        }
    }

    private fun Row.toVurderingsperiode(): VurderingsperiodeLovligOpphold {
        return VurderingsperiodeLovligOpphold.tryCreate(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            vurdering = ResultatDto.valueOf(string("resultat")).toDomain(),
            vurderingsperiode = Periode.create(
                fraOgMed = localDate("fraOgMed"),
                tilOgMed = localDate("tilOgMed"),
            ),
        )
    }
}
