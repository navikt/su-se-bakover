package no.nav.su.se.bakover.database.revurdering

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.database.grunnlag.BosituasjongrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering

internal class StansAvYtelsePostgresRepo(
    private val fradragsgrunnlagPostgresRepo: FradragsgrunnlagPostgresRepo,
    private val bosituasjonsgrunnlagPostgresRepo: BosituasjongrunnlagPostgresRepo,
    private val uføreVilkårsvurderingRepo: UføreVilkårsvurderingPostgresRepo,
    private val formueVilkårsvurderingRepo: FormueVilkårsvurderingPostgresRepo,
) {
    internal fun lagre(revurdering: StansAvYtelseRevurdering, sessionContext: TransactionContext) {
        sessionContext.withTransaction { tx ->
            when (revurdering) {
                is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
                    """
                    insert into revurdering (
                        id,
                        opprettet,
                        periode,
                        simulering,
                        saksbehandler,
                        revurderingsType,
                        vedtakSomRevurderesId,
                        årsak,
                        begrunnelse,
                        attestering,
                        skalFøreTilBrevutsending
                    ) values (
                        :id,
                        :opprettet,
                        to_json(:periode::json),
                        to_json(:simulering::json),
                        :saksbehandler,
                        :revurderingsType,
                        :vedtakSomRevurderesId,
                        :arsak,
                        :begrunnelse,
                        to_json(:attestering::json),
                        :skalFoereTilBrevutsending
                    ) on conflict(id) do update set
                        periode=to_json(:periode::json),
                        simulering=to_json(:simulering::json),
                        saksbehandler=:saksbehandler,
                        revurderingsType=:revurderingsType,
                        vedtakSomRevurderesId=:vedtakSomRevurderesId,
                        årsak=:arsak,
                        begrunnelse=:begrunnelse
                    """.trimIndent()
                        .insert(
                            mapOf(
                                "id" to revurdering.id,
                                "opprettet" to revurdering.opprettet,
                                "periode" to objectMapper.writeValueAsString(revurdering.periode),
                                "simulering" to objectMapper.writeValueAsString(revurdering.simulering),
                                "saksbehandler" to revurdering.saksbehandler,
                                "revurderingsType" to RevurderingsType.SIMULERT_STANS,
                                "vedtakSomRevurderesId" to revurdering.tilRevurdering.id,
                                "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                                "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                                "attestering" to Attesteringshistorikk.empty().hentAttesteringer().serialize(),
                                "skalFoereTilBrevutsending" to false,
                            ),
                            tx,
                        )
                    fradragsgrunnlagPostgresRepo.lagreFradragsgrunnlag(
                        behandlingId = revurdering.id,
                        fradragsgrunnlag = revurdering.grunnlagsdata.fradragsgrunnlag,
                        tx = tx,
                    )
                    bosituasjonsgrunnlagPostgresRepo.lagreBosituasjongrunnlag(
                        behandlingId = revurdering.id,
                        grunnlag = revurdering.grunnlagsdata.bosituasjon,
                        tx = tx,
                    )
                    uføreVilkårsvurderingRepo.lagre(
                        behandlingId = revurdering.id,
                        vilkår = revurdering.vilkårsvurderinger.uføre,
                        tx = tx,
                    )
                    formueVilkårsvurderingRepo.lagre(
                        behandlingId = revurdering.id,
                        vilkår = revurdering.vilkårsvurderinger.formue,
                        tx = tx,
                    )
                }
                is StansAvYtelseRevurdering.IverksattStansAvYtelse -> {
                    """
                    update revurdering set 
                        attestering = to_json(:attestering::json),
                        revurderingsType = :revurderingsType 
                    where id = :id
                    """.trimIndent()
                        .oppdatering(
                            mapOf(
                                "attestering" to revurdering.attesteringer.hentAttesteringer().serialize(),
                                "revurderingsType" to RevurderingsType.IVERKSATT_STANS,
                                "id" to revurdering.id,
                            ),
                            tx,
                        )
                }
                is StansAvYtelseRevurdering.AvsluttetStansAvYtelse -> {
                    """
                        update 
                            revurdering
                        set 
                            avsluttet = to_jsonb(:avsluttet::jsonb)
                        where
                            id = :id
                    """.trimIndent().oppdatering(
                        params = mapOf(
                            "id" to revurdering.id,
                            "avsluttet" to objectMapper.writeValueAsString(
                                RevurderingPostgresRepo.AvsluttetRevurderingInfo(
                                    begrunnelse = revurdering.begrunnelse,
                                    fritekst = null,
                                    datoAvsluttet = revurdering.datoAvsluttet,
                                ),
                            ),
                        ),
                        session = tx,
                    )
                }
            }
        }
    }
}
