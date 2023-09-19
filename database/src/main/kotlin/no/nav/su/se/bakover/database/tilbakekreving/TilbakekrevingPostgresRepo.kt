package no.nav.su.se.bakover.database.tilbakekreving

import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunktOrNull
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeTilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.MottattKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.SendtTilbakekrevingsvedtak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingRepo
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import tilbakekreving.domain.kravgrunnlag.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import java.util.UUID

internal class TilbakekrevingPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
) : TilbakekrevingRepo {

    override fun lagre(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag) {
        sessionFactory.withSession { session ->
            lagreTilbakekrevingsbehandling(tilbakekrevingsbehandling, session)
        }
    }

    override fun lagre(
        tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.SendtTilbakekrevingsvedtak,
        transactionContext: TransactionContext,
    ) {
        transactionContext.withTransaction { session ->
            lagreTilbakekrevingsbehandling(tilbakekrevingsbehandling, session)
        }
    }

    override fun hentMottattKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag> {
        return sessionFactory.withSession { session ->
            "select * from revurdering_tilbakekreving where tilstand = '${Tilstand.MOTTATT_KRAVGRUNNLAG}' and tilbakekrevingsvedtakForsendelse is null"
                .hentListe(
                    emptyMap(),
                    session,
                ) {
                    it.toTilbakekrevingsbehandling()
                }.filterIsInstance<Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag>()
        }
    }

    internal fun lagreTilbakekrevingsbehandling(
        tilbakrekrevingsbehanding: Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving,
        tx: TransactionalSession,
    ) {
        slettForRevurderingId(tilbakrekrevingsbehanding.revurderingId, tx)

        "insert into revurdering_tilbakekreving (id, opprettet, sakId, revurderingId, fraOgMed, tilOgMed, avgjørelse, tilstand) values (:id, :opprettet, :sakId, :revurderingId, :fraOgMed, :tilOgMed, :avgjorelse, :tilstand)"
            .insert(
                mapOf(
                    "id" to tilbakrekrevingsbehanding.id,
                    "opprettet" to tilbakrekrevingsbehanding.opprettet,
                    "sakId" to tilbakrekrevingsbehanding.sakId,
                    "revurderingId" to tilbakrekrevingsbehanding.revurderingId,
                    "fraOgMed" to tilbakrekrevingsbehanding.periode.fraOgMed,
                    "tilOgMed" to tilbakrekrevingsbehanding.periode.tilOgMed,
                    "avgjorelse" to when (tilbakrekrevingsbehanding) {
                        is Tilbakekrev -> Avgjørelsestype.TILBAKEKREV
                        is IkkeTilbakekrev -> Avgjørelsestype.IKKE_TILBAKEKREV
                        is IkkeAvgjort -> Avgjørelsestype.IKKE_AVGJORT
                    }.toString(),
                    "tilstand" to Tilstand.UNDER_BEHANDLING.toString(),
                ),
                tx,
            )
    }

    internal fun lagreTilbakekrevingsbehandling(
        tilbakrekrevingsbehanding: Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag,
        session: Session,
    ) {
        """
            update revurdering_tilbakekreving set tilstand = :tilstand where id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to tilbakrekrevingsbehanding.avgjort.id,
                    "tilstand" to Tilstand.AVVENTER_KRAVGRUNNLAG.toString(),
                ),
                session,
            )
    }

    internal fun lagreTilbakekrevingsbehandling(
        tilbakrekrevingsbehanding: Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag,
        session: Session,
    ) {
        """
            update revurdering_tilbakekreving set tilstand = :tilstand, kravgrunnlag = :kravgrunnlag, kravgrunnlagMottatt = :kravgrunnlagMottatt where id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to tilbakrekrevingsbehanding.avgjort.id,
                    "tilstand" to Tilstand.MOTTATT_KRAVGRUNNLAG.toString(),
                    "kravgrunnlag" to tilbakrekrevingsbehanding.kravgrunnlag.melding,
                    "kravgrunnlagMottatt" to tilbakrekrevingsbehanding.kravgrunnlagMottatt,
                ),
                session,
            )
    }

    internal fun lagreTilbakekrevingsbehandling(
        tilbakrekrevingsbehanding: Tilbakekrevingsbehandling.Ferdigbehandlet.MedKravgrunnlag.SendtTilbakekrevingsvedtak,
        session: Session,
    ) {
        """
            update revurdering_tilbakekreving set tilstand = :tilstand, tilbakekrevingsvedtakForsendelse = :tilbakekrevingsvedtakForsendelse where id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to tilbakrekrevingsbehanding.avgjort.id,
                    "tilstand" to Tilstand.SENDT_TILBAKEKREVINGSVEDTAK.toString(),
                    "tilbakekrevingsvedtakForsendelse" to serialize(
                        RåTilbakekrevingsvedtakForsendelseDb.fra(
                            tilbakrekrevingsbehanding.tilbakekrevingsvedtakForsendelse,
                        ),
                    ),
                ),
                session,
            )
    }

    internal fun slettForRevurderingId(revurderingId: UUID, session: Session) {
        """
                delete from revurdering_tilbakekreving where revurderingId = :revurderingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "revurderingId" to revurderingId,
                ),
                session,
            )
    }

    internal fun hentTilbakekrevingsbehandling(revurderingId: UUID, session: Session): Tilbakekrevingsbehandling? {
        return """
            select * from revurdering_tilbakekreving where revurderingId = :revurderingId
        """.trimIndent()
            .hent(
                mapOf("revurderingId" to revurderingId),
                session,
            ) {
                it.toTilbakekrevingsbehandling()
            }
    }

    private fun Row.toTilbakekrevingsbehandling(): Tilbakekrevingsbehandling {
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")
        val revurderingId = uuid("revurderingId")
        val sakId = uuid("sakId")
        val periode = Periode.create(
            fraOgMed = localDate("fraOgMed"),
            tilOgMed = localDate("tilOgMed"),
        )
        val tilstand = Tilstand.fromValue(string("tilstand"))
        val avgjørelse = Avgjørelsestype.fromValue(string("avgjørelse"))
        val kravgrunnlag = stringOrNull("kravgrunnlag")?.let { RåttKravgrunnlag(it) }
        val kravgrunnlagMottatt = tidspunktOrNull("kravgrunnlagMottatt")
        val tilbakekrevingsvedtakForsendelse =
            stringOrNull("tilbakekrevingsvedtakForsendelse")?.let { forsendelseJson ->
                deserialize<RåTilbakekrevingsvedtakForsendelseDb>(forsendelseJson).let {
                    RåTilbakekrevingsvedtakForsendelse(
                        requestXml = it.requestXml,
                        tidspunkt = it.requestSendt,
                        responseXml = it.responseXml,
                    )
                }
            }

        val tilbakekrevingsbehandling = when (avgjørelse) {
            Avgjørelsestype.TILBAKEKREV -> Tilbakekrev(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                revurderingId = revurderingId,
                periode = periode,
            )
            Avgjørelsestype.IKKE_TILBAKEKREV -> IkkeTilbakekrev(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                revurderingId = revurderingId,
                periode = periode,
            )
            Avgjørelsestype.IKKE_AVGJORT -> IkkeAvgjort(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                revurderingId = revurderingId,
                periode = periode,
            )
        }

        return when (tilstand) {
            Tilstand.UNDER_BEHANDLING -> {
                tilbakekrevingsbehandling
            }
            Tilstand.AVVENTER_KRAVGRUNNLAG -> {
                AvventerKravgrunnlag(
                    avgjort = tilbakekrevingsbehandling as Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
                )
            }
            Tilstand.MOTTATT_KRAVGRUNNLAG -> {
                MottattKravgrunnlag(
                    avgjort = tilbakekrevingsbehandling as Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
                    kravgrunnlag = kravgrunnlag!!,
                    kravgrunnlagMottatt = kravgrunnlagMottatt!!,
                )
            }
            Tilstand.SENDT_TILBAKEKREVINGSVEDTAK -> {
                SendtTilbakekrevingsvedtak(
                    avgjort = tilbakekrevingsbehandling as Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
                    kravgrunnlag = kravgrunnlag!!,
                    kravgrunnlagMottatt = kravgrunnlagMottatt!!,
                    tilbakekrevingsvedtakForsendelse = tilbakekrevingsvedtakForsendelse!!,
                )
            }
        }
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }

    override fun hentAvventerKravgrunnlag(sakId: UUID): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag> {
        return sessionFactory.withSession { session ->
            "select * from revurdering_tilbakekreving where sakId = :sakId and tilstand = '${Tilstand.AVVENTER_KRAVGRUNNLAG}'"
                .hentListe(
                    params = mapOf(
                        "sakId" to sakId,
                    ),
                    session = session,
                ) {
                    it.toTilbakekrevingsbehandling()
                }.filterIsInstance<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>()
        }
    }

    // TODO klarer vi å lage noe berdre test-support som lar oss teste denne typen sqls uten å kode seg ihjel? Mulig å bridge oppsett for testdata og TestDataHelper for db?
    override fun hentAvventerKravgrunnlag(utbetalingId: UUID30): Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag? {
        return sessionFactory.withSession { session ->
            """
                select t.* from revurdering_tilbakekreving t
                    join behandling_vedtak bv on bv.revurderingid = t.revurderingid
                    join vedtak v on v.id = bv.vedtakid
                    join utbetaling u on u.id = v.utbetalingid
                where u.id = :utbetalingId
                    and t.tilstand = '${Tilstand.AVVENTER_KRAVGRUNNLAG}'
            """.trimIndent()
                .hent(
                    params = mapOf(
                        "utbetalingId" to utbetalingId,
                    ),
                    session = session,
                ) {
                    it.toTilbakekrevingsbehandling()
                } as? Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag
        }
    }

    override fun hentAvventerKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag> {
        return sessionFactory.withSession { session ->
            "select * from revurdering_tilbakekreving where tilstand = '${Tilstand.AVVENTER_KRAVGRUNNLAG}'"
                .hentListe(
                    params = emptyMap(),
                    session = session,
                ) {
                    it.toTilbakekrevingsbehandling()
                }.filterIsInstance<Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>()
        }
    }

    private enum class Avgjørelsestype(private val value: String) {
        IKKE_AVGJORT("ikke_avgjort"),
        TILBAKEKREV("tilbakekrev"),
        IKKE_TILBAKEKREV("ikke_tilbakekrev"),
        ;

        override fun toString() = value

        companion object {
            fun fromValue(value: String): Avgjørelsestype {
                return entries.firstOrNull { it.value == value }
                    ?: throw IllegalStateException("Ukjent avgjørelsestype: $value")
            }
        }
    }

    private enum class Tilstand(private val value: String) {
        UNDER_BEHANDLING("under_behandling"),
        AVVENTER_KRAVGRUNNLAG("avventer_kravgrunnlag"),
        MOTTATT_KRAVGRUNNLAG("mottatt_kravgrunnlag"),
        SENDT_TILBAKEKREVINGSVEDTAK("sendt_tilbakekrevingsvedtak"),
        ;

        override fun toString() = value

        companion object {
            fun fromValue(value: String): Tilstand {
                return entries.firstOrNull { it.value == value }
                    ?: throw IllegalStateException("Ukjent tilstand: $value")
            }
        }
    }
}
