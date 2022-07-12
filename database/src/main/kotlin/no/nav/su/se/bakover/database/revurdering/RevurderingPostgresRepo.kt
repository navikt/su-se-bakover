package no.nav.su.se.bakover.database.revurdering

import arrow.core.getOrHandle
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.deserializeMapNullable
import no.nav.su.se.bakover.common.deserializeNullable
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.serializeNullable
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.avkorting.AvkortingVedRevurderingDb
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.avkorting.toDb
import no.nav.su.se.bakover.database.avkorting.toDomain
import no.nav.su.se.bakover.database.beregning.deserialiserBeregning
import no.nav.su.se.bakover.database.grunnlag.GrunnlagsdataOgVilkårsvurderingerPostgresRepo
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.regulering.ReguleringPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingsType.Companion.toRevurderingsType
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.tilbakekreving.TilbakekrevingPostgresRepo
import no.nav.su.se.bakover.database.vedtak.VedtakPostgresRepo
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.BeregningMedFradragBeregnetMånedsvis
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingFerdigbehandlet
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.MottattKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.SendtTilbakekrevingsvedtak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
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
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

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

        fun åpneRevurderingstyper() = listOf(
            OPPRETTET,
            BEREGNET_INNVILGET,
            BEREGNET_OPPHØRT,
            BEREGNET_INGEN_ENDRING,
            SIMULERT_INNVILGET,
            SIMULERT_OPPHØRT,
            TIL_ATTESTERING_INNVILGET,
            TIL_ATTESTERING_OPPHØRT,
            TIL_ATTESTERING_INGEN_ENDRING,
            UNDERKJENT_INNVILGET,
            UNDERKJENT_OPPHØRT,
            UNDERKJENT_INGEN_ENDRING,
            SIMULERT_STANS,
            SIMULERT_GJENOPPTAK,
        )

        fun åpneRevurderingstyperKommaseparert(): String = åpneRevurderingstyper().joinToString(",") { "'$it'" }
    }
}

internal class RevurderingPostgresRepo(
    private val grunnlagsdataOgVilkårsvurderingerPostgresRepo: GrunnlagsdataOgVilkårsvurderingerPostgresRepo,
    søknadsbehandlingRepo: SøknadsbehandlingPostgresRepo,
    klageRepo: KlagePostgresRepo,
    private val dbMetrics: DbMetrics,
    private val sessionFactory: PostgresSessionFactory,
    private val avkortingsvarselRepo: AvkortingsvarselPostgresRepo,
    private val tilbakekrevingRepo: TilbakekrevingPostgresRepo,
    reguleringPostgresRepo: ReguleringPostgresRepo,
    private val satsFactory: SatsFactoryForSupplerendeStønad,
) : RevurderingRepo {
    private val vedtakRepo = VedtakPostgresRepo(
        sessionFactory = sessionFactory,
        dbMetrics = dbMetrics,
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        revurderingRepo = this,
        klageRepo = klageRepo,
        reguleringRepo = reguleringPostgresRepo,
        satsFactory = satsFactory,
    )

    private val stansAvYtelseRepo = StansAvYtelsePostgresRepo(
        dbMetrics = dbMetrics,
        grunnlagsdataOgVilkårsvurderingerPostgresRepo = grunnlagsdataOgVilkårsvurderingerPostgresRepo,
    )

    private val gjenopptakAvYtelseRepo = GjenopptakAvYtelsePostgresRepo(
        grunnlagsdataOgVilkårsvurderingerPostgresRepo = grunnlagsdataOgVilkårsvurderingerPostgresRepo,
        dbMetrics = dbMetrics,
    )

    override fun hent(id: UUID): AbstraktRevurdering? {
        return dbMetrics.timeQuery("hentRevurdering") {
            sessionFactory.withSession { session ->
                hent(id, session)
            }
        }
    }

    internal fun hent(id: UUID, session: Session): AbstraktRevurdering? {
        return """
                    SELECT 
                        r.*,
                        s.type
                    FROM revurdering r
                        INNER JOIN behandling_vedtak bv ON r.vedtakSomRevurderesId = bv.vedtakId
                        JOIN sak s ON s.id = bv.sakid 
                    WHERE r.id = :id
        """.trimIndent()
            .hent(mapOf("id" to id), session) { row ->
                row.toRevurdering(
                    session = session,
                    sakstype = Sakstype.from(row.string("type")),
                )
            }
    }

    override fun lagre(revurdering: AbstraktRevurdering, transactionContext: TransactionContext) {
        dbMetrics.timeQuery("lagreRevurdering") {
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
        }.also {
            grunnlagsdataOgVilkårsvurderingerPostgresRepo.lagre(
                behandlingId = revurdering.id,
                grunnlagsdataOgVilkårsvurderinger = revurdering.grunnlagsdataOgVilkårsvurderinger,
                tx = session,
            )
        }
    }

    internal fun hentRevurderingerForSak(sakId: UUID, session: Session, sakstype: Sakstype): List<AbstraktRevurdering> =
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
                it.toRevurdering(session, sakstype)
            }

    private fun Row.toRevurdering(session: Session, sakstype: Sakstype): AbstraktRevurdering {
        val id = uuid("id")
        val status = RevurderingsType.valueOf(string("revurderingsType"))
        val periode = deserialize<Periode>(string("periode"))
        val opprettet = tidspunkt("opprettet")
        val tilRevurdering = vedtakRepo.hent(uuid("vedtakSomRevurderesId"), session)!! as VedtakSomKanRevurderes
        val beregning: BeregningMedFradragBeregnetMånedsvis? = stringOrNull("beregning")?.deserialiserBeregning(
            satsFactory = satsFactory.gjeldende(opprettet),
            sakstype = sakstype,
        )
        val simulering = deserializeNullable<Simulering>(stringOrNull("simulering"))
        val saksbehandler = string("saksbehandler")
        val oppgaveId = stringOrNull("oppgaveid")
        val attesteringer = Attesteringshistorikk.create(deserializeList(string("attestering")))
        val fritekstTilBrev = stringOrNull("fritekstTilBrev")
        val årsak = string("årsak")
        val begrunnelse = string("begrunnelse")
        val revurderingsårsak = Revurderingsårsak.create(
            årsak = årsak,
            begrunnelse = begrunnelse,
        )
        val skalFøreTilBrevutsending = boolean("skalFøreTilBrevutsending")
        val forhåndsvarsel = deserializeNullable<ForhåndsvarselDatabaseJson>(stringOrNull("forhåndsvarsel"))?.toDomain()

        val informasjonSomRevurderes = deserializeMapNullable<Revurderingsteg, Vurderingstatus>(stringOrNull("informasjonSomRevurderes"))?.let {
            InformasjonSomRevurderes.create(it)
        }

        val (grunnlagsdata, vilkårsvurderinger) = grunnlagsdataOgVilkårsvurderingerPostgresRepo.hentForRevurdering(
            behandlingId = id,
            session = session,
            sakstype = sakstype,
        )

        val avkorting = deserializeNullable<AvkortingVedRevurderingDb>(stringOrNull("avkorting"))?.toDomain()

        val tilbakekrevingsbehandling = tilbakekrevingRepo.hentTilbakekrevingsbehandling(
            revurderingId = id,
            session = session,
        )

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
            tilbakekrevingsbehandling = tilbakekrevingsbehandling,
        )

        val avsluttet = deserializeNullable<AvsluttetRevurderingInfo>(stringOrNull("avsluttet"))

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
                    "periode" to serialize(revurdering.periode),
                    "opprettet" to revurdering.opprettet,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "vedtakSomRevurderesId" to revurdering.tilRevurdering.id,
                    "fritekstTilBrev" to revurdering.fritekstTilBrev,
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "forhandsvarsel" to serializeNullable(
                        revurdering.forhåndsvarsel?.let {
                            ForhåndsvarselDatabaseJson.from(
                                it,
                            )
                        },
                    ),
                    "informasjonSomRevurderes" to serialize(revurdering.informasjonSomRevurderes),
                    "attestering" to revurdering.attesteringer.serialize(),
                    "avkorting" to serialize(revurdering.avkorting.toDb()),
                ),
                session,
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
                    "informasjonSomRevurderes" to serialize(revurdering.informasjonSomRevurderes),
                    "avkorting" to serialize(revurdering.avkorting.toDb()),
                ),
                tx,
            )

    private fun lagre(revurdering: SimulertRevurdering, tx: TransactionalSession) {
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
                    "simulering" to serialize(revurdering.simulering),
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to revurdering.toRevurderingsType(),
                    "forhandsvarsel" to serializeNullable(
                        revurdering.forhåndsvarsel?.let {
                            ForhåndsvarselDatabaseJson.from(
                                it,
                            )
                        },
                    ),
                    "avkorting" to serialize(revurdering.avkorting.toDb()),
                ),
                tx,
            )

        when (val t = revurdering.tilbakekrevingsbehandling) {
            is Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving -> {
                tilbakekrevingRepo.slettForRevurderingId(
                    revurderingId = revurdering.id,
                    session = tx,
                )
            }
            is Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort -> {
                tilbakekrevingRepo.lagreTilbakekrevingsbehandling(
                    tilbakrekrevingsbehanding = t,
                    tx = tx,
                )
            }
            is Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.IkkeAvgjort -> {
                tilbakekrevingRepo.lagreTilbakekrevingsbehandling(
                    tilbakrekrevingsbehanding = t,
                    tx = tx,
                )
            }
        }
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
                        is RevurderingTilAttestering.Innvilget -> serialize(revurdering.simulering)
                        is RevurderingTilAttestering.Opphørt -> serialize(revurdering.simulering)
                    },
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "fritekstTilBrev" to revurdering.fritekstTilBrev,
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to revurdering.toRevurderingsType(),
                    "skalFoereTilBrevutsending" to revurdering.skalFøreTilUtsendingAvVedtaksbrev,
                    "forhandsvarsel" to serializeNullable(
                        revurdering.forhåndsvarsel?.let {
                            ForhåndsvarselDatabaseJson.from(
                                it,
                            )
                        },
                    ),
                    "avkorting" to serialize(revurdering.avkorting.toDb()),
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
                        is IverksattRevurdering.Innvilget -> serialize(revurdering.simulering)
                        is IverksattRevurdering.Opphørt -> serialize(revurdering.simulering)
                    },
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "attestering" to revurdering.attesteringer.serialize(),
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "revurderingsType" to revurdering.toRevurderingsType(),
                    "avkorting" to serialize(revurdering.avkorting.toDb()),
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

        when (revurdering) {
            is IverksattRevurdering.IngenEndring -> {
                // noop
            }
            is IverksattRevurdering.Innvilget -> {
                oppdaterTilbakekrevingsbehandlingVedIverksettelse(
                    tilbakekrevingsbehandling = revurdering.tilbakekrevingsbehandling,
                    tx = tx,
                )
            }
            is IverksattRevurdering.Opphørt -> {
                oppdaterTilbakekrevingsbehandlingVedIverksettelse(
                    tilbakekrevingsbehandling = revurdering.tilbakekrevingsbehandling,
                    tx = tx,
                )
            }
        }
    }

    private fun oppdaterTilbakekrevingsbehandlingVedIverksettelse(
        tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet,
        tx: Session,
    ) {
        when (tilbakekrevingsbehandling) {
            is AvventerKravgrunnlag -> {
                tilbakekrevingRepo.lagreTilbakekrevingsbehandling(tilbakekrevingsbehandling, tx)
            }
            is Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving -> {
                // noop
            }
            is MottattKravgrunnlag -> {
                throw IllegalStateException("Kan aldri ha mottatt kravgrunnlag før vi har iverksatt")
            }
            is SendtTilbakekrevingsvedtak -> {
                throw IllegalStateException("Kan aldri ha besvart kravgrunnlag før vi har iverksatt")
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
                    "avkorting" to serialize(revurdering.avkorting.toDb()),
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
                    "periode" to serialize(revurdering.periode),
                    "beregning" to revurdering.beregning,
                    "simulering" to serializeNullable(revurdering.simulering),
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "revurderingsType" to revurdering.toRevurderingsType(),
                    "vedtakSomRevurderesId" to revurdering.tilRevurdering.id,
                    "fritekstTilBrev" to revurdering.fritekstTilBrev,
                    "arsak" to revurdering.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.revurderingsårsak.begrunnelse.toString(),
                    "forhandsvarsel" to serializeNullable(
                        revurdering.forhåndsvarsel?.let {
                            ForhåndsvarselDatabaseJson.from(
                                it,
                            )
                        },
                    ),
                    "informasjonSomRevurderes" to serialize(revurdering.informasjonSomRevurderes),
                    "attestering" to revurdering.attesteringer.serialize(),
                    "avsluttet" to serialize(
                        AvsluttetRevurderingInfo(
                            begrunnelse = revurdering.begrunnelse,
                            fritekst = revurdering.fritekst,
                            tidspunktAvsluttet = revurdering.tidspunktAvsluttet,
                        ),
                    ),
                    "avkorting" to serialize(revurdering.avkorting.toDb()),
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
        beregning: BeregningMedFradragBeregnetMånedsvis?,
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
        tilbakekrevingsbehandling: Tilbakekrevingsbehandling?,
    ): AbstraktRevurdering {
        val sakinfo = tilRevurdering.sakinfo()

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
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.UnderBehandling
                    ?: IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = sakinfo,
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
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.UnderBehandling
                    ?: IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = sakinfo,
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
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.Ferdigbehandlet
                    ?: IkkeBehovForTilbakekrevingFerdigbehandlet,
                sakinfo = sakinfo,
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
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.Ferdigbehandlet
                    ?: IkkeBehovForTilbakekrevingFerdigbehandlet,
                sakinfo = sakinfo,
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
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.UnderBehandling
                    ?: IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = sakinfo,
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
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.UnderBehandling
                    ?: IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = sakinfo,
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
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.UnderBehandling
                    ?: IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = sakinfo,
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
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.UnderBehandling
                    ?: IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = sakinfo,
            )
            RevurderingsType.BEREGNET_INNVILGET -> BeregnetRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.DelvisHåndtert,
                sakinfo = sakinfo,
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
                sakinfo = sakinfo,
            )
            RevurderingsType.OPPRETTET -> OpprettetRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.Uhåndtert,
                sakinfo = sakinfo,
            )
            RevurderingsType.BEREGNET_INGEN_ENDRING -> BeregnetRevurdering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                fritekstTilBrev = fritekstTilBrev ?: "",
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.DelvisHåndtert,
                sakinfo = sakinfo,
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
                sakinfo = sakinfo,
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
                sakinfo = sakinfo,
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
                sakinfo = sakinfo,
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
                sakinfo = sakinfo,
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
                sakinfo = sakinfo,
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
                sakinfo = sakinfo,
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
                sakinfo = sakinfo,
            )
        }
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }
}
