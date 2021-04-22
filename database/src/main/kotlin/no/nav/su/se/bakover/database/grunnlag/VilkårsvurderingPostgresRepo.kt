package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurdering
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import java.util.UUID
import javax.sql.DataSource

internal class VilkårsvurderingPostgresRepo(
    private val dataSource: DataSource,
) : VilkårsvurderingRepo {
    override fun lagre(behandlingId: UUID, vilkårsvurdering: Vilkårsvurdering<Grunnlag.Uføregrunnlag>) {
        dataSource.withTransaction { tx ->
            slettForBehandlingId(behandlingId, tx)
            vilkårsvurdering.vurdering.forEach {
                lagre(behandlingId, it, tx)
            }
        }
    }

    private fun lagre(behandlingId: UUID, vurderingsperiode: Vurderingsperiode<Grunnlag.Uføregrunnlag>, session: Session) {
        """
                insert into vilkårsvurdering_uføre
                (
                    id,
                    opprettet,
                    behandlingId,
                    uføre_grunnlag_id,
                    vurdering,
                    resultat,
                    begrunnelse
                ) values 
                (
                    :id,
                    :opprettet,
                    :behandlingId,
                    :ufore_grunnlag_id,
                    :vurdering,
                    :resultat,
                    :begrunnelse
                )
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to vurderingsperiode.id,
                    "opprettet" to vurderingsperiode.opprettet,
                    "behandlingId" to behandlingId,
                    "ufore_grunnlag_id" to vurderingsperiode.grunnlag?.id,
                    "vurdering" to when (vurderingsperiode) {
                        is Vurderingsperiode.Manuell -> "MANUELL"
                    },
                    "resultat" to when (vurderingsperiode.resultat) {
                        Resultat.Avslag -> "AVSLAG"
                        Resultat.Innvilget -> "INNVILGET"
                    },
                    "begrunnelse" to vurderingsperiode.begrunnelse,
                ),
                session,
            )
    }

    private fun slettForBehandlingId(behandlingId: UUID, session: Session) {
        """
                delete from vilkårsvurdering_uføre vu 
                where vu.behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                session,
            )
    }
}
