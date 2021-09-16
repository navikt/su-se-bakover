package no.nav.su.se.bakover.database.revurdering

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.grunnlag.BosituasjongrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.vedtak.VedtakPosgresRepo
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID
import javax.sql.DataSource

interface RevurderingRepo {
    fun hent(id: UUID): AbstraktRevurdering?
    fun hentEventuellTidligereAttestering(id: UUID): Attestering?
    fun lagre(revurdering: AbstraktRevurdering)
    fun oppdaterForhåndsvarsel(id: UUID, forhåndsvarsel: Forhåndsvarsel)
}

enum class RevurderingsType {
    OPPRETTET,
    BEREGNET_INNVILGET,
    BEREGNET_OPPHØRT,
    BEREGNET_INGEN_ENDRING,
    SIMULERT_INNVILGET,
    SIMULERT_OPPHØRT,
    TIL_ATTESTERING_INNVILGET,
    TIL_ATTESTERING_OPPHØRT,
    TIL_ATTESTERING_INGEN_ENDRING,
    IVERKSATT_INNVILGET,
    IVERKSATT_OPPHØRT,
    IVERKSATT_INGEN_ENDRING,
    UNDERKJENT_INNVILGET,
    UNDERKJENT_OPPHØRT,
    UNDERKJENT_INGEN_ENDRING,
    SIMULERT_STANS,
    IVERKSATT_STANS,
}

internal class RevurderingPostgresRepo(
    private val dataSource: DataSource,
    private val fradragsgrunnlagPostgresRepo: FradragsgrunnlagPostgresRepo,
    private val bosituasjonsgrunnlagPostgresRepo: BosituasjongrunnlagPostgresRepo,
    private val uføreVilkårsvurderingRepo: UføreVilkårsvurderingPostgresRepo,
    private val formueVilkårsvurderingRepo: FormueVilkårsvurderingPostgresRepo,
    søknadsbehandlingRepo: SøknadsbehandlingPostgresRepo,
    private val dbMetrics: DbMetrics,
) : RevurderingRepo {
    private val vedtakRepo = VedtakPosgresRepo(dataSource, søknadsbehandlingRepo, this, dbMetrics)

    override fun hent(id: UUID): AbstraktRevurdering? {
        return dbMetrics.timeQuery("hentRevurdering") {
            dataSource.withSession { session ->
                hent(id, session)
            }
        }
    }

    internal fun hent(id: UUID, session: Session): AbstraktRevurdering? =
        """
                SELECT *
                FROM revurdering
                WHERE id = :id
        """.trimIndent()
            .hent(mapOf("id" to id), session) { row ->
                row.toRevurdering(session)
            }

    override fun hentEventuellTidligereAttestering(id: UUID): Attestering? =
        dataSource.withSession { session ->
            "select * from revurdering where id = :id"
                .hent(mapOf("id" to id), session) { row ->
                    row.string("attestering").let {
                        val attesteringer = Attesteringshistorikk(objectMapper.readValue(it))
                        attesteringer.hentAttesteringer().lastOrNull()
                    }
                }
        }

    override fun lagre(revurdering: AbstraktRevurdering) {
        dataSource.withSession { session ->
            when (revurdering) {
                is Revurdering -> lagre(revurdering, session)
                is StansAvYtelseRevurdering -> lagre(revurdering, session)
            }
        }
    }

    internal fun lagre(revurdering: Revurdering, session: Session) {
        when (revurdering) {
            is OpprettetRevurdering -> lagre(revurdering, session)
            is BeregnetRevurdering -> lagre(revurdering, session)
            is SimulertRevurdering -> lagre(revurdering, session)
            is RevurderingTilAttestering -> lagre(revurdering, session)
            is IverksattRevurdering -> lagre(revurdering, session)
            is UnderkjentRevurdering -> lagre(revurdering, session)
        }
    }

    internal fun lagre(revurdering: StansAvYtelseRevurdering, session: Session) {
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
                        informasjonSomRevurderes,
                        attestering
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
                        to_json(:informasjonSomRevurderes::json),
                        to_json(:attestering::json)
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
                            "informasjonSomRevurderes" to objectMapper.writeValueAsString(revurdering.informasjonSomRevurderes),
                            "attestering" to Attesteringshistorikk.empty().hentAttesteringer().serialize(),
                        ),
                        session,
                    )
                fradragsgrunnlagPostgresRepo.lagreFradragsgrunnlag(
                    revurdering.id,
                    revurdering.grunnlagsdata.fradragsgrunnlag,
                )
                bosituasjonsgrunnlagPostgresRepo.lagreBosituasjongrunnlag(
                    revurdering.id,
                    revurdering.grunnlagsdata.bosituasjon,
                )
                uføreVilkårsvurderingRepo.lagre(revurdering.id, revurdering.vilkårsvurderinger.uføre)
                formueVilkårsvurderingRepo.lagre(revurdering.id, revurdering.vilkårsvurderinger.formue)
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
                        session,
                    )
            }
        }
    }

    override fun oppdaterForhåndsvarsel(id: UUID, forhåndsvarsel: Forhåndsvarsel) {
        dataSource.withSession { session ->
            """
                UPDATE
                    revurdering r
                SET
                    forhåndsvarsel = to_json(:forhandsvarsel::json)
                WHERE
                    r.id = :id
            """.trimIndent()
                .oppdatering(
                    mapOf(
                        "id" to id,
                        "forhandsvarsel" to serialize(ForhåndsvarselDto.from(forhåndsvarsel)),
                    ),
                    session,
                )
        }
    }

    internal fun hentRevurderingerForSak(sakId: UUID, session: Session): List<AbstraktRevurdering> =
        """
            SELECT
                r.*
            FROM
                revurdering r
                INNER JOIN behandling_vedtak bv
                    ON r.vedtakSomRevurderesId = bv.vedtakId
            WHERE bv.sakid=:sakId
        """.trimIndent()
            .hentListe(mapOf("sakId" to sakId), session) {
                it.toRevurdering(session)
            }

    private fun Row.toRevurdering(session: Session): AbstraktRevurdering {
        val id = uuid("id")
        val periode = string("periode").let { objectMapper.readValue<Periode>(it) }
        val opprettet = tidspunkt("opprettet")
        val tilRevurdering = vedtakRepo.hent(uuid("vedtakSomRevurderesId"), session)!! as VedtakSomKanRevurderes
        val beregning = stringOrNull("beregning")?.let { objectMapper.readValue<PersistertBeregning>(it) }
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val saksbehandler = string("saksbehandler")
        val oppgaveId = stringOrNull("oppgaveid")
        val attesteringer = Attesteringshistorikk(objectMapper.readValue(string("attestering")))
        val fritekstTilBrev = stringOrNull("fritekstTilBrev")
        val årsak = string("årsak")
        val begrunnelse = string("begrunnelse")
        val revurderingsårsak = Revurderingsårsak.create(
            årsak = årsak,
            begrunnelse = begrunnelse,
        )
        val skalFøreTilBrevutsending = boolean("skalFøreTilBrevutsending")
        val forhåndsvarsel = stringOrNull("forhåndsvarsel")?.let {
            objectMapper.readValue<ForhåndsvarselDto>(it).toDomain()
        }

        val informasjonSomRevurderes: InformasjonSomRevurderes =
            InformasjonSomRevurderes.create(objectMapper.readValue<Map<Revurderingsteg, Vurderingstatus>>(string("informasjonSomRevurderes")))

        val fradragsgrunnlag = fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(id, session)
        val bosituasjonsgrunnlag = bosituasjonsgrunnlagPostgresRepo.hentBosituasjongrunnlag(id, session)
        val grunnlagsdata = Grunnlagsdata.create(
            fradragsgrunnlag = fradragsgrunnlag,
            bosituasjon = bosituasjonsgrunnlag,
        )
        val vilkårsvurderinger = Vilkårsvurderinger(
            uføre = uføreVilkårsvurderingRepo.hent(id, session),
            formue = formueVilkårsvurderingRepo.hent(id, session),
        )

        return when (RevurderingsType.valueOf(string("revurderingsType"))) {
            RevurderingsType.UNDERKJENT_INNVILGET -> UnderkjentRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                simulering = simulering!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                attesteringer = attesteringer,
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
            )
            RevurderingsType.UNDERKJENT_OPPHØRT -> UnderkjentRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                simulering = simulering!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                attesteringer = attesteringer,
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
            )
            RevurderingsType.IVERKSATT_INNVILGET -> IverksattRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                beregning = beregning!!,
                simulering = simulering!!,
                attesteringer = attesteringer,
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
            )
            RevurderingsType.IVERKSATT_OPPHØRT -> IverksattRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                simulering = simulering!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                attesteringer = attesteringer,
                grunnlagsdata = grunnlagsdata,
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel!!,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
            )
            RevurderingsType.TIL_ATTESTERING_INNVILGET -> RevurderingTilAttestering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                grunnlagsdata = grunnlagsdata,
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel!!,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
            )
            RevurderingsType.TIL_ATTESTERING_OPPHØRT -> RevurderingTilAttestering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel!!,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
            )
            RevurderingsType.SIMULERT_INNVILGET -> SimulertRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
            )
            RevurderingsType.SIMULERT_OPPHØRT -> SimulertRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                grunnlagsdata = grunnlagsdata,
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
            )
            RevurderingsType.BEREGNET_INNVILGET -> BeregnetRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                grunnlagsdata = grunnlagsdata,
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
            )
            RevurderingsType.BEREGNET_OPPHØRT -> BeregnetRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                grunnlagsdata = grunnlagsdata,
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
            )
            RevurderingsType.OPPRETTET -> OpprettetRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                grunnlagsdata = grunnlagsdata,
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
            )
            RevurderingsType.BEREGNET_INGEN_ENDRING -> BeregnetRevurdering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
            )
            RevurderingsType.TIL_ATTESTERING_INGEN_ENDRING -> RevurderingTilAttestering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                skalFøreTilBrevutsending = skalFøreTilBrevutsending,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
            )
            RevurderingsType.IVERKSATT_INGEN_ENDRING -> IverksattRevurdering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                attesteringer = attesteringer,
                skalFøreTilBrevutsending = skalFøreTilBrevutsending,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
            )
            RevurderingsType.UNDERKJENT_INGEN_ENDRING -> UnderkjentRevurdering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                attesteringer = attesteringer,
                skalFøreTilBrevutsending = skalFøreTilBrevutsending,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
            )
            RevurderingsType.SIMULERT_STANS -> StansAvYtelseRevurdering.SimulertStansAvYtelse(
                id = id,
                opprettet = opprettet,
                periode = periode,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                simulering = simulering!!,
                revurderingsårsak = revurderingsårsak,
            )
            RevurderingsType.IVERKSATT_STANS -> StansAvYtelseRevurdering.IverksattStansAvYtelse(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                simulering = simulering!!,
                attesteringer = attesteringer,
                revurderingsårsak = revurderingsårsak,
            )
        }
    }

    private fun lagre(revurdering: OpprettetRevurdering, session: Session) =
        """
                    insert into revurdering (
                        id,
                        opprettet,
                        periode,
                        beregning,
                        simulering,
                        saksbehandler,
                        oppgaveId,
                        revurderingsType,
                        attestering,
                        vedtakSomRevurderesId,
                        fritekstTilBrev,
                        årsak,
                        begrunnelse,
                        forhåndsvarsel,
                        informasjonSomRevurderes
                    ) values (
                        :id,
                        :opprettet,
                        to_json(:periode::json),
                        null,
                        null,
                        :saksbehandler,
                        :oppgaveId,
                        '${RevurderingsType.OPPRETTET}',
                        to_jsonb(:attestering::jsonb),
                        :vedtakSomRevurderesId,
                        :fritekstTilBrev,
                        :arsak,
                        :begrunnelse,
                        to_json(:forhandsvarsel::json),
                        to_json(:informasjonSomRevurderes::json)
                    )
                        ON CONFLICT(id) do update set
                        id=:id,
                        opprettet=:opprettet,
                        periode=to_json(:periode::json),
                        beregning=null,
                        simulering=null,
                        saksbehandler=:saksbehandler,
                        oppgaveId=:oppgaveId,
                        revurderingsType='${RevurderingsType.OPPRETTET}',
                        attestering=to_jsonb(:attestering::jsonb),
                        vedtakSomRevurderesId=:vedtakSomRevurderesId,
                        fritekstTilBrev=:fritekstTilBrev,
                        årsak=:arsak,
                        begrunnelse=:begrunnelse,
                        forhåndsvarsel=to_json(:forhandsvarsel::json),
                        informasjonSomRevurderes=to_json(:informasjonSomRevurderes::json)
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to revurdering.id,
                    "periode" to objectMapper.writeValueAsString(revurdering.periode),
                    "opprettet" to revurdering.opprettet,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "vedtakSomRevurderesId" to revurdering.tilRevurdering.id,
                    "fritekstTilBrev" to revurdering.fritekstTilBrev,
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "forhandsvarsel" to revurdering.forhåndsvarsel?.let {
                        objectMapper.writeValueAsString(ForhåndsvarselDto.from(it))
                    },
                    "informasjonSomRevurderes" to objectMapper.writeValueAsString(revurdering.informasjonSomRevurderes),
                    "attestering" to revurdering.attesteringer.hentAttesteringer().serialize(),
                ),
                session,
            )

    private fun lagre(revurdering: BeregnetRevurdering, session: Session) =
        """
                    update
                        revurdering
                    set
                        beregning = to_json(:beregning::json),
                        simulering = null,
                        revurderingsType = :revurderingsType,
                        saksbehandler = :saksbehandler,
                        årsak = :arsak,
                        begrunnelse = :begrunnelse,
                        informasjonSomRevurderes = to_json(:informasjonSomRevurderes::json)
                    where
                        id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "revurderingsType" to when (revurdering) {
                        is BeregnetRevurdering.IngenEndring -> RevurderingsType.BEREGNET_INGEN_ENDRING
                        is BeregnetRevurdering.Innvilget -> RevurderingsType.BEREGNET_INNVILGET
                        is BeregnetRevurdering.Opphørt -> RevurderingsType.BEREGNET_OPPHØRT
                    },
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "informasjonSomRevurderes" to objectMapper.writeValueAsString(revurdering.informasjonSomRevurderes),
                ),
                session,
            )

    private fun lagre(revurdering: SimulertRevurdering, session: Session) =
        """
                    update
                        revurdering
                    set
                        saksbehandler = :saksbehandler,
                        beregning = to_json(:beregning::json),
                        simulering = to_json(:simulering::json),
                        revurderingsType = :revurderingsType,
                        årsak = :arsak,
                        begrunnelse =:begrunnelse,
                        forhåndsvarsel = to_json(:forhandsvarsel::json)
                    where
                        id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "simulering" to objectMapper.writeValueAsString(revurdering.simulering),
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to when (revurdering) {
                        is SimulertRevurdering.Innvilget -> RevurderingsType.SIMULERT_INNVILGET
                        is SimulertRevurdering.Opphørt -> RevurderingsType.SIMULERT_OPPHØRT
                    },
                    "forhandsvarsel" to revurdering.forhåndsvarsel?.let {
                        objectMapper.writeValueAsString(ForhåndsvarselDto.from(it))
                    },
                ),
                session,
            )

    private fun lagre(revurdering: RevurderingTilAttestering, session: Session) =
        """
                    update
                        revurdering
                    set
                        saksbehandler = :saksbehandler,
                        beregning = to_json(:beregning::json),
                        simulering = to_json(:simulering::json),
                        oppgaveId = :oppgaveId,
                        fritekstTilBrev = :fritekstTilBrev,
                        årsak = :arsak,
                        begrunnelse =:begrunnelse,
                        revurderingsType = :revurderingsType,
                        skalFøreTilBrevutsending = :skalFoereTilBrevutsending
                    where
                        id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "simulering" to when (revurdering) {
                        is RevurderingTilAttestering.IngenEndring -> null
                        is RevurderingTilAttestering.Innvilget -> objectMapper.writeValueAsString(revurdering.simulering)
                        is RevurderingTilAttestering.Opphørt -> objectMapper.writeValueAsString(revurdering.simulering)
                    },
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "fritekstTilBrev" to revurdering.fritekstTilBrev,
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to when (revurdering) {
                        is RevurderingTilAttestering.IngenEndring -> RevurderingsType.TIL_ATTESTERING_INGEN_ENDRING
                        is RevurderingTilAttestering.Innvilget -> RevurderingsType.TIL_ATTESTERING_INNVILGET
                        is RevurderingTilAttestering.Opphørt -> RevurderingsType.TIL_ATTESTERING_OPPHØRT
                    },
                    "skalFoereTilBrevutsending" to when (revurdering) {
                        is RevurderingTilAttestering.IngenEndring -> revurdering.skalFøreTilBrevutsending
                        is RevurderingTilAttestering.Innvilget -> true
                        is RevurderingTilAttestering.Opphørt -> true
                    },
                ),
                session,
            )

    private fun lagre(revurdering: IverksattRevurdering, session: Session) =
        """
                    update
                        revurdering
                    set
                        saksbehandler = :saksbehandler,
                        beregning = to_json(:beregning::json),
                        simulering = to_json(:simulering::json),
                        oppgaveId = :oppgaveId,
                        attestering = to_jsonb(:attestering::jsonb),
                        årsak = :arsak,
                        begrunnelse =:begrunnelse,
                        revurderingsType = :revurderingsType
                    where
                        id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "simulering" to when (revurdering) {
                        is IverksattRevurdering.IngenEndring -> null
                        is IverksattRevurdering.Innvilget -> objectMapper.writeValueAsString(revurdering.simulering)
                        is IverksattRevurdering.Opphørt -> objectMapper.writeValueAsString(revurdering.simulering)
                    },
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "attestering" to revurdering.attesteringer.hentAttesteringer().serialize(),
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to when (revurdering) {
                        is IverksattRevurdering.IngenEndring -> RevurderingsType.IVERKSATT_INGEN_ENDRING
                        is IverksattRevurdering.Innvilget -> RevurderingsType.IVERKSATT_INNVILGET
                        is IverksattRevurdering.Opphørt -> RevurderingsType.IVERKSATT_OPPHØRT
                    },
                ),
                session,
            )

    private fun lagre(revurdering: UnderkjentRevurdering, session: Session) =
        """
                    update
                        revurdering
                    set
                        oppgaveId = :oppgaveId,
                        attestering = to_jsonb(:attestering::jsonb),
                        årsak = :arsak,
                        begrunnelse =:begrunnelse,
                        revurderingsType = :revurderingsType
                    where
                        id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "attestering" to revurdering.attesteringer.hentAttesteringer().serialize(),
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to when (revurdering) {
                        is UnderkjentRevurdering.IngenEndring -> RevurderingsType.UNDERKJENT_INGEN_ENDRING
                        is UnderkjentRevurdering.Innvilget -> RevurderingsType.UNDERKJENT_INNVILGET
                        is UnderkjentRevurdering.Opphørt -> RevurderingsType.UNDERKJENT_OPPHØRT
                    },
                ),
                session,
            )

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
    )
    @JsonSubTypes(
        JsonSubTypes.Type(value = ForhåndsvarselDto.IngenForhåndsvarsel::class, name = "IngenForhåndsvarsel"),
        JsonSubTypes.Type(value = ForhåndsvarselDto.Sendt::class, name = "Sendt"),
        JsonSubTypes.Type(value = ForhåndsvarselDto.Besluttet::class, name = "Besluttet"),
    )
    sealed class ForhåndsvarselDto {
        object IngenForhåndsvarsel : ForhåndsvarselDto()

        object Sendt : ForhåndsvarselDto()

        data class Besluttet(
            val valg: BeslutningEtterForhåndsvarsling,
            val begrunnelse: String,
        ) : ForhåndsvarselDto()

        fun toDomain(): Forhåndsvarsel =
            when (this) {
                is Sendt -> Forhåndsvarsel.SkalForhåndsvarsles.Sendt
                is IngenForhåndsvarsel -> Forhåndsvarsel.IngenForhåndsvarsel
                is Besluttet -> Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                    valg = valg,
                    begrunnelse = begrunnelse,
                )
            }

        companion object {
            fun from(forhåndsvarsel: Forhåndsvarsel) =
                when (forhåndsvarsel) {
                    is Forhåndsvarsel.SkalForhåndsvarsles.Sendt -> Sendt
                    is Forhåndsvarsel.IngenForhåndsvarsel -> IngenForhåndsvarsel
                    is Forhåndsvarsel.SkalForhåndsvarsles.Besluttet -> Besluttet(
                        valg = forhåndsvarsel.valg,
                        begrunnelse = forhåndsvarsel.begrunnelse,
                    )
                }
        }
    }
}
