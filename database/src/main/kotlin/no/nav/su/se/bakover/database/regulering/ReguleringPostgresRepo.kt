package no.nav.su.se.bakover.database.regulering

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.beregning.deserialiserBeregning
import no.nav.su.se.bakover.database.grunnlag.BosituasjongrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UtenlandsoppholdVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.vedtak.VedtakType
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringType
import no.nav.su.se.bakover.domain.regulering.Reguleringsjobb
import no.nav.su.se.bakover.domain.regulering.VedtakSomKanReguleres
import no.nav.su.se.bakover.domain.regulering.VedtakType.AVSLAG
import no.nav.su.se.bakover.domain.regulering.VedtakType.AVVIST_KLAGE
import no.nav.su.se.bakover.domain.regulering.VedtakType.ENDRING
import no.nav.su.se.bakover.domain.regulering.VedtakType.GJENOPPTAK_AV_YTELSE
import no.nav.su.se.bakover.domain.regulering.VedtakType.INGEN_ENDRING
import no.nav.su.se.bakover.domain.regulering.VedtakType.OPPHØR
import no.nav.su.se.bakover.domain.regulering.VedtakType.REGULERING
import no.nav.su.se.bakover.domain.regulering.VedtakType.STANS_AV_YTELSE
import no.nav.su.se.bakover.domain.regulering.VedtakType.SØKNAD
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

// TODO Vurdere om vi skal lage et felles repo for alle grunnlag, så vi slipper å sende inn alle sammen
internal class ReguleringPostgresRepo(
    private val dataSource: DataSource,
    private val fradragsgrunnlagPostgresRepo: FradragsgrunnlagPostgresRepo,
    private val bosituasjongrunnlagPostgresRepo: BosituasjongrunnlagPostgresRepo,
    private val uføreVilkårsvurderingPostgresRepo: UføreVilkårsvurderingPostgresRepo,
    private val formueVilkårsvurderingPostgresRepo: FormueVilkårsvurderingPostgresRepo,
    private val utenlandsoppholdVilkårsvurderingPostgresRepo: UtenlandsoppholdVilkårsvurderingPostgresRepo,
    private val sessionFactory: PostgresSessionFactory,
) : ReguleringRepo {
    override fun hent(id: UUID): Regulering? {
        return dataSource.withSession { session ->
            hent(id, session)
        }
    }

    override fun hent(saksnummer: Saksnummer, jobbnavn: String): Regulering? {
        return dataSource.withSession { session ->
            hent(saksnummer, jobbnavn, session)
        }
    }

    override fun hent(jobbnavn: Reguleringsjobb): List<Regulering> = dataSource.withSession { session ->
        """ select * from regulering r left join sak s on r.sakid = s.id
            where jobbnavn = :jobbnavn 
        """.trimIndent().hentListe(
            mapOf("jobbnavn" to jobbnavn.jobbnavn),
            session,
        ) { it.toRegulering(session) }
    }

    internal fun hent(saksnummer: Saksnummer, jobbnavn: String, session: Session): Regulering? =
        """
            select *
            from regulering r
            
            inner join sak s
            on s.id = r.sakId
            
            where s.saksnummer = :saksnummer
            and r.jobbnavn = :jobbnavn
        """.trimIndent()
            .hent(mapOf("saksnummer" to saksnummer.nummer, "jobbnavn" to jobbnavn), session) { row ->
                row.toRegulering(session)
            }

    internal fun hent(id: UUID, session: Session): Regulering? =
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

    override fun lagre(regulering: Regulering, sessionContext: TransactionContext) {
        sessionContext.withTransaction { session ->
            when (regulering) {
                is Regulering.IverksattRegulering -> lagreIntern(regulering, session)
                is Regulering.OpprettetRegulering -> lagreIntern(regulering, session)
            }
        }
    }

    private fun lagreIntern(regulering: Regulering.OpprettetRegulering, session: TransactionalSession) {
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
                reguleringType,
                jobbnavn
            ) values (
                :id,
                :sakId,
                :opprettet,
                to_json(:periode::json),
                to_json(:beregning::json),
                to_json(:simulering::json),
                :saksbehandler,
                '${ReguleringStatus.OPPRETTET}',
                :reguleringType,
                :jobbnavn
            )
                ON CONFLICT(id) do update set
                id=:id,
                sakId=:sakId,
                opprettet=:opprettet,
                periode=to_json(:periode::json),
                beregning=to_json(:beregning::json),
                simulering=to_json(:simulering::json),
                saksbehandler=:saksbehandler,
                reguleringStatus='${ReguleringStatus.OPPRETTET}',
                reguleringType=:reguleringType,
                jobbnavn=:jobbnavn
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
                    "jobbnavn" to regulering.jobbnavn.toString(),
                ),
                session,
            )
        utenlandsoppholdVilkårsvurderingPostgresRepo.lagre(
            behandlingId = regulering.id,
            vilkår = regulering.vilkårsvurderinger.utenlandsopphold,
            tx = session,
        )
    }

    private fun lagreIntern(regulering: Regulering.IverksattRegulering, session: TransactionalSession) {
        """
            update regulering set
                reguleringStatus='${ReguleringStatus.IVERKSATT}'
              where id=:id
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to regulering.id,
                ),
                session,
            )
    }

    override fun hentVedtakSomKanReguleres(fraOgMed: LocalDate): List<VedtakSomKanReguleres> {
        return dataSource.withSession { session ->
            """
                with sakogid (sakid, saksnummer, bid, fraOgMed, tilOgMed, vedtaktype, opprettet ) as (
                    select bv.sakid
                         , s.saksnummer
                         , coalesce(bv.søknadsbehandlingid, bv.revurderingid)
                         , v.fraogmed
                         , v.tilogmed
                         , v.vedtaktype
                         , v.opprettet
                    from behandling_vedtak bv
                
                    inner join vedtak v
                    on bv.vedtakid = v.id
                    
                    inner join sak s
                    on s.id = bv.sakid
                
                    where v.tilogmed >= :dato
                
                )
                
                select s.sakid
                     , s.saksnummer
                     , s.bid
                     , s.fraOgMed
                     , s.tilOgMed
                     , s.vedtaktype
                     , s.opprettet
                     , case when ( EXISTS( select 1
                                             from grunnlag_fradrag g
                                            where g.behandlingid = s.bid
                                              and g.fradragstype in ('NAVytelserTilLivsopphold', 'OffentligPensjon')
                                              and ( (s.tilOgMed >= g.fraogmed) and (:dato <= g.tilogmed) )
                         ) ) then 'MANUELL'
                         else 'AUTOMATISK'
                         end behandlingtype
                       from sakogid s
            """.trimIndent()
                .hentListe(mapOf("dato" to fraOgMed), session) {
                    it.toVedtakSomKanReguleres()
                }
        }
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
        val jobbnavn = Reguleringsjobb.valueOf(string("jobbnavn"))
        val reguleringType = ReguleringType.valueOf(string("reguleringType"))

        val beregning = deserialiserBeregning(stringOrNull("beregning"))
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val saksbehandler = NavIdentBruker.Saksbehandler(string("saksbehandler"))
        val periode = string("periode").let { objectMapper.readValue<Periode>(it) }

        val fradragsgrunnlag = fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(id, session)
        val bosituasjonsgrunnlag = bosituasjongrunnlagPostgresRepo.hentBosituasjongrunnlag(id, session)
        val grunnlagsdata = Grunnlagsdata.create(
            fradragsgrunnlag = fradragsgrunnlag,
            bosituasjon = bosituasjonsgrunnlag,
        )
        val vilkårsvurderinger = Vilkårsvurderinger.Revurdering(
            uføre = uføreVilkårsvurderingPostgresRepo.hent(id, session),
            formue = formueVilkårsvurderingPostgresRepo.hent(id, session),
            utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(id, session),
        )

        return lagRegulering(
            status = status,
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            saksbehandler = saksbehandler,
            fnr = fnr,
            periode = periode,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            beregning = beregning,
            simulering = simulering,
            reguleringType = reguleringType,
            jobbnavn = jobbnavn,
        )
    }

    private fun Row.toVedtakSomKanReguleres(): VedtakSomKanReguleres {
        val sakId = uuid("sakid")
        val behandlingId = uuid("bid")
        val saksnummer = Saksnummer(long("saksnummer"))
        val opprettet = tidspunkt("opprettet")
        val fraOgMed = localDate("fraOgMed")
        val tilOgMed = localDate("tilOgMed")
        val vedtakType = when (VedtakType.valueOf(string("vedtaktype"))) {
            VedtakType.SØKNAD -> SØKNAD
            VedtakType.AVSLAG -> AVSLAG
            VedtakType.ENDRING -> ENDRING
            VedtakType.INGEN_ENDRING -> INGEN_ENDRING
            VedtakType.OPPHØR -> OPPHØR
            VedtakType.STANS_AV_YTELSE -> STANS_AV_YTELSE
            VedtakType.GJENOPPTAK_AV_YTELSE -> GJENOPPTAK_AV_YTELSE
            VedtakType.AVVIST_KLAGE -> AVVIST_KLAGE
            VedtakType.REGULERING -> REGULERING
        }
        val reguleringType = ReguleringType.valueOf(string("behandlingtype"))

        return VedtakSomKanReguleres(
            sakId = sakId,
            saksnummer = saksnummer,
            opprettet = opprettet,
            behandlingId = behandlingId,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            vedtakType = vedtakType,
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
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        beregning: Beregning?,
        simulering: Simulering?,
        reguleringType: ReguleringType,
        jobbnavn: Reguleringsjobb,
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
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                beregning = beregning,
                simulering = simulering,
                reguleringType = reguleringType,
                jobbnavn = jobbnavn,
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
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    beregning = beregning,
                    simulering = simulering,
                    reguleringType = reguleringType,
                    jobbnavn = jobbnavn,
                ),
            )
        }
    }
}
