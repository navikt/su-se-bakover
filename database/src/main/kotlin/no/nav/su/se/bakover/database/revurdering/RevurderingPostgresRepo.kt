package no.nav.su.se.bakover.database.revurdering

import arrow.core.getOrElse
import kotliquery.Row
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.deserializeMapNullable
import no.nav.su.se.bakover.common.deserializeNullable
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.persistence.Session
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.persistence.TransactionalSession
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.common.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.tidspunkt
import no.nav.su.se.bakover.common.persistence.tidspunktOrNull
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.serializeNullable
import no.nav.su.se.bakover.database.avkorting.AvkortingVedRevurderingDb
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.database.avkorting.toDb
import no.nav.su.se.bakover.database.avkorting.toDomain
import no.nav.su.se.bakover.database.beregning.deserialiserBeregning
import no.nav.su.se.bakover.database.brev.BrevvalgDatabaseJson.Companion.toJson
import no.nav.su.se.bakover.database.brev.BrevvalgRevurderingDbJson
import no.nav.su.se.bakover.database.brev.toDb
import no.nav.su.se.bakover.database.brev.toDomain
import no.nav.su.se.bakover.database.grunnlag.GrunnlagsdataOgVilkårsvurderingerPostgresRepo
import no.nav.su.se.bakover.database.revurdering.RevurderingsType.Companion.toRevurderingsType
import no.nav.su.se.bakover.database.tilbakekreving.TilbakekrevingPostgresRepo
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningMedFradragBeregnetMånedsvis
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingFerdigbehandlet
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.revurderes.VedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.steg.Vurderingstatus
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

/**
 * Brukes kun ved insert / update av revurderinger.
 */
private data class BaseRevurderingDb(
    val id: UUID,
    val periode: Periode,
    val opprettet: Tidspunkt,
    val oppdatert: Tidspunkt?,
    val tilRevurdering: UUID,
    val vedtakSomRevurderesMånedsvis: String,
    val saksbehandler: Saksbehandler,
    val oppgaveId: OppgaveId?,
    val revurderingsårsak: Revurderingsårsak,
    val grunnlagsdata: Grunnlagsdata,
    val vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
    val informasjonSomRevurderes: InformasjonSomRevurderes?,
    val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty(),
    val avkorting: AvkortingVedRevurdering?,
    val sakinfo: SakInfo,
    val type: String,
    val brevvalgRevurdering: BrevvalgRevurdering,
)

private data class RevurderingDb(
    val base: BaseRevurderingDb,
    val beregning: Beregning?,
    val simulering: Simulering?,
    val avsluttet: AvsluttetRevurderingDatabaseJson?,
    val tilbakekrevingsbehandling: Tilbakekrevingsbehandling?,
)

private fun StansAvYtelseRevurdering.toBaseRevurderingDb(): BaseRevurderingDb {
    return BaseRevurderingDb(
        id = this.id,
        periode = this.periode,
        opprettet = this.opprettet,
        oppdatert = this.oppdatert,
        tilRevurdering = this.tilRevurdering,
        vedtakSomRevurderesMånedsvis = this.vedtakSomRevurderesMånedsvis.toDbJson(),
        saksbehandler = this.saksbehandler,
        oppgaveId = null,
        revurderingsårsak = this.revurderingsårsak,
        grunnlagsdata = this.grunnlagsdata,
        vilkårsvurderinger = this.vilkårsvurderinger,
        informasjonSomRevurderes = null,
        attesteringer = this.attesteringer,
        avkorting = null,
        sakinfo = this.sakinfo,
        type = this.toRevurderingsType(),
        brevvalgRevurdering = this.brevvalgRevurdering,
    )
}

private fun GjenopptaYtelseRevurdering.toBaseRevurderingDb(): BaseRevurderingDb {
    return BaseRevurderingDb(
        id = this.id,
        periode = this.periode,
        opprettet = this.opprettet,
        oppdatert = this.oppdatert,
        tilRevurdering = this.tilRevurdering,
        vedtakSomRevurderesMånedsvis = this.vedtakSomRevurderesMånedsvis.toDbJson(),
        saksbehandler = this.saksbehandler,
        oppgaveId = null,
        revurderingsårsak = this.revurderingsårsak,
        grunnlagsdata = this.grunnlagsdata,
        vilkårsvurderinger = this.vilkårsvurderinger,
        informasjonSomRevurderes = null,
        attesteringer = this.attesteringer,
        avkorting = null,
        sakinfo = this.sakinfo,
        type = this.toRevurderingsType(),
        brevvalgRevurdering = this.brevvalgRevurdering,
    )
}

private fun Revurdering.toBaseRevurderingDb(): BaseRevurderingDb {
    return BaseRevurderingDb(
        id = this.id,
        periode = this.periode,
        opprettet = this.opprettet,
        oppdatert = this.oppdatert,
        tilRevurdering = this.tilRevurdering,
        vedtakSomRevurderesMånedsvis = this.vedtakSomRevurderesMånedsvis.toDbJson(),
        saksbehandler = this.saksbehandler,
        oppgaveId = this.oppgaveId,
        revurderingsårsak = this.revurderingsårsak,
        grunnlagsdata = this.grunnlagsdata,
        vilkårsvurderinger = this.vilkårsvurderinger,
        informasjonSomRevurderes = this.informasjonSomRevurderes,
        attesteringer = this.attesteringer,
        avkorting = this.avkorting,
        sakinfo = this.sakinfo,
        type = this.toRevurderingsType(),
        brevvalgRevurdering = this.brevvalgRevurdering,
    )
}

private fun Revurdering.toDb(): RevurderingDb {
    val base = this.toBaseRevurderingDb()
    return when (this) {
        is AvsluttetRevurdering -> {
            RevurderingDb(
                base = base,
                beregning = this.beregning,
                simulering = this.simulering,
                avsluttet = AvsluttetRevurderingDatabaseJson(
                    begrunnelse = this.begrunnelse,
                    brevvalg = this.brevvalg.toJson(),
                    tidspunktAvsluttet = this.tidspunktAvsluttet,
                ),
                tilbakekrevingsbehandling = null,
            )
        }

        is BeregnetRevurdering.Innvilget -> {
            RevurderingDb(
                base = base,
                beregning = this.beregning,
                simulering = null,
                avsluttet = null,
                tilbakekrevingsbehandling = null,
            )
        }

        is BeregnetRevurdering.Opphørt -> {
            RevurderingDb(
                base = base,
                beregning = this.beregning,
                simulering = null,
                avsluttet = null,
                tilbakekrevingsbehandling = null,
            )
        }

        is IverksattRevurdering.Innvilget -> {
            RevurderingDb(
                base = base,
                beregning = this.beregning,
                simulering = this.simulering,
                avsluttet = null,
                tilbakekrevingsbehandling = this.tilbakekrevingsbehandling,
            )
        }

        is IverksattRevurdering.Opphørt -> {
            RevurderingDb(
                base = base,
                beregning = this.beregning,
                simulering = this.simulering,
                avsluttet = null,
                tilbakekrevingsbehandling = this.tilbakekrevingsbehandling,
            )
        }

        is OpprettetRevurdering -> {
            RevurderingDb(
                base = base,
                beregning = null,
                simulering = null,
                avsluttet = null,
                tilbakekrevingsbehandling = null,
            )
        }

        is RevurderingTilAttestering.Innvilget -> {
            RevurderingDb(
                base = base,
                beregning = this.beregning,
                simulering = this.simulering,
                avsluttet = null,
                tilbakekrevingsbehandling = this.tilbakekrevingsbehandling,
            )
        }

        is RevurderingTilAttestering.Opphørt -> {
            RevurderingDb(
                base = base,
                beregning = this.beregning,
                simulering = this.simulering,
                avsluttet = null,
                tilbakekrevingsbehandling = this.tilbakekrevingsbehandling,
            )
        }

        is SimulertRevurdering.Innvilget -> {
            RevurderingDb(
                base = base,
                beregning = this.beregning,
                simulering = this.simulering,
                avsluttet = null,
                tilbakekrevingsbehandling = this.tilbakekrevingsbehandling,
            )
        }

        is SimulertRevurdering.Opphørt -> {
            RevurderingDb(
                base = base,
                beregning = this.beregning,
                simulering = this.simulering,
                avsluttet = null,
                tilbakekrevingsbehandling = this.tilbakekrevingsbehandling,
            )
        }

        is UnderkjentRevurdering.Innvilget -> {
            RevurderingDb(
                base = base,
                beregning = this.beregning,
                simulering = this.simulering,
                avsluttet = null,
                tilbakekrevingsbehandling = this.tilbakekrevingsbehandling,
            )
        }

        is UnderkjentRevurdering.Opphørt -> {
            RevurderingDb(
                base = base,
                beregning = this.beregning,
                simulering = this.simulering,
                avsluttet = null,
                tilbakekrevingsbehandling = this.tilbakekrevingsbehandling,
            )
        }
    }
}

enum class RevurderingsType {
    OPPRETTET,
    BEREGNET_INNVILGET,
    BEREGNET_OPPHØRT,
    SIMULERT_INNVILGET,
    SIMULERT_OPPHØRT,
    TIL_ATTESTERING_INNVILGET,
    TIL_ATTESTERING_OPPHØRT,
    IVERKSATT_INNVILGET,
    IVERKSATT_OPPHØRT,
    UNDERKJENT_INNVILGET,
    UNDERKJENT_OPPHØRT,
    SIMULERT_STANS,
    IVERKSATT_STANS,
    SIMULERT_GJENOPPTAK,
    IVERKSATT_GJENOPPTAK,
    ;

    companion object {
        internal fun Revurdering.toRevurderingsType(): String {
            return when (this) {
                is OpprettetRevurdering -> OPPRETTET
                is BeregnetRevurdering.Innvilget -> BEREGNET_INNVILGET
                is BeregnetRevurdering.Opphørt -> BEREGNET_OPPHØRT
                is SimulertRevurdering.Innvilget -> SIMULERT_INNVILGET
                is SimulertRevurdering.Opphørt -> SIMULERT_OPPHØRT
                is RevurderingTilAttestering.Innvilget -> TIL_ATTESTERING_INNVILGET
                is RevurderingTilAttestering.Opphørt -> TIL_ATTESTERING_OPPHØRT
                is IverksattRevurdering.Innvilget -> IVERKSATT_INNVILGET
                is IverksattRevurdering.Opphørt -> IVERKSATT_OPPHØRT
                is UnderkjentRevurdering.Innvilget -> UNDERKJENT_INNVILGET
                is UnderkjentRevurdering.Opphørt -> UNDERKJENT_OPPHØRT
                is AvsluttetRevurdering -> this.underliggendeRevurdering.toRevurderingsType()
            }.toString()
        }

        internal fun StansAvYtelseRevurdering.toRevurderingsType(): String {
            return when (this) {
                is StansAvYtelseRevurdering.AvsluttetStansAvYtelse -> SIMULERT_STANS
                is StansAvYtelseRevurdering.IverksattStansAvYtelse -> IVERKSATT_STANS
                is StansAvYtelseRevurdering.SimulertStansAvYtelse -> SIMULERT_STANS
            }.toString()
        }

        internal fun GjenopptaYtelseRevurdering.toRevurderingsType(): String {
            return when (this) {
                is GjenopptaYtelseRevurdering.AvsluttetGjenoppta -> SIMULERT_GJENOPPTAK
                is GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse -> IVERKSATT_GJENOPPTAK
                is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> SIMULERT_GJENOPPTAK
            }.toString()
        }

        private fun åpneRevurderingstyper() = listOf(
            OPPRETTET,
            BEREGNET_INNVILGET,
            BEREGNET_OPPHØRT,
            SIMULERT_INNVILGET,
            SIMULERT_OPPHØRT,
            TIL_ATTESTERING_INNVILGET,
            TIL_ATTESTERING_OPPHØRT,
            UNDERKJENT_INNVILGET,
            UNDERKJENT_OPPHØRT,
            SIMULERT_STANS,
            SIMULERT_GJENOPPTAK,
        )

        fun åpneRevurderingstyperKommaseparert(): String = åpneRevurderingstyper().joinToString(",") { "'$it'" }
    }
}

internal class RevurderingPostgresRepo(
    private val grunnlagsdataOgVilkårsvurderingerPostgresRepo: GrunnlagsdataOgVilkårsvurderingerPostgresRepo,
    private val dbMetrics: DbMetrics,
    private val sessionFactory: PostgresSessionFactory,
    private val avkortingsvarselRepo: AvkortingsvarselPostgresRepo,
    private val tilbakekrevingRepo: TilbakekrevingPostgresRepo,
    private val satsFactory: SatsFactoryForSupplerendeStønad,
) : RevurderingRepo {

    override fun hent(id: UUID): AbstraktRevurdering? {
        return dbMetrics.timeQuery("hentRevurdering") {
            sessionFactory.withSession { session ->
                hent(id, session)
            }
        }
    }

    override fun hent(id: UUID, sessionContext: SessionContext): AbstraktRevurdering? {
        return dbMetrics.timeQuery("hentRevurdering") {
            sessionContext.withSession { session ->
                hent(id, session)
            }
        }
    }

    internal fun hent(id: UUID, session: Session): AbstraktRevurdering? {
        return """
                    SELECT
                        r.*,
                        s.saksnummer,
                        s.fnr,
                        s.type
                    FROM revurdering r
                        JOIN sak s ON s.id = r.sakid
                    WHERE r.id = :id
        """.trimIndent()
            .hent(mapOf("id" to id), session) { row ->
                row.toRevurdering(
                    session = session,
                )
            }
    }

    override fun lagre(revurdering: AbstraktRevurdering, transactionContext: TransactionContext) {
        dbMetrics.timeQuery("lagreRevurdering") {
            transactionContext.withTransaction { tx ->
                lagre(
                    abstraktRevurdering = revurdering,
                    tx = tx,
                )
                grunnlagsdataOgVilkårsvurderingerPostgresRepo.lagre(
                    behandlingId = revurdering.id,
                    grunnlagsdataOgVilkårsvurderinger = revurdering.grunnlagsdataOgVilkårsvurderinger,
                    tx = tx,
                )
            }
        }
    }

    private fun lagre(abstraktRevurdering: AbstraktRevurdering, tx: TransactionalSession) {
        when (abstraktRevurdering) {
            is GjenopptaYtelseRevurdering -> {
                lagre(
                    revurdering = abstraktRevurdering.toDb(),
                    session = tx,
                )
            }

            is Revurdering -> {
                lagre(
                    revurdering = abstraktRevurdering.toDb(),
                    session = tx,
                )
            }

            is StansAvYtelseRevurdering -> {
                lagre(
                    revurdering = abstraktRevurdering.toDb(),
                    session = tx,
                )
            }
        }
    }

    private fun GjenopptaYtelseRevurdering.toDb(): RevurderingDb {
        val base = this.toBaseRevurderingDb()
        return when (this) {
            is GjenopptaYtelseRevurdering.AvsluttetGjenoppta -> {
                RevurderingDb(
                    base = base,
                    beregning = null,
                    simulering = this.simulering,
                    avsluttet = AvsluttetRevurderingDatabaseJson(
                        begrunnelse = this.begrunnelse,
                        brevvalg = null,
                        tidspunktAvsluttet = this.tidspunktAvsluttet,
                    ),
                    tilbakekrevingsbehandling = null,
                )
            }

            is GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse -> {
                RevurderingDb(
                    base = base,
                    beregning = null,
                    simulering = this.simulering,
                    avsluttet = null,
                    tilbakekrevingsbehandling = null,
                )
            }

            is GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse -> {
                RevurderingDb(
                    base = base,
                    beregning = null,
                    simulering = this.simulering,
                    avsluttet = null,
                    tilbakekrevingsbehandling = null,
                )
            }
        }
    }

    private fun StansAvYtelseRevurdering.toDb(): RevurderingDb {
        val base = this.toBaseRevurderingDb()
        return when (this) {
            is StansAvYtelseRevurdering.AvsluttetStansAvYtelse -> {
                RevurderingDb(
                    base = base,
                    beregning = null,
                    simulering = this.simulering,
                    avsluttet = AvsluttetRevurderingDatabaseJson(
                        begrunnelse = this.begrunnelse,
                        brevvalg = null,
                        tidspunktAvsluttet = this.tidspunktAvsluttet,
                    ),
                    tilbakekrevingsbehandling = null,
                )
            }

            is StansAvYtelseRevurdering.IverksattStansAvYtelse -> {
                RevurderingDb(
                    base = base,
                    beregning = null,
                    simulering = this.simulering,
                    avsluttet = null,
                    tilbakekrevingsbehandling = null,
                )
            }

            is StansAvYtelseRevurdering.SimulertStansAvYtelse -> {
                RevurderingDb(
                    base = base,
                    beregning = null,
                    simulering = this.simulering,
                    avsluttet = null,
                    tilbakekrevingsbehandling = null,
                )
            }
        }
    }

    internal fun hentRevurderingerForSak(sakId: UUID, session: Session): List<AbstraktRevurdering> =
        """
            SELECT
                r.*,
                s.saksnummer,
                s.fnr,
                s.type
            FROM revurdering r
                JOIN sak s on s.id = r.sakid
            WHERE r.sakid=:sakId
        """.trimIndent()
            .hentListe(mapOf("sakId" to sakId), session) {
                it.toRevurdering(session)
            }

    private fun Row.toRevurdering(session: Session): AbstraktRevurdering {
        val id = uuid("id")
        val status = RevurderingsType.valueOf(string("revurderingsType"))
        val periode = deserialize<Periode>(string("periode"))
        val opprettet = tidspunkt("opprettet")
        val oppdatert = tidspunktOrNull("oppdatert")
        val tilRevurdering = uuid("vedtakSomRevurderesId")
        val sakinfo = SakInfo(
            sakId = uuid("sakid"),
            saksnummer = Saksnummer(long("saksnummer")),
            fnr = Fnr(string("fnr")),
            type = Sakstype.from(string("type")),
        )
        val beregning: BeregningMedFradragBeregnetMånedsvis? = stringOrNull("beregning")?.deserialiserBeregning(
            satsFactory = satsFactory.gjeldende(opprettet),
            sakstype = sakinfo.type,
        )
        val simulering = deserializeNullable<Simulering>(stringOrNull("simulering"))
        val saksbehandler = string("saksbehandler")
        val oppgaveId = stringOrNull("oppgaveid")
        val attesteringer = Attesteringshistorikk.create(deserializeList(string("attestering")))
        val årsak = string("årsak")
        val begrunnelse = string("begrunnelse")
        val revurderingsårsak = Revurderingsårsak.create(
            årsak = årsak,
            begrunnelse = begrunnelse,
        )

        val informasjonSomRevurderes =
            deserializeMapNullable<Revurderingsteg, Vurderingstatus>(stringOrNull("informasjonSomRevurderes"))?.let {
                InformasjonSomRevurderes.create(it)
            }

        val (grunnlagsdata, vilkårsvurderinger) = grunnlagsdataOgVilkårsvurderingerPostgresRepo.hentForRevurdering(
            behandlingId = id,
            session = session,
            sakstype = sakinfo.type,
        )

        val avkorting = deserializeNullable<AvkortingVedRevurderingDb>(stringOrNull("avkorting"))?.toDomain()

        val tilbakekrevingsbehandling = tilbakekrevingRepo.hentTilbakekrevingsbehandling(
            revurderingId = id,
            session = session,
        )

        val brevvalg = deserialize<BrevvalgRevurderingDbJson>(string("brevvalg")).toDomain()

        val vedtakSomRevurderesMånedsvis = VedtakSomRevurderesMånedsvisDbJson.toDomain(string("vedtakSomRevurderesMånedsvis"))

        val revurdering = lagRevurdering(
            status = status,
            id = id,
            periode = periode,
            opprettet = opprettet,
            oppdatert = oppdatert ?: opprettet,
            tilRevurdering = tilRevurdering,
            vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = simulering,
            oppgaveId = oppgaveId,
            attesteringer = attesteringer,
            revurderingsårsak = revurderingsårsak,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            avkorting = avkorting,
            tilbakekrevingsbehandling = tilbakekrevingsbehandling,
            sakinfo = sakinfo,
            brevvalgRevurdering = brevvalg,
        )

        val avsluttet = deserializeNullable<AvsluttetRevurderingDatabaseJson>(stringOrNull("avsluttet"))

        if (avsluttet != null) {
            return when (revurdering) {
                is GjenopptaYtelseRevurdering -> GjenopptaYtelseRevurdering.AvsluttetGjenoppta.tryCreate(
                    gjenopptakAvYtelseRevurdering = revurdering,
                    begrunnelse = avsluttet.begrunnelse,
                    tidspunktAvsluttet = avsluttet.tidspunktAvsluttet,
                ).getOrElse {
                    throw IllegalStateException("Kunne ikke lage en avsluttet gjenoppta revurdering. Se innhold i databasen. revurderingsId $id")
                }

                is Revurdering -> {
                    return AvsluttetRevurdering.tryCreate(
                        underliggendeRevurdering = revurdering,
                        begrunnelse = avsluttet.begrunnelse,
                        brevvalg = avsluttet.brevvalg?.toDomain(),
                        tidspunktAvsluttet = avsluttet.tidspunktAvsluttet,
                    ).getOrElse {
                        throw IllegalStateException("Kunne ikke lage en avsluttet revurdering. Se innhold i databasen. revurderingsId $id")
                    }
                }

                is StansAvYtelseRevurdering -> StansAvYtelseRevurdering.AvsluttetStansAvYtelse.tryCreate(
                    stansAvYtelseRevurdering = revurdering,
                    begrunnelse = avsluttet.begrunnelse,
                    tidspunktAvsluttet = avsluttet.tidspunktAvsluttet,
                ).getOrElse {
                    throw IllegalStateException("Kunne ikke lage en avsluttet stans av ytelse. Se innhold i databasen. revurderingsId $id")
                }
            }
        }
        return revurdering
    }

    private fun lagre(revurdering: RevurderingDb, session: TransactionalSession) {
        """
                    insert into revurdering (
                        id,
                        opprettet,
                        oppdatert,
                        periode,
                        beregning,
                        simulering,
                        saksbehandler,
                        oppgaveId,
                        revurderingsType,
                        vedtakSomRevurderesId,
                        vedtakSomRevurderesMånedsvis,
                        attestering,
                        årsak,
                        begrunnelse,
                        informasjonSomRevurderes,
                        avsluttet,
                        avkorting,
                        sakid,
                        brevvalg
                    ) values (
                        :id,
                        :opprettet,
                        :oppdatert,
                        to_json(:periode::json),
                        to_json(:beregning::json),
                        to_json(:simulering::json),
                        :saksbehandler,
                        :oppgaveId,
                        :revurderingsType,
                        :vedtakSomRevurderesId,
                        to_jsonb(:vedtakSomRevurderesManedsvis::jsonb),
                        to_jsonb(:attestering::jsonb),
                        :arsak,
                        :begrunnelse,
                        to_json(:informasjonSomRevurderes::json),
                        to_jsonb(:avsluttet::jsonb),
                        to_json(:avkorting::json),
                        :sakid,
                        to_json(:brevvalg::json)
                    )
                        ON CONFLICT(id) do update set
                        oppdatert = :oppdatert,
                        periode = to_json(:periode::json),
                        beregning = to_json(:beregning::json),
                        simulering = to_json(:simulering::json),
                        saksbehandler = :saksbehandler,
                        oppgaveId = :oppgaveId,
                        revurderingsType = :revurderingsType,
                        vedtakSomRevurderesId = :vedtakSomRevurderesId,
                        vedtakSomRevurderesMånedsvis = to_jsonb(:vedtakSomRevurderesManedsvis::jsonb),
                        attestering = to_jsonb(:attestering::jsonb),
                        årsak = :arsak,
                        begrunnelse = :begrunnelse,
                        informasjonSomRevurderes = to_json(:informasjonSomRevurderes::json),
                        avsluttet = to_jsonb(:avsluttet::jsonb),
                        avkorting = to_json(:avkorting::json),
                        brevvalg = to_json(:brevvalg::json)
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to revurdering.base.id,
                    "opprettet" to revurdering.base.opprettet,
                    "oppdatert" to revurdering.base.oppdatert,
                    "periode" to serialize(revurdering.base.periode),
                    "beregning" to revurdering.beregning,
                    "simulering" to serializeNullable(revurdering.simulering),
                    "saksbehandler" to revurdering.base.saksbehandler.navIdent,
                    "oppgaveId" to revurdering.base.oppgaveId.toString(),
                    "revurderingsType" to revurdering.base.type,
                    "vedtakSomRevurderesId" to revurdering.base.tilRevurdering,
                    "vedtakSomRevurderesManedsvis" to revurdering.base.vedtakSomRevurderesMånedsvis,
                    "attestering" to revurdering.base.attesteringer.serialize(),
                    "arsak" to revurdering.base.revurderingsårsak.årsak.toString(),
                    "begrunnelse" to revurdering.base.revurderingsårsak.begrunnelse.toString(),
                    "informasjonSomRevurderes" to serializeNullable(revurdering.base.informasjonSomRevurderes),
                    "avsluttet" to serializeNullable(revurdering.avsluttet),
                    "avkorting" to serializeNullable(revurdering.base.avkorting?.toDb()),
                    "sakid" to revurdering.base.sakinfo.sakId,
                    "brevvalg" to serialize(revurdering.base.brevvalgRevurdering.toDb()),
                ),
                session,
            )

        when (val avkorting = revurdering.base.avkorting) {
            is AvkortingVedRevurdering.Iverksatt -> {
                when (avkorting) {
                    is AvkortingVedRevurdering.Iverksatt.AnnullerUtestående -> {
                        avkortingsvarselRepo.lagre(
                            avkortingsvarsel = avkorting.annullerUtestående,
                            tx = session,
                        )
                    }

                    is AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående -> {
                        // noop
                    }

                    is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel -> {
                        avkortingsvarselRepo.lagre(
                            avkortingsvarsel = avkorting.avkortingsvarsel,
                            tx = session,
                        )
                    }

                    is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
                        avkortingsvarselRepo.lagre(
                            avkortingsvarsel = avkorting.avkortingsvarsel,
                            tx = session,
                        )
                        avkortingsvarselRepo.lagre(
                            avkortingsvarsel = avkorting.annullerUtestående,
                            tx = session,
                        )
                    }

                    is AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres -> {
                        // noop
                    }
                }
            }

            else -> {
                // noop
            }
        }

        when (val tilbakekrevingsbehandling = revurdering.tilbakekrevingsbehandling) {
            is Tilbakekrevingsbehandling.UnderBehandling -> {
                when (tilbakekrevingsbehandling) {
                    is Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving -> {
                        tilbakekrevingRepo.slettForRevurderingId(
                            revurderingId = revurdering.base.id,
                            session = session,
                        )
                    }

                    is Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort -> {
                        tilbakekrevingRepo.lagreTilbakekrevingsbehandling(
                            tilbakrekrevingsbehanding = tilbakekrevingsbehandling,
                            tx = session,
                        )
                    }

                    is Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.IkkeAvgjort -> {
                        tilbakekrevingRepo.lagreTilbakekrevingsbehandling(
                            tilbakrekrevingsbehanding = tilbakekrevingsbehandling,
                            tx = session,
                        )
                    }
                }
            }

            is Tilbakekrevingsbehandling.Ferdigbehandlet -> {
                when (tilbakekrevingsbehandling) {
                    is Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag -> {
                        throw IllegalStateException("Kan aldri ha mottatt kravgrunnlag før vi har iverksatt")
                    }

                    is Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.SendtTilbakekrevingsvedtak -> {
                        throw IllegalStateException("Kan aldri ha besvart kravgrunnlag før vi har iverksatt")
                    }

                    is Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag -> {
                        tilbakekrevingRepo.lagreTilbakekrevingsbehandling(
                            tilbakrekrevingsbehanding = tilbakekrevingsbehandling,
                            session = session,
                        )
                    }

                    is Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving -> {
                        // noop
                    }
                }
            }

            else -> {
                // noop
            }
        }
    }

    private fun lagRevurdering(
        status: RevurderingsType,
        id: UUID,
        periode: Periode,
        opprettet: Tidspunkt,
        oppdatert: Tidspunkt,
        tilRevurdering: UUID,
        vedtakSomRevurderesMånedsvis: VedtakSomRevurderesMånedsvis,
        saksbehandler: String,
        beregning: BeregningMedFradragBeregnetMånedsvis?,
        simulering: Simulering?,
        oppgaveId: String?,
        attesteringer: Attesteringshistorikk,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger.Revurdering,
        informasjonSomRevurderes: InformasjonSomRevurderes?,
        avkorting: AvkortingVedRevurdering?,
        tilbakekrevingsbehandling: Tilbakekrevingsbehandling?,
        sakinfo: SakInfo,
        brevvalgRevurdering: BrevvalgRevurdering,
    ): AbstraktRevurdering {
        return when (status) {
            RevurderingsType.UNDERKJENT_INNVILGET -> UnderkjentRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                simulering = simulering!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                attesteringer = attesteringer,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.UnderBehandling
                    ?: IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering as BrevvalgRevurdering.Valgt,
            )

            RevurderingsType.UNDERKJENT_OPPHØRT -> UnderkjentRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                simulering = simulering!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                attesteringer = attesteringer,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.UnderBehandling
                    ?: IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering as BrevvalgRevurdering.Valgt,
            )

            RevurderingsType.IVERKSATT_INNVILGET -> IverksattRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                beregning = beregning!!,
                simulering = simulering!!,
                attesteringer = attesteringer,
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                avkorting = avkorting as AvkortingVedRevurdering.Iverksatt,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.Ferdigbehandlet
                    ?: IkkeBehovForTilbakekrevingFerdigbehandlet,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering as BrevvalgRevurdering.Valgt,
            )

            RevurderingsType.IVERKSATT_OPPHØRT -> IverksattRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                simulering = simulering!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                attesteringer = attesteringer,
                grunnlagsdata = grunnlagsdata,
                revurderingsårsak = revurderingsårsak,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                avkorting = avkorting as AvkortingVedRevurdering.Iverksatt,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.Ferdigbehandlet
                    ?: IkkeBehovForTilbakekrevingFerdigbehandlet,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering as BrevvalgRevurdering.Valgt,
            )

            RevurderingsType.TIL_ATTESTERING_INNVILGET -> RevurderingTilAttestering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                grunnlagsdata = grunnlagsdata,
                revurderingsårsak = revurderingsårsak,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.UnderBehandling
                    ?: IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering as BrevvalgRevurdering.Valgt,
            )

            RevurderingsType.TIL_ATTESTERING_OPPHØRT -> RevurderingTilAttestering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.UnderBehandling
                    ?: IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering as BrevvalgRevurdering.Valgt,
            )

            RevurderingsType.SIMULERT_INNVILGET -> SimulertRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.UnderBehandling
                    ?: IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            )

            RevurderingsType.SIMULERT_OPPHØRT -> SimulertRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                grunnlagsdata = grunnlagsdata,
                revurderingsårsak = revurderingsårsak,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.Håndtert,
                tilbakekrevingsbehandling = tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.UnderBehandling
                    ?: IkkeBehovForTilbakekrevingUnderBehandling,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            )

            RevurderingsType.BEREGNET_INNVILGET -> BeregnetRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.DelvisHåndtert,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            )

            RevurderingsType.BEREGNET_OPPHØRT -> BeregnetRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                grunnlagsdata = grunnlagsdata,
                revurderingsårsak = revurderingsårsak,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.DelvisHåndtert,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering,
            )

            RevurderingsType.OPPRETTET -> OpprettetRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!),
                revurderingsårsak = revurderingsårsak,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes!!,
                attesteringer = attesteringer,
                avkorting = avkorting as AvkortingVedRevurdering.Uhåndtert,
                sakinfo = sakinfo,
            )

            RevurderingsType.SIMULERT_STANS -> StansAvYtelseRevurdering.SimulertStansAvYtelse(
                id = id,
                opprettet = opprettet,
                oppdatert = oppdatert,
                periode = periode,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = Saksbehandler(saksbehandler),
                simulering = simulering!!,
                revurderingsårsak = revurderingsårsak,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering as BrevvalgRevurdering.Valgt.IkkeSendBrev,
            )

            RevurderingsType.IVERKSATT_STANS -> StansAvYtelseRevurdering.IverksattStansAvYtelse(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = Saksbehandler(saksbehandler),
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                simulering = simulering!!,
                attesteringer = attesteringer,
                revurderingsårsak = revurderingsårsak,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering as BrevvalgRevurdering.Valgt.IkkeSendBrev,
            )

            RevurderingsType.SIMULERT_GJENOPPTAK -> GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse(
                id = id,
                opprettet = opprettet,
                oppdatert = oppdatert,
                periode = periode,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = Saksbehandler(saksbehandler),
                simulering = simulering!!,
                revurderingsårsak = revurderingsårsak,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering as BrevvalgRevurdering.Valgt.IkkeSendBrev,
            )

            RevurderingsType.IVERKSATT_GJENOPPTAK -> GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse(
                id = id,
                periode = periode,
                opprettet = opprettet,
                oppdatert = oppdatert,
                tilRevurdering = tilRevurdering,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                saksbehandler = Saksbehandler(saksbehandler),
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                simulering = simulering!!,
                attesteringer = attesteringer,
                revurderingsårsak = revurderingsårsak,
                sakinfo = sakinfo,
                brevvalgRevurdering = brevvalgRevurdering as BrevvalgRevurdering.Valgt.IkkeSendBrev,
            )
        }
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }
}
