package no.nav.su.se.bakover.database.grunnlag

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
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import vilkår.personligoppmøte.domain.VurderingsperiodePersonligOppmøte

internal class PersonligOppmøteVilkårsvurderingPostgresRepo(
    private val personligOppmøteGrunnlagPostgresRepo: PersonligOppmøteGrunnlagPostgresRepo,
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(
        behandlingId: BehandlingsId,
        vilkår: PersonligOppmøteVilkår,
        tx: TransactionalSession,
    ) {
        dbMetrics.timeQuery("lagreVilkårsvurderingPersonligOppmøte") {
            slettForBehandlingId(behandlingId, tx)
            when (vilkår) {
                PersonligOppmøteVilkår.IkkeVurdert -> {
                    personligOppmøteGrunnlagPostgresRepo.lagre(behandlingId, emptyList(), tx)
                }
                is PersonligOppmøteVilkår.Vurdert -> {
                    personligOppmøteGrunnlagPostgresRepo.lagre(behandlingId, vilkår.grunnlag, tx)
                    vilkår.vurderingsperioder.forEach {
                        lagre(behandlingId, it, tx)
                    }
                }
            }
        }
    }

    private fun lagre(
        behandlingId: BehandlingsId,
        vurderingsperiode: VurderingsperiodePersonligOppmøte,
        tx: TransactionalSession,
    ) {
        """
                insert into vilkårsvurdering_personlig_oppmøte
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
                delete from vilkårsvurdering_personlig_oppmøte where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId.value,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: BehandlingsId, session: Session): PersonligOppmøteVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderingPersonligOppmøte") {
            """
                    select * from vilkårsvurdering_personlig_oppmøte where behandlingId = :behandlingId
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
                        true -> PersonligOppmøteVilkår.Vurdert(
                            vurderingsperioder = it.toNonEmptyList(),

                        )
                        false -> PersonligOppmøteVilkår.IkkeVurdert
                    }
                }
        }
    }

    private fun Row.toVurderingsperiode(session: Session): VurderingsperiodePersonligOppmøte {
        return VurderingsperiodePersonligOppmøte(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            grunnlag = personligOppmøteGrunnlagPostgresRepo.hent(uuid("grunnlag_id"), session)!!,
            periode = Periode.create(
                fraOgMed = localDate("fraOgMed"),
                tilOgMed = localDate("tilOgMed"),
            ),
        )
    }
}
