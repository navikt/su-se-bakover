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
import no.nav.su.se.bakover.common.infrastructure.persistence.periode
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import vilkår.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import vilkår.utenlandsopphold.domain.vilkår.VurderingsperiodeUtenlandsopphold

internal class UtenlandsoppholdVilkårsvurderingPostgresRepo(
    private val utenlandsoppholdgrunnlagRepo: UtenlandsoppholdgrunnlagPostgresRepo,
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: BehandlingsId, vilkår: UtenlandsoppholdVilkår, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagreVilkårsvurderingUtlandsopphold") {
            slettForBehandlingId(behandlingId, tx)
            when (vilkår) {
                UtenlandsoppholdVilkår.IkkeVurdert -> {
                    utenlandsoppholdgrunnlagRepo.lagre(behandlingId, emptyList(), tx)
                }
                is UtenlandsoppholdVilkår.Vurdert -> {
                    utenlandsoppholdgrunnlagRepo.lagre(behandlingId, vilkår.grunnlag, tx)
                    vilkår.vurderingsperioder.forEach {
                        lagre(behandlingId, it, tx)
                    }
                }
            }
        }
    }

    private fun lagre(
        behandlingId: BehandlingsId,
        vurderingsperiode: VurderingsperiodeUtenlandsopphold,
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
                    fraOgMed,
                    tilOgMed
                ) values
                (
                    :id,
                    :opprettet,
                    :behandlingId,
                    :grunnlag_utland_id,
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
                    "grunnlag_utland_id" to vurderingsperiode.grunnlag?.id,
                    "resultat" to vurderingsperiode.vurdering.toDto(),
                    "fraOgMed" to vurderingsperiode.periode.fraOgMed,
                    "tilOgMed" to vurderingsperiode.periode.tilOgMed,
                ),
                tx,
            )
    }

    private fun slettForBehandlingId(behandlingId: BehandlingsId, tx: TransactionalSession) {
        """
                delete from vilkårsvurdering_utland where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId.value,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: BehandlingsId, session: Session): UtenlandsoppholdVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderingUtlandsopphold") {
            """
                    select * from vilkårsvurdering_utland where behandlingId = :behandlingId
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
                        true -> UtenlandsoppholdVilkår.Vurdert.tryCreate(
                            vurderingsperioder = it.toNonEmptyList(),

                        )
                            .getOrElse { feil ->
                                throw IllegalStateException("Kunne ikke instansiere ${UtenlandsoppholdVilkår.Vurdert::class.simpleName}. Melding: $feil")
                            }

                        false -> UtenlandsoppholdVilkår.IkkeVurdert
                    }
                }
        }
    }

    private fun Row.toVurderingsperiode(session: Session): VurderingsperiodeUtenlandsopphold {
        return VurderingsperiodeUtenlandsopphold.create(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            vurdering = ResultatDto.valueOf(string("resultat")).toDomain(),
            grunnlag = uuidOrNull("grunnlag_utland_id")?.let {
                utenlandsoppholdgrunnlagRepo.hent(it, session)
            },
            periode = periode("fraOgMed", "tilOgMed"),
        )
    }
}
