package no.nav.su.se.bakover.database.revurdering

import arrow.core.getOrHandle
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.avkorting.AvkortingVedRevurderingDb
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.avkorting.toDb
import no.nav.su.se.bakover.database.avkorting.toDomain
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.beregning.deserialiserBeregning
import no.nav.su.se.bakover.database.grunnlag.BosituasjongrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FormueVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.FradragsgrunnlagPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UføreVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.grunnlag.UtenlandsoppholdVilkårsvurderingPostgresRepo
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.revurdering.RevurderingsType.Companion.toRevurderingsType
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.vedtak.VedtakPostgresRepo
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
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
    SIMULERT_GJENOPPTAK,
    IVERKSATT_GJENOPPTAK;

    companion object {
        internal fun Revurdering.toRevurderingsType(): String {
            return when (this) {
                is OpprettetRevurdering -> OPPRETTET
                is BeregnetRevurdering.Innvilget -> BEREGNET_INNVILGET
                is BeregnetRevurdering.Opphørt -> BEREGNET_OPPHØRT
                is BeregnetRevurdering.IngenEndring -> BEREGNET_INGEN_ENDRING
                is SimulertRevurdering.Innvilget -> SIMULERT_INNVILGET
                is SimulertRevurdering.Opphørt -> SIMULERT_OPPHØRT
                is RevurderingTilAttestering.Innvilget -> TIL_ATTESTERING_INNVILGET
                is RevurderingTilAttestering.Opphørt -> TIL_ATTESTERING_OPPHØRT
                is RevurderingTilAttestering.IngenEndring -> TIL_ATTESTERING_INGEN_ENDRING
                is IverksattRevurdering.Innvilget -> IVERKSATT_INNVILGET
                is IverksattRevurdering.Opphørt -> IVERKSATT_OPPHØRT
                is IverksattRevurdering.IngenEndring -> IVERKSATT_INGEN_ENDRING
                is UnderkjentRevurdering.Innvilget -> UNDERKJENT_INNVILGET
                is UnderkjentRevurdering.Opphørt -> UNDERKJENT_OPPHØRT
                is UnderkjentRevurdering.IngenEndring -> UNDERKJENT_INGEN_ENDRING
                is AvsluttetRevurdering -> this.underliggendeRevurdering.toRevurderingsType()
            }.toString()
        }
    }
}

internal class RevurderingPostgresRepo(
    private val dataSource: DataSource,
    private val fradragsgrunnlagPostgresRepo: FradragsgrunnlagPostgresRepo,
    private val bosituasjonsgrunnlagPostgresRepo: BosituasjongrunnlagPostgresRepo,
    private val uføreVilkårsvurderingRepo: UføreVilkårsvurderingPostgresRepo,
    private val utlandsoppholdVilkårsvurderingRepo: UtenlandsoppholdVilkårsvurderingPostgresRepo,
    private val formueVilkårsvurderingRepo: FormueVilkårsvurderingPostgresRepo,
    søknadsbehandlingRepo: SøknadsbehandlingPostgresRepo,
    klageRepo: KlagePostgresRepo,
    private val dbMetrics: DbMetrics,
    private val sessionFactory: PostgresSessionFactory,
    private val avkortingsvarselRepo: AvkortingsvarselPostgresRepo,
) : RevurderingRepo {
    private val vedtakRepo =
        VedtakPostgresRepo(dataSource, søknadsbehandlingRepo, this, klageRepo, dbMetrics, sessionFactory)

    private val stansAvYtelseRepo = StansAvYtelsePostgresRepo(
        fradragsgrunnlagPostgresRepo = fradragsgrunnlagPostgresRepo,
        bosituasjonsgrunnlagPostgresRepo = bosituasjonsgrunnlagPostgresRepo,
        uføreVilkårsvurderingRepo = uføreVilkårsvurderingRepo,
        formueVilkårsvurderingRepo = formueVilkårsvurderingRepo,
        utlandsoppholdVilkårsvurderingRepo = utlandsoppholdVilkårsvurderingRepo,
    )

    private val gjenopptakAvYtelseRepo = GjenopptakAvYtelsePostgresRepo(
        fradragsgrunnlagPostgresRepo = fradragsgrunnlagPostgresRepo,
        bosituasjonsgrunnlagPostgresRepo = bosituasjonsgrunnlagPostgresRepo,
        uføreVilkårsvurderingRepo = uføreVilkårsvurderingRepo,
        formueVilkårsvurderingRepo = formueVilkårsvurderingRepo,
        utlandsoppholdVilkårsvurderingRepo = utlandsoppholdVilkårsvurderingRepo,
    )

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

    override fun lagre(revurdering: AbstraktRevurdering, transactionContext: TransactionContext) {
        when (revurdering) {
            is Revurdering -> {
                transactionContext.withTransaction {
                    lagre(revurdering, it)
                }
            }
            is GjenopptaYtelseRevurdering -> {
                transactionContext.withTransaction {
                    gjenopptakAvYtelseRepo.lagre(revurdering, it)
                }
            }
            is StansAvYtelseRevurdering -> {
                transactionContext.withTransaction {
                    stansAvYtelseRepo.lagre(revurdering, it)
                }
            }
        }
    }

    internal fun lagre(revurdering: Revurdering, session: TransactionalSession) {
        when (revurdering) {
            is OpprettetRevurdering -> lagre(revurdering, session)
            is BeregnetRevurdering -> lagre(revurdering, session)
            is SimulertRevurdering -> lagre(revurdering, session)
            is RevurderingTilAttestering -> lagre(revurdering, session)
            is IverksattRevurdering -> lagre(revurdering, session)
            is UnderkjentRevurdering -> lagre(revurdering, session)
            is AvsluttetRevurdering -> lagre(revurdering, session)
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
        val status = RevurderingsType.valueOf(string("revurderingsType"))
        val periode = string("periode").let { objectMapper.readValue<Periode>(it) }
        val opprettet = tidspunkt("opprettet")
        val tilRevurdering = vedtakRepo.hent(uuid("vedtakSomRevurderesId"), session)!! as VedtakSomKanRevurderes
        val beregning = deserialiserBeregning(stringOrNull("beregning"))
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val saksbehandler = string("saksbehandler")
        val oppgaveId = stringOrNull("oppgaveid")
        val attesteringer = Attesteringshistorikk.create(objectMapper.readValue(string("attestering")))
        val fritekstTilBrev = stringOrNull("fritekstTilBrev")
        val årsak = string("årsak")
        val begrunnelse = string("begrunnelse")
        val revurderingsårsak = Revurderingsårsak.create(
            årsak = årsak,
            begrunnelse = begrunnelse,
        )
        val skalFøreTilBrevutsending = boolean("skalFøreTilBrevutsending")
        val forhåndsvarsel = stringOrNull("forhåndsvarsel")?.let {
            objectMapper.readValue<ForhåndsvarselDatabaseJson>(it).toDomain()
        }

        val informasjonSomRevurderes = stringOrNull("informasjonSomRevurderes")?.let {
            InformasjonSomRevurderes.create(objectMapper.readValue<Map<Revurderingsteg, Vurderingstatus>>(it))
        }

        val fradragsgrunnlag = fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(id, session)
        val bosituasjonsgrunnlag = bosituasjonsgrunnlagPostgresRepo.hentBosituasjongrunnlag(id, session)
        val grunnlagsdata = Grunnlagsdata.create(
            fradragsgrunnlag = fradragsgrunnlag,
            bosituasjon = bosituasjonsgrunnlag,
        )
        val vilkårsvurderinger = Vilkårsvurderinger.Revurdering(
            uføre = uføreVilkårsvurderingRepo.hent(id, session),
            formue = formueVilkårsvurderingRepo.hent(id, session),
            utenlandsopphold = utlandsoppholdVilkårsvurderingRepo.hent(id, session),
        )

        val avkorting = stringOrNull("avkorting")?.let {
            objectMapper.readValue<AvkortingVedRevurderingDb>(it).toDomain()
        }

        val revurdering = lagRevurdering(
            status = status,
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = simulering,
            oppgaveId = oppgaveId,
            attesteringer = attesteringer,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = forhåndsvarsel,
            skalFøreTilBrevutsending = skalFøreTilBrevutsending,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            avkorting = avkorting,
        )

        val avsluttet = stringOrNull("avsluttet")?.let {
            objectMapper.readValue<AvsluttetRevurderingInfo>(it)
        }

        if (avsluttet != null) {
            return when (revurdering) {
                is GjenopptaYtelseRevurdering -> GjenopptaYtelseRevurdering.AvsluttetGjenoppta.tryCreate(
                    gjenopptakAvYtelseRevurdering = revurdering,
                    begrunnelse = avsluttet.begrunnelse,
                    tidspunktAvsluttet = avsluttet.tidspunktAvsluttet,
                ).getOrHandle {
                    throw IllegalStateException("Kunne ikke lage en avsluttet gjenoppta revurdering. Se innhold i databasen. revurderingsId $id")
                }
                is Revurdering -> {
                    return AvsluttetRevurdering.tryCreate(
                        underliggendeRevurdering = revurdering,
                        begrunnelse = avsluttet.begrunnelse,
                        fritekst = avsluttet.fritekst,
                        tidspunktAvsluttet = avsluttet.tidspunktAvsluttet,
                    ).getOrHandle {
                        throw IllegalStateException("Kunne ikke lage en avsluttet revurdering. Se innhold i databasen. revurderingsId $id")
                    }
                }
                is StansAvYtelseRevurdering -> StansAvYtelseRevurdering.AvsluttetStansAvYtelse.tryCreate(
                    stansAvYtelseRevurdering = revurdering,
                    begrunnelse = avsluttet.begrunnelse,
                    tidspunktAvsluttet = avsluttet.tidspunktAvsluttet,
                ).getOrHandle {
                    throw IllegalStateException("Kunne ikke lage en avsluttet stans av ytelse. Se innhold i databasen. revurderingsId $id")
                }
            }
        }
        return revurdering
    }

    private fun lagre(revurdering: OpprettetRevurdering, session: TransactionalSession) {
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
                        informasjonSomRevurderes,
                        avkorting
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
                        to_json(:informasjonSomRevurderes::json),
                        to_json(:avkorting::json)
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
                        informasjonSomRevurderes=to_json(:informasjonSomRevurderes::json),
                        avkorting = to_json(:avkorting::json)
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
                        objectMapper.writeValueAsString(ForhåndsvarselDatabaseJson.from(it))
                    },
                    "informasjonSomRevurderes" to objectMapper.writeValueAsString(revurdering.informasjonSomRevurderes),
                    "attestering" to revurdering.attesteringer.serialize(),
                    "avkorting" to objectMapper.writeValueAsString(revurdering.avkorting.toDb()),
                ),
                session,
            )
        utlandsoppholdVilkårsvurderingRepo.lagre(
            behandlingId = revurdering.id,
            vilkår = revurdering.vilkårsvurderinger.utenlandsopphold,
            tx = session,
        )
    }

    private fun lagre(revurdering: BeregnetRevurdering, tx: TransactionalSession) =
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
                        informasjonSomRevurderes = to_json(:informasjonSomRevurderes::json),
                        avkorting = to_json(:avkorting::json)
                    where
                        id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to revurdering.beregning,
                    "revurderingsType" to revurdering.toRevurderingsType(),
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "informasjonSomRevurderes" to objectMapper.writeValueAsString(revurdering.informasjonSomRevurderes),
                    "avkorting" to objectMapper.writeValueAsString(revurdering.avkorting.toDb()),
                ),
                tx,
            )

    private fun lagre(revurdering: SimulertRevurdering, tx: TransactionalSession) {
        return """
                    update
                        revurdering
                    set
                        saksbehandler = :saksbehandler,
                        beregning = to_json(:beregning::json),
                        simulering = to_json(:simulering::json),
                        revurderingsType = :revurderingsType,
                        årsak = :arsak,
                        begrunnelse =:begrunnelse,
                        forhåndsvarsel = to_json(:forhandsvarsel::json),
                        avkorting = to_json(:avkorting::json)
                    where
                        id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to revurdering.beregning,
                    "simulering" to objectMapper.writeValueAsString(revurdering.simulering),
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to revurdering.toRevurderingsType(),
                    "forhandsvarsel" to revurdering.forhåndsvarsel?.let {
                        objectMapper.writeValueAsString(ForhåndsvarselDatabaseJson.from(it))
                    },
                    "avkorting" to objectMapper.writeValueAsString(revurdering.avkorting.toDb()),
                ),
                tx,
            )
    }

    private fun lagre(revurdering: RevurderingTilAttestering, session: TransactionalSession) =
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
                        skalFøreTilBrevutsending = :skalFoereTilBrevutsending,
                        forhåndsvarsel = to_json(:forhandsvarsel::json),
                        avkorting = to_json(:avkorting::json)
                    where
                        id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to revurdering.beregning,
                    "simulering" to when (revurdering) {
                        is RevurderingTilAttestering.IngenEndring -> null
                        is RevurderingTilAttestering.Innvilget -> objectMapper.writeValueAsString(revurdering.simulering)
                        is RevurderingTilAttestering.Opphørt -> objectMapper.writeValueAsString(revurdering.simulering)
                    },
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "fritekstTilBrev" to revurdering.fritekstTilBrev,
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to revurdering.toRevurderingsType(),
                    "skalFoereTilBrevutsending" to revurdering.skalFøreTilUtsendingAvVedtaksbrev,
                    "forhandsvarsel" to revurdering.forhåndsvarsel?.let {
                        objectMapper.writeValueAsString(ForhåndsvarselDatabaseJson.from(it))
                    },
                    "avkorting" to objectMapper.writeValueAsString(revurdering.avkorting.toDb()),
                ),
                session,
            )

    private fun lagre(revurdering: IverksattRevurdering, tx: TransactionalSession) {
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
                        revurderingsType = :revurderingsType,
                        avkorting = to_json(:avkorting::json)
                    where
                        id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to revurdering.beregning,
                    "simulering" to when (revurdering) {
                        is IverksattRevurdering.IngenEndring -> null
                        is IverksattRevurdering.Innvilget -> objectMapper.writeValueAsString(revurdering.simulering)
                        is IverksattRevurdering.Opphørt -> objectMapper.writeValueAsString(revurdering.simulering)
                    },
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "attestering" to revurdering.attesteringer.serialize(),
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to revurdering.toRevurderingsType(),
                    "avkorting" to objectMapper.writeValueAsString(revurdering.avkorting.toDb()),
                ),
                tx,
            )

        when (val iverksatt = revurdering.avkorting) {
            is AvkortingVedRevurdering.Iverksatt.AnnullerUtestående -> {
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = iverksatt.annullerUtestående,
                    tx = tx,
                )
            }
            is AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående -> {
                // noop
            }
            is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel -> {
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = iverksatt.avkortingsvarsel,
                    tx = tx,
                )
            }
            is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = iverksatt.avkortingsvarsel,
                    tx = tx,
                )
                avkortingsvarselRepo.lagre(
                    avkortingsvarsel = iverksatt.annullerUtestående,
                    tx = tx,
                )
            }
            is AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres -> {
                // noop
            }
        }
    }

    private fun lagre(revurdering: UnderkjentRevurdering, session: TransactionalSession) =
        """
                    update
                        revurdering
                    set
                        oppgaveId = :oppgaveId,
                        attestering = to_jsonb(:attestering::jsonb),
                        årsak = :arsak,
                        begrunnelse =:begrunnelse,
                        revurderingsType = :revurderingsType,
                        avkorting=to_json(:avkorting::json)
                    where
                        id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "attestering" to revurdering.attesteringer.serialize(),
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to revurdering.toRevurderingsType(),
                    "avkorting" to objectMapper.writeValueAsString(revurdering.avkorting.toDb()),
                ),
                session,
            )

    private fun lagre(revurdering: AvsluttetRevurdering, session: TransactionalSession) {
        // TODO jah: feltet "skalFoereTilBrevutsending" er default satt til true inntil vi kommer til AttestertRevurdering (som er en ugyldig underliggende revurdering). Revurdering.kt burde ha et abstract felt: skalFøreTilUtsendingAvVedtaksbrev som vi setter i alle update-queryene
        """
        update
            revurdering
        set
            opprettet=:opprettet,
            periode=to_json(:periode::json),
            beregning = to_json(:beregning::json),
            simulering = to_json(:simulering::json),
            saksbehandler=:saksbehandler,
            oppgaveId=:oppgaveId,
            revurderingsType=:revurderingsType,
            vedtakSomRevurderesId=:vedtakSomRevurderesId,
            attestering=to_jsonb(:attestering::jsonb),
            fritekstTilBrev=:fritekstTilBrev,
            årsak=:arsak,
            begrunnelse=:begrunnelse,
            forhåndsvarsel=to_json(:forhandsvarsel::json),
            informasjonSomRevurderes=to_json(:informasjonSomRevurderes::json),
            avsluttet = to_jsonb(:avsluttet::jsonb),
            avkorting = to_jsonb(:avkorting::json)
        where
            id = :id
        """.trimIndent()
            .oppdatering(
                params = mapOf(
                    "id" to revurdering.id,
                    "opprettet" to revurdering.opprettet,
                    "periode" to objectMapper.writeValueAsString(revurdering.periode),
                    "beregning" to revurdering.beregning,
                    "simulering" to revurdering.simulering?.let { objectMapper.writeValueAsString(it) },
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "revurderingsType" to revurdering.toRevurderingsType(),
                    "vedtakSomRevurderesId" to revurdering.tilRevurdering.id,
                    "fritekstTilBrev" to revurdering.fritekstTilBrev,
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "forhandsvarsel" to revurdering.forhåndsvarsel?.let {
                        objectMapper.writeValueAsString(ForhåndsvarselDatabaseJson.from(it))
                    },
                    "informasjonSomRevurderes" to objectMapper.writeValueAsString(revurdering.informasjonSomRevurderes),
                    "attestering" to revurdering.attesteringer.serialize(),
                    "avsluttet" to objectMapper.writeValueAsString(
                        AvsluttetRevurderingInfo(
                            begrunnelse = revurdering.begrunnelse,
                            fritekst = revurdering.fritekst,
                            tidspunktAvsluttet = revurdering.tidspunktAvsluttet,
                        ),
                    ),
                    "avkorting" to objectMapper.writeValueAsString(revurdering.avkorting.toDb()),
                ),
                session = session,
            )
    }

    private fun lagRevurdering(
        status: RevurderingsType,
        id: UUID,
        periode: Periode,
        opprettet: Tidspunkt,
        tilRevurdering: VedtakSomKanRevurderes,
        saksbehandler: String,
        beregning: PersistertBeregning?,
        simulering: Simulering?,
        oppgaveId: String?,
        attesteringer: Attesteringshistorikk,
        fritekstTilBrev: String?,
        revurderingsårsak: Revurderingsårsak,
        forhåndsvarsel: Forhåndsvarsel?,
        skalFøreTilBrevutsending: Boolean,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes?,
        avkorting: AvkortingVedRevurdering?,
    ): AbstraktRevurdering {
        return when (status) {
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
                forhåndsvarsel = forhåndsvarsel as Forhåndsvarsel.Ferdigbehandlet,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
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
                forhåndsvarsel = forhåndsvarsel as Forhåndsvarsel.Ferdigbehandlet,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
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
                forhåndsvarsel = forhåndsvarsel as Forhåndsvarsel.Ferdigbehandlet,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                avkorting = avkorting as AvkortingVedRevurdering.Iverksatt,
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
                forhåndsvarsel = forhåndsvarsel as Forhåndsvarsel.Ferdigbehandlet,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                avkorting = avkorting as AvkortingVedRevurdering.Iverksatt,
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
                forhåndsvarsel = forhåndsvarsel as Forhåndsvarsel.Ferdigbehandlet,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
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
                forhåndsvarsel = forhåndsvarsel as Forhåndsvarsel.Ferdigbehandlet,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
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
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
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
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
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
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.DelvisHåndtert,
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
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.DelvisHåndtert,
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
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.Uhåndtert,
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
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.DelvisHåndtert,
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
                skalFøreTilUtsendingAvVedtaksbrev = skalFøreTilBrevutsending,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
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
                skalFøreTilUtsendingAvVedtaksbrev = skalFøreTilBrevutsending,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                avkorting = avkorting as AvkortingVedRevurdering.Iverksatt,
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
                skalFøreTilUtsendingAvVedtaksbrev = skalFøreTilBrevutsending,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
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
            RevurderingsType.SIMULERT_GJENOPPTAK -> GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
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
            RevurderingsType.IVERKSATT_GJENOPPTAK -> GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse(
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

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }
}
