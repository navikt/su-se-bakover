package no.nav.su.se.bakover.database.revurdering

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.grunnlag.GrunnlagsdataOgVilkårsvurderingerPostgresRepo
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering

internal class StansAvYtelsePostgresRepo(
    private val dbMetrics: DbMetrics,
    private val grunnlagsdataOgVilkårsvurderingerPostgresRepo: GrunnlagsdataOgVilkårsvurderingerPostgresRepo,
) {
    internal fun lagre(revurdering: StansAvYtelseRevurdering, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagreStansAvYtelsePostgresRepo") {
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
                        skalFøreTilBrevutsending,
                        sakid
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
                        :skalFoereTilBrevutsending,
                        :sakid
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
                                "periode" to serialize(revurdering.periode),
                                "simulering" to serialize(revurdering.simulering),
                                "saksbehandler" to revurdering.saksbehandler,
                                "revurderingsType" to RevurderingsType.SIMULERT_STANS,
                                "vedtakSomRevurderesId" to revurdering.tilRevurdering,
                                "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                                "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                                "attestering" to Attesteringshistorikk.empty().serialize(),
                                "skalFoereTilBrevutsending" to false,
                                "sakid" to revurdering.sakId
                            ),
                            tx,
                        )
                    grunnlagsdataOgVilkårsvurderingerPostgresRepo.lagre(
                        behandlingId = revurdering.id,
                        grunnlagsdataOgVilkårsvurderinger = revurdering.grunnlagsdataOgVilkårsvurderinger,
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
                                "attestering" to revurdering.attesteringer.serialize(),
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
                            "avsluttet" to serialize(
                                AvsluttetRevurderingInfo(
                                    begrunnelse = revurdering.begrunnelse,
                                    brevvalg = null,
                                    tidspunktAvsluttet = revurdering.tidspunktAvsluttet,
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
