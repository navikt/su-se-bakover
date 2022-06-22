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
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeLovligOpphold
import java.util.UUID

internal class LovligOppholdVilkårsvurderingPostgresRepo(
    private val dbMetrics: DbMetrics,
    private val lovligOppholdGrunnlagPostgresRepo: LovligOppholdgrunnlagPostgresRepo,
) {

    internal fun lagre(behandlingId: UUID, vilkår: LovligOppholdVilkår, tx: TransactionalSession) {
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
        behandlingId: UUID,
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
                    "behandlingId" to behandlingId,
                    "grunnlag_id" to vurderingsperiode.grunnlag?.id,
                    "resultat" to vurderingsperiode.resultat.toDto(),
                    "fraOgMed" to vurderingsperiode.periode.fraOgMed,
                    "tilOgMed" to vurderingsperiode.periode.tilOgMed,
                ),
                tx,
            )
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
                delete from vilkårsvurdering_lovligopphold where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: UUID, session: Session): LovligOppholdVilkår {
        return dbMetrics.timeQuery("hentVilkårsvurderinglovligopphold") {
            """
                    select * from vilkårsvurdering_lovligopphold where behandlingId = :behandlingId
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
                        true -> LovligOppholdVilkår.Vurdert.tryCreate(vurderingsperioder = Nel.fromListUnsafe(it))
                            .getOrHandle { feil ->
                                throw IllegalStateException("Kunne ikke instansiere ${LovligOppholdVilkår.Vurdert::class.simpleName}. Melding: $feil")
                            }
                        false -> LovligOppholdVilkår.IkkeVurdert
                    }
                }
        }
    }

    private fun Row.toVurderingsperiode(session: Session): VurderingsperiodeLovligOpphold {
        return VurderingsperiodeLovligOpphold.tryCreate(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            resultat = ResultatDto.valueOf(string("resultat")).toDomain(),
            grunnlag = uuidOrNull("grunnlag_id")?.let {
                lovligOppholdGrunnlagPostgresRepo.hent(it, session)
            },
            vurderingsperiode = Periode.create(
                fraOgMed = localDate("fraOgMed"),
                tilOgMed = localDate("tilOgMed"),
            ),
        ).getOrHandle {
            throw IllegalStateException(it.toString())
        }
    }
}
