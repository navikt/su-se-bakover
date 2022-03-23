package no.nav.su.se.bakover.database.regulering

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.deserialiserBeregning
import no.nav.su.se.bakover.database.grunnlag.GrunnlagsdataOgVilkårsvurderingerPostgresRepo
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.revurdering.RevurderingsType
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import java.util.UUID

internal class ReguleringPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val grunnlagsdataOgVilkårsvurderingerPostgresRepo: GrunnlagsdataOgVilkårsvurderingerPostgresRepo,
    private val dbMetrics: DbMetrics,
) : ReguleringRepo {
    override fun hent(id: UUID): Regulering? {
        return sessionFactory.withSession { session ->
            hent(id, session)
        }
    }

    override fun hentReguleringerSomIkkeErIverksatt(): List<Regulering.OpprettetRegulering> =
        dbMetrics.timeQuery("hentReguleringerSomIkkeErIverksatt") {
            sessionFactory.withSession { session ->
                """ select * from regulering r left join sak s on r.sakid = s.id
                    where reguleringstatus = :reguleringstatus
                """.trimIndent().hentListe(
                    mapOf("reguleringstatus" to ReguleringStatus.OPPRETTET.toString()),
                    session,
                ) { it.toRegulering(session) as Regulering.OpprettetRegulering }
            }
        }

    override fun hentForSakId(sakId: UUID, sessionContext: SessionContext): List<Regulering> =
        dbMetrics.timeQuery("hentReguleringerForSakId") {
            sessionContext.withSession { session ->
                """ select * from regulering r inner join sak s on s.id = r.sakid where sakid = :sakid """.trimIndent()
                    .hentListe(
                        mapOf("sakid" to sakId),
                        session,
                    ) { it.toRegulering(session) }
            }
        }

    // TODO jah: Flytte til [SakPostgresRepo.kt]
    override fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer> {
        return dbMetrics.timeQuery("hentSakerMedÅpenBehandlingEllerStans") {
            sessionFactory.withSession { session ->
                """
                select saksnummer
                from behandling
                         left join sak s on sakid = s.id
                where status in (${BehandlingsStatus.åpneBeregnetSøknadsbehandlingerKommaseparert()}) and lukket is false

                union

                select saksnummer
                from ( revurdering r left join behandling_vedtak bv on r.vedtaksomrevurderesid = bv.vedtakid )
                         left join sak s on s.id = sakid
                where revurderingstype in (${RevurderingsType.åpneRevurderingstyperKommaseparert()}) and avsluttet is null

                union

                select s.saksnummer
                from vedtak v
                         inner join behandling_vedtak bv on v.id = bv.vedtakid
                         inner join sak s on bv.sakid = s.id
                         inner join (select s.saksnummer, max(v.opprettet) as vedtaksdato
                                     from vedtak v
                                              inner join behandling_vedtak bv on v.id = bv.vedtakid
                                              inner join sak s on bv.sakid = s.id group by s.id) sisteVedtak on
                            sisteVedtak.saksnummer = s.saksnummer and sisteVedtak.vedtaksdato = v.opprettet
                where v.vedtaktype = 'STANS_AV_YTELSE';
                """.trimIndent().hentListe(mapOf(), session) {
                    Saksnummer(it.long("saksnummer"))
                }
            }
        }
    }

    internal fun hent(id: UUID, session: Session): Regulering? =
        dbMetrics.timeQuery("hentReguleringFraId") {
            """
            select *
            from regulering r
            
            inner join sak s
            on s.id = r.sakId
            
            where r.id = :id
            """.trimIndent()
                .hent(mapOf("id" to id), session) { row ->
                    row.toRegulering(session)
                }
        }

    override fun lagre(regulering: Regulering, sessionContext: TransactionContext) {
        dbMetrics.timeQuery("lagreRegulering") {
            sessionContext.withTransaction { session ->
                """
            insert into regulering (
                id,
                sakId,
                opprettet,
                periode,
                beregning,
                simulering,
                saksbehandler,
                reguleringStatus,
                reguleringType
            ) values (
                :id,
                :sakId,
                :opprettet,
                to_json(:periode::json),
                to_json(:beregning::json),
                to_json(:simulering::json),
                :saksbehandler,
                :reguleringStatus,
                :reguleringType
            )
                ON CONFLICT(id) do update set
                id=:id,
                sakId=:sakId,
                opprettet=:opprettet,
                periode=to_json(:periode::json),
                beregning=to_json(:beregning::json),
                simulering=to_json(:simulering::json),
                saksbehandler=:saksbehandler,
                reguleringStatus=:reguleringStatus,
                reguleringType=:reguleringType
                """.trimIndent()
                    .insert(
                        mapOf(
                            "id" to regulering.id,
                            "sakId" to regulering.sakId,
                            "periode" to serialize(regulering.periode),
                            "opprettet" to regulering.opprettet,
                            "saksbehandler" to regulering.saksbehandler.navIdent,
                            "beregning" to regulering.beregning,
                            "simulering" to regulering.simulering?.let { serialize(it) },
                            "reguleringType" to regulering.reguleringType.toString(),
                            "reguleringStatus" to when (regulering) {
                                is Regulering.IverksattRegulering -> ReguleringStatus.IVERKSATT
                                is Regulering.OpprettetRegulering -> ReguleringStatus.OPPRETTET
                            }.toString(),
                        ),
                        session,
                    )
                grunnlagsdataOgVilkårsvurderingerPostgresRepo.lagre(
                    behandlingId = regulering.id,
                    grunnlagsdataOgVilkårsvurderinger = regulering.grunnlagsdataOgVilkårsvurderinger,
                    tx = session,
                )
            }
        }
    }

    override fun defaultSessionContext(): SessionContext {
        return sessionFactory.newSessionContext()
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }

    private fun Row.toRegulering(session: Session): Regulering {
        val sakId = uuid("sakid")
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")
        val saksnummer = Saksnummer(long("saksnummer"))
        val fnr = Fnr(string("fnr"))
        val status = ReguleringStatus.valueOf(string("reguleringStatus"))
        val reguleringType = ReguleringType.valueOf(string("reguleringType"))

        val beregning = deserialiserBeregning(stringOrNull("beregning"))
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val saksbehandler = NavIdentBruker.Saksbehandler(string("saksbehandler"))
        val periode = string("periode").let { objectMapper.readValue<Periode>(it) }

        val grunnlagsdataOgVilkårsvurderinger =
            grunnlagsdataOgVilkårsvurderingerPostgresRepo.hentForRevurdering(id, session)

        return lagRegulering(
            status = status,
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            saksbehandler = saksbehandler,
            fnr = fnr,
            periode = periode,
            grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
            beregning = beregning,
            simulering = simulering,
            reguleringType = reguleringType,
        )
    }

    private enum class ReguleringStatus {
        OPPRETTET,
        IVERKSATT;
    }

    private fun lagRegulering(
        status: ReguleringStatus,
        id: UUID,
        opprettet: Tidspunkt,
        sakId: UUID,
        saksnummer: Saksnummer,
        saksbehandler: NavIdentBruker.Saksbehandler,
        fnr: Fnr,
        periode: Periode,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger.Revurdering,
        beregning: Beregning?,
        simulering: Simulering?,
        reguleringType: ReguleringType,
    ): Regulering {
        return when (status) {
            ReguleringStatus.OPPRETTET -> Regulering.OpprettetRegulering(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                saksbehandler = saksbehandler,
                fnr = fnr,
                periode = periode,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                beregning = beregning,
                simulering = simulering,
                reguleringType = reguleringType,
            )
            ReguleringStatus.IVERKSATT -> Regulering.IverksattRegulering(
                opprettetRegulering = Regulering.OpprettetRegulering(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    saksbehandler = saksbehandler,
                    fnr = fnr,
                    periode = periode,
                    grunnlagsdataOgVilkårsvurderinger = grunnlagsdataOgVilkårsvurderinger,
                    beregning = beregning,
                    simulering = simulering,
                    reguleringType = reguleringType,
                ),
                beregning = beregning!!,
                simulering = simulering!!,
            )
        }
    }
}
