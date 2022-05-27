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
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold
import java.util.UUID

internal class UtenlandsoppholdVilkårsvurderingPostgresRepo(
    private val utenlandsoppholdgrunnlagRepo: UtenlandsoppholdgrunnlagPostgresRepo,
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: UUID, vilkår: UtenlandsoppholdVilkår, tx: TransactionalSession) {
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
        behandlingId: UUID,
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
                    "behandlingId" to behandlingId,
                    "grunnlag_utland_id" to vurderingsperiode.grunnlag?.id,
                    "resultat" to vurderingsperiode.resultat.toDto().toString(),
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

    internal fun hent(behandlingId: UUID, session: Session): UtenlandsoppholdVilkår {
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
                        true -> UtenlandsoppholdVilkår.Vurdert.tryCreate(vurderingsperioder = Nel.fromListUnsafe(it))
                            .getOrHandle { feil ->
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
            resultat = ResultatDto.valueOf(string("resultat")).toDomain(),
            grunnlag = uuidOrNull("grunnlag_utland_id")?.let {
                utenlandsoppholdgrunnlagRepo.hent(it, session)
            },
            periode = Periode.create(
                fraOgMed = localDate("fraOgMed"),
                tilOgMed = localDate("tilOgMed"),
            ),
        )
    }
}
