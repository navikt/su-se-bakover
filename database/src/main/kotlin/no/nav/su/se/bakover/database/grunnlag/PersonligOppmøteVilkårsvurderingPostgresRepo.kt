package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodePersonligOppmøte
import java.util.UUID

internal class PersonligOppmøteVilkårsvurderingPostgresRepo(
    private val personligOppmøteGrunnlagPostgresRepo: PersonligOppmøteGrunnlagPostgresRepo,
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(
        behandlingId: UUID,
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
        behandlingId: UUID,
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
                delete from vilkårsvurdering_personlig_oppmøte where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: UUID, session: Session): PersonligOppmøteVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderingPersonligOppmøte") {
            """
                    select * from vilkårsvurdering_personlig_oppmøte where behandlingId = :behandlingId
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
