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
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import java.util.UUID
import javax.sql.DataSource

internal class UføreVilkårsvurderingPostgresRepo(
    private val dataSource: DataSource,
    private val uføregrunnlagRepo: UføregrunnlagPostgresRepo,
    private val dbMetrics: DbMetrics,
) : UføreVilkårsvurderingRepo {

    override fun lagre(behandlingId: UUID, vilkår: Vilkår.Uførhet) {
        dataSource.withTransaction { tx ->
            lagre(
                behandlingId = behandlingId,
                vilkår = vilkår,
                tx = tx,
            )
        }
    }

    internal fun lagre(behandlingId: UUID, vilkår: Vilkår.Uførhet, tx: TransactionalSession) {
        slettForBehandlingId(behandlingId, tx)
        when (vilkår) {
            Vilkår.Uførhet.IkkeVurdert -> Unit
            is Vilkår.Uførhet.Vurdert -> {
                uføregrunnlagRepo.lagre(behandlingId, vilkår.grunnlag, tx)
                vilkår.vurderingsperioder.forEach {
                    lagre(behandlingId, it, tx)
                }
            }
        }
    }

    private fun lagre(behandlingId: UUID, vurderingsperiode: Vurderingsperiode.Uføre, tx: TransactionalSession) {
        """
                insert into vilkårsvurdering_uføre
                (
                    id,
                    opprettet,
                    behandlingId,
                    uføre_grunnlag_id,
                    vurdering,
                    resultat,
                    begrunnelse,
                    fraOgMed,
                    tilOgMed
                ) values 
                (
                    :id,
                    :opprettet,
                    :behandlingId,
                    :ufore_grunnlag_id,
                    :vurdering,
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
                    "ufore_grunnlag_id" to vurderingsperiode.grunnlag?.id,
                    "vurdering" to "MANUELL",
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
                delete from vilkårsvurdering_uføre where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    internal fun hent(behandlingId: UUID, session: Session): Vilkår.Uførhet {
        return dbMetrics.timeQuery("hentVilkårsvurderingUføre") {
            """
                    select * from vilkårsvurdering_uføre where behandlingId = :behandlingId
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "behandlingId" to behandlingId,
                    ),
                    session,
                ) {
                    it.toVurderingsperioder(session)
                }.let {
                    when (it.isNotEmpty()) {
                        true -> Vilkår.Uførhet.Vurdert.tryCreate(vurderingsperioder = Nel.fromListUnsafe(it))
                            .getOrHandle {
                                throw IllegalArgumentException("Kunne ikke instansiere ${Vilkår.Uførhet.Vurdert::class.simpleName}. Melding: $it")
                            }
                        false -> Vilkår.Uførhet.IkkeVurdert
                    }
                }
        }
    }

    private fun Row.toVurderingsperioder(session: Session): Vurderingsperiode.Uføre {
        return Vurderingsperiode.Uføre.create(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            resultat = ResultatDto.valueOf(string("resultat")).toDomain(),
            grunnlag = uuidOrNull("uføre_grunnlag_id")?.let {
                uføregrunnlagRepo.hentForUføregrunnlagId(it, session)
            },
            begrunnelse = stringOrNull("begrunnelse"),
            periode = Periode.create(
                fraOgMed = localDate("fraOgMed"),
                tilOgMed = localDate("tilOgMed"),
            ),
        )
    }
}
