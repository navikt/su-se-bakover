package db.migration

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.database.grunnlag.PersonligOppmøteGrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.PersonligOppmøteVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.PersonligOppmøteÅrsakDb
import no.nav.su.se.bakover.database.grunnlag.toDomain
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodePersonligOppmøte
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class V154__migrer_personlig_oppmøte : BaseJavaMigration() {
    override fun migrate(context: Context?) {
        val statement = context!!.connection.createStatement()

        val migrerteSøknadsbehandlinger = statement.executeQuery(
            """
                with søknadsbehandling_personligOppmøte as (
                select 
                b.id as behandling_id,
                (b.stønadsperiode->'periode'->>'fraOgMed')::date as behandling_fom,
                (b.stønadsperiode->'periode'->>'tilOgMed')::date as behandling_tom,
                b.opprettet as behandling_opprettet,
                b.behandlingsinformasjon->'personligOppmøte'->>'status' as personligOppmøte,
                s.saksnummer as saksnummer,
                s.id as sak_id
                from behandling b
                join sak s on s.id = b.sakid
                where b.stønadsperiode is not null
                and b.behandlingsinformasjon->>'personligOppmøte' is not null
            ) select * from søknadsbehandling_personligOppmøte;
            """.trimIndent(),
        ).let {
            val resultSøknadsbehandling = mutableListOf<QueryRadSøknadsbehandlong>()

            while (it.next()) {
                resultSøknadsbehandling.add(
                    QueryRadSøknadsbehandlong(
                        sakId = UUID.fromString(it.getString("sak_id")),
                        saksnummer = Saksnummer(it.getLong("saksnummer")),
                        behandlingId = UUID.fromString(it.getString("behandling_id")),
                        behandlingFom = LocalDate.parse(it.getString("behandling_fom")),
                        behandlingTom = LocalDate.parse(it.getString("behandling_tom")),
                        behandlingOpprettet = it.getTimestamp("behandling_opprettet").toInstant(),
                        personligOppmøte = it.getString("personligOppmøte"),
                    ),
                )
            }
            it.close()
            KonstruerVilkårOgGrunnlag.forSøknadsbehandlinger(resultSøknadsbehandling)
        }

        val migrerteRevurderinger = statement.executeQuery(
            """
            with søknadsbehandling_vedtak as (
            	select 
            	v.id as vedtak_id,
            	v.fraogmed as vedtak_fom,
            	v.tilogmed as vedtak_tom,
            	v.opprettet as vedtak_opprettet,
            	b.id as behandling_id,
            	b.behandlingsinformasjon->'personligOppmøte'->>'status' as personlig_oppmøte,
            	s.saksnummer as saksnummer,
            	s.id as sak_id
            	from vedtak v
            	join behandling_vedtak bv on bv.vedtakid = v.id
            	join behandling b on b.id = bv.søknadsbehandlingid
            	join sak s on s.id = bv.sakid
            	where v.vedtaktype = 'SØKNAD'
            ),
            revurderinger as (
            	select
            	r.id as revurdering_id,
            	(r.periode->>'fraOgMed')::date as revurdering_fom,
            	(r.periode->>'tilOgMed')::date as revurdering_tom,
            	r.opprettet as revurdering_opprettet,
            	s.id as sak_id
            	from revurdering r 
            	join behandling_vedtak bv on bv.vedtakid = r.vedtaksomrevurderesid
            	join sak s on s.id = bv.sakid
            ),
            revurdering_personligoppmøte as (
            	select
            	sv.sak_id,
            	sv.saksnummer,
            	sv.vedtak_id,
            	r.revurdering_id,
            	sv.vedtak_fom,
            	sv.vedtak_tom,
            	r.revurdering_fom,
            	r.revurdering_tom,
            	greatest(sv.vedtak_fom, r.revurdering_fom) as grlFom,
            	least(sv.vedtak_tom, r.revurdering_tom) as grlTom,
            	sv.vedtak_opprettet as grlOpprettet,
            	sv.personlig_oppmøte as personligOppmøte
            	from søknadsbehandling_vedtak sv 
            	join revurderinger r on sv.sak_id = r.sak_id
            	where 1=1
            	and sv.vedtak_opprettet < r.revurdering_opprettet
            	and ((sv.vedtak_fom between r.revurdering_fom and r.revurdering_tom) or (sv.vedtak_tom between r.revurdering_fom and r.revurdering_tom))	
            ) select * from revurdering_personligoppmøte;
            """.trimIndent(),
        ).let {
            val resultRevurdering = mutableListOf<QueryRadRevurderingRegulering>()

            while (it.next()) {
                resultRevurdering.add(
                    QueryRadRevurderingRegulering(
                        sakId = UUID.fromString(it.getString("sak_id")),
                        saksnummer = Saksnummer(it.getLong("saksnummer")),
                        vedtakId = UUID.fromString(it.getString("vedtak_id")),
                        revurderingId = UUID.fromString(it.getString("revurdering_id")),
                        vedtakFom = LocalDate.parse(it.getString("vedtak_fom")),
                        vedtakTom = LocalDate.parse(it.getString("vedtak_tom")),
                        revurderingFom = LocalDate.parse(it.getString("revurdering_fom")),
                        revurderingTom = LocalDate.parse(it.getString("revurdering_tom")),
                        grunnlagFom = LocalDate.parse(it.getString("grlFom")),
                        grunnlagTom = LocalDate.parse(it.getString("grlTom")),
                        grunnlagOpprettet = it.getTimestamp("grlOpprettet").toInstant(),
                        personligOppmøte = it.getString("personligOppmøte"),
                    ),
                )
            }
            it.close()
            KonstruerVilkårOgGrunnlag.forRevurderingOgRegulering(resultRevurdering)
        }

        val migrerteReguleringer = statement.executeQuery(
            """
            with søknadsbehandling_vedtak as (
            	select 
            	v.id as vedtak_id,
            	v.fraogmed as vedtak_fom,
            	v.tilogmed as vedtak_tom,
            	v.opprettet as vedtak_opprettet,
            	b.id as behandling_id,
            	b.behandlingsinformasjon->'personligOppmøte'->>'status' as personlig_oppmøte,
            	s.saksnummer as saksnummer,
            	s.id as sak_id
            	from vedtak v
            	join behandling_vedtak bv on bv.vedtakid = v.id
            	join behandling b on b.id = bv.søknadsbehandlingid
            	join sak s on s.id = bv.sakid
            	where v.vedtaktype = 'SØKNAD'            	
            ),
            revurderinger as (
            	select
            	r.id as revurdering_id,
            	(r.periode->>'fraOgMed')::date as revurdering_fom,
            	(r.periode->>'tilOgMed')::date as revurdering_tom,
            	r.opprettet as revurdering_opprettet,
            	s.id as sak_id
            	from regulering r 
            	join sak s on s.id = r.sakid
            ),
            revurdering_personligoppmøte as (
            	select
            	sv.sak_id,
            	sv.saksnummer,
            	sv.vedtak_id,
            	r.revurdering_id,
            	sv.vedtak_fom,
            	sv.vedtak_tom,
            	r.revurdering_fom,
            	r.revurdering_tom,
            	greatest(sv.vedtak_fom, r.revurdering_fom) as grlFom,
            	least(sv.vedtak_tom, r.revurdering_tom) as grlTom,
            	sv.vedtak_opprettet as grlOpprettet,
            	sv.personlig_oppmøte as personligOppmøte
            	from søknadsbehandling_vedtak sv 
            	join revurderinger r on sv.sak_id = r.sak_id
            	where 1=1
            	and sv.vedtak_opprettet < r.revurdering_opprettet
            	and ((sv.vedtak_fom between r.revurdering_fom and r.revurdering_tom) or (sv.vedtak_tom between r.revurdering_fom and r.revurdering_tom))	
            ) select * from revurdering_personligoppmøte;
            """.trimIndent(),
        ).let {
            val resultRegulering = mutableListOf<QueryRadRevurderingRegulering>()
            while (it.next()) {
                resultRegulering.add(
                    QueryRadRevurderingRegulering(
                        sakId = UUID.fromString(it.getString("sak_id")),
                        saksnummer = Saksnummer(it.getLong("saksnummer")),
                        vedtakId = UUID.fromString(it.getString("vedtak_id")),
                        revurderingId = UUID.fromString(it.getString("revurdering_id")),
                        vedtakFom = LocalDate.parse(it.getString("vedtak_fom")),
                        vedtakTom = LocalDate.parse(it.getString("vedtak_tom")),
                        revurderingFom = LocalDate.parse(it.getString("revurdering_fom")),
                        revurderingTom = LocalDate.parse(it.getString("revurdering_tom")),
                        grunnlagFom = LocalDate.parse(it.getString("grlFom")),
                        grunnlagTom = LocalDate.parse(it.getString("grlTom")),
                        grunnlagOpprettet = it.getTimestamp("grlOpprettet").toInstant(),
                        personligOppmøte = it.getString("personligOppmøte"),
                    ),
                )
            }
            it.close()
            KonstruerVilkårOgGrunnlag.forRevurderingOgRegulering(resultRegulering)
        }

        statement.close()

        val dbMetrics = object : DbMetrics {
            override fun <T> timeQuery(label: String, block: () -> T): T {
                return block()
            }
        }
        val db = DatabaseBuilder.buildInternal(
            dataSource = context.configuration.dataSource,
            dbMetrics = dbMetrics,
            clock = Clock.systemUTC(),
            satsFactory = SatsFactoryForSupplerendeStønad(),
        )

        val repo = PersonligOppmøteVilkårsvurderingPostgresRepo(
            personligOppmøteGrunnlagPostgresRepo = PersonligOppmøteGrunnlagPostgresRepo(
                dbMetrics = dbMetrics,
            ),
            dbMetrics = dbMetrics,
        )

        db.sessionFactory.withTransactionContext { ctx ->
            ctx.withTransaction { tx ->
                (migrerteSøknadsbehandlinger + migrerteRevurderinger + migrerteReguleringer).forEach {
                    repo.lagre(
                        behandlingId = it.behandlingInfo.id,
                        vilkår = it.vilkår,
                        tx = tx,
                    )
                }
            }
        }
    }
}

internal object KonstruerVilkårOgGrunnlag {
    fun forSøknadsbehandlinger(rader: List<QueryRadSøknadsbehandlong>): List<InsertRad> {
        return rader.map {
            val periode = Periode.create(it.behandlingFom, it.behandlingTom)
            InsertRad(
                behandlingInfo = BehandlingInfo(
                    id = it.behandlingId,
                    periode = periode,
                ),
                vilkår = PersonligOppmøteVilkår.Vurdert(
                    vurderingsperioder = nonEmptyListOf(
                        VurderingsperiodePersonligOppmøte(
                            id = UUID.randomUUID(),
                            opprettet = it.behandlingOpprettet.toTidspunkt(),
                            grunnlag = PersonligOppmøteGrunnlag(
                                id = UUID.randomUUID(),
                                opprettet = it.behandlingOpprettet.toTidspunkt(),
                                periode = periode,
                                årsak = PersonligOppmøteÅrsakDb.valueOf(it.personligOppmøte).toDomain(),
                            ),
                            periode = periode,
                        ),
                    ),
                ),
            )
        }
    }

    fun forRevurderingOgRegulering(rader: List<QueryRadRevurderingRegulering>): List<InsertRad> {
        return rader.groupBy { it.revurderingId }
            .map { (revurderingId, rad) ->
                BehandlingInfo(
                    id = revurderingId,
                    periode = Periode.create(
                        rad.first().revurderingFom,
                        rad.first().revurderingTom,
                    ), // periode er lik for alle radene til en spesifikk revurdering
                ) to rad.map {
                    VurderingsperiodePersonligOppmøte(
                        id = UUID.randomUUID(),
                        opprettet = it.grunnlagOpprettet.toTidspunkt(),
                        grunnlag = PersonligOppmøteGrunnlag(
                            id = UUID.randomUUID(),
                            opprettet = it.grunnlagOpprettet.toTidspunkt(),
                            periode = Periode.create(it.grunnlagFom, it.grunnlagTom),
                            årsak = PersonligOppmøteÅrsakDb.valueOf(it.personligOppmøte).toDomain(),
                        ),
                        periode = Periode.create(it.grunnlagFom, it.grunnlagTom),
                    )
                }
            }
            .map { (behandlingInfo, vurderingsperioder) ->
                InsertRad(
                    behandlingInfo = behandlingInfo,
                    vilkår = PersonligOppmøteVilkår.Vurdert(
                        NonEmptyList.fromListUnsafe(
                            Tidslinje(
                                periode = behandlingInfo.periode,
                                objekter = vurderingsperioder,
                            ).tidslinje,
                        ),
                    ),
                )
            }
    }
}

internal data class InsertRad(
    val behandlingInfo: BehandlingInfo,
    val vilkår: PersonligOppmøteVilkår.Vurdert,
)

internal data class BehandlingInfo(
    val id: UUID,
    val periode: Periode,
)

internal data class QueryRadRevurderingRegulering(
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val vedtakId: UUID,
    val revurderingId: UUID,
    val vedtakFom: LocalDate,
    val vedtakTom: LocalDate,
    val revurderingFom: LocalDate,
    val revurderingTom: LocalDate,
    val grunnlagFom: LocalDate,
    val grunnlagTom: LocalDate,
    val grunnlagOpprettet: Instant,
    val personligOppmøte: String,
)

internal data class QueryRadSøknadsbehandlong(
    val sakId: UUID,
    val saksnummer: Saksnummer,
    val behandlingId: UUID,
    val behandlingFom: LocalDate,
    val behandlingTom: LocalDate,
    val behandlingOpprettet: Instant,
    val personligOppmøte: String,
)
