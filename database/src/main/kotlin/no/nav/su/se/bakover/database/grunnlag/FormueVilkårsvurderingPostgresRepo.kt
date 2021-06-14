package no.nav.su.se.bakover.database.grunnlag

import arrow.core.Nel
import kotliquery.Row
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuidOrNull
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import java.util.UUID
import javax.sql.DataSource

internal class FormueVilkårsvurderingPostgresRepo(
    private val dataSource: DataSource,
    private val formuegrunnlagPostgresRepo: FormuegrunnlagPostgresRepo,
) : FormueVilkårsvurderingRepo {

    override fun lagre(behandlingId: UUID, vilkår: Vilkår.Formue) {
        dataSource.withTransaction { tx ->
            slettForBehandlingId(behandlingId, tx)
            when (vilkår) {
                Vilkår.Formue.IkkeVurdert -> Unit
                is Vilkår.Formue.Vurdert -> {
                    formuegrunnlagPostgresRepo.lagreFormuegrunnlag(
                        behandlingId = behandlingId,
                        formuegrunnlag = vilkår.grunnlag,
                        tx,
                    )
                    vilkår.vurderingsperioder.forEach {
                        lagre(behandlingId, it, tx)
                    }
                }
            }
        }
    }

    private fun lagre(behandlingId: UUID, vurderingsperiode: Vurderingsperiode.Formue, session: Session) {
        """
                insert into vilkårsvurdering_formue
                (
                    id,
                    opprettet,
                    behandlingId,
                    formue_grunnlag_id,
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
                    :formue_grunnlag_id,
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
                    "formue_grunnlag_id" to vurderingsperiode.grunnlag?.id,
                    "vurdering" to "AUTOMATISK",
                    "resultat" to vurderingsperiode.resultat.toDto().toString(),
                    "begrunnelse" to vurderingsperiode.begrunnelse,
                    "fraOgMed" to vurderingsperiode.periode.fraOgMed,
                    "tilOgMed" to vurderingsperiode.periode.tilOgMed,
                ),
                session,
            )
    }

    private fun slettForBehandlingId(behandlingId: UUID, session: Session) {
        """
                delete from vilkårsvurdering_formue where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                session,
            )
    }

    internal fun hent(behandlingId: UUID, session: Session): Vilkår.Formue {
        return """
                select * from vilkårsvurdering_formue where behandlingId = :behandlingId
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
                    true -> Vilkår.Formue.Vurdert.createFromVilkårsvurderinger(
                        vurderingsperioder = Nel.fromListUnsafe(
                            it,
                        ),
                    )
                    false -> Vilkår.Formue.IkkeVurdert
                }
            }
    }

    private fun Row.toVurderingsperioder(session: Session): Vurderingsperiode.Formue {
        return Vurderingsperiode.Formue.create(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            resultat = ResultatDto.valueOf(string("resultat")).toDomain(),
            grunnlag = uuidOrNull("formue_grunnlag_id")?.let {
                formuegrunnlagPostgresRepo.hentForFormuegrunnlagId(it, session)
            },
            begrunnelse = stringOrNull("begrunnelse"),
            periode = Periode.create(
                fraOgMed = localDate("fraOgMed"),
                tilOgMed = localDate("tilOgMed"),
            ),
        )
    }

    private enum class ResultatDto {
        AVSLAG,
        INNVILGET,
        UAVKLART;

        fun toDomain() = when (this) {
            AVSLAG -> Resultat.Avslag
            INNVILGET -> Resultat.Innvilget
            UAVKLART -> Resultat.Uavklart
        }
    }

    private fun Resultat.toDto() = when (this) {
        Resultat.Avslag -> ResultatDto.AVSLAG
        Resultat.Innvilget -> ResultatDto.INNVILGET
        Resultat.Uavklart -> ResultatDto.UAVKLART
    }
}
