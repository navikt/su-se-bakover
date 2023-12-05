package no.nav.su.se.bakover.database.tilbakekreving

import arrow.core.Either
import arrow.core.getOrElse
import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
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
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.IkkeTilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.MottattKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.SendtTilbakekrevingsvedtak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.Tilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.TilbakekrevingUnderRevurderingRepo
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.TilbakekrevingsbehandlingUnderRevurdering
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåTilbakekrevingsvedtakForsendelse
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlag
import tilbakekreving.infrastructure.repo.kravgrunnlag.mapDbJsonToKravgrunnlag
import tilbakekreving.infrastructure.repo.kravgrunnlag.mapKravgrunnlagToDbJson
import java.util.UUID

/**
 * @param råttKravgrunnlagMapper brukes for de historiske tilfellene, der vi lagres RåttKravgrunnlag og ikke json-versjonen av Kravgrunnlag
 */
internal class TilbakekrevingUnderRevurderingPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val råttKravgrunnlagMapper: MapRåttKravgrunnlag,
) : TilbakekrevingUnderRevurderingRepo {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagre(
        tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag,
        sessionContext: SessionContext?,
    ) {
        sessionContext.withOptionalSession(sessionFactory) { session ->
            lagreTilbakekrevingsbehandling(tilbakekrevingsbehandling, session)
        }
    }

    override fun lagre(
        tilbakekrevingsbehandling: TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.SendtTilbakekrevingsvedtak,
        transactionContext: TransactionContext,
    ) {
        transactionContext.withTransaction { session ->
            lagreTilbakekrevingsbehandling(tilbakekrevingsbehandling, session)
        }
    }

    override fun hentMottattKravgrunnlag(): List<TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag> {
        return sessionFactory.withSession { session ->
            "select * from revurdering_tilbakekreving where tilstand = '${Tilstand.MOTTATT_KRAVGRUNNLAG}' and tilbakekrevingsvedtakForsendelse is null"
                .hentListe(
                    emptyMap(),
                    session,
                ) {
                    it.toTilbakekrevingsbehandling()
                }.filterIsInstance<TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag>()
        }
    }

    internal fun lagreTilbakekrevingsbehandling(
        tilbakrekrevingsbehanding: TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving,
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
        tilbakrekrevingsbehanding: TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag,
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
        tilbakrekrevingsbehanding: TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.MottattKravgrunnlag,
        session: Session,
    ) {
        """
            update revurdering_tilbakekreving set tilstand = :tilstand, kravgrunnlag = :kravgrunnlag, kravgrunnlagMottatt = :kravgrunnlagMottatt where id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to tilbakrekrevingsbehanding.avgjort.id,
                    "tilstand" to Tilstand.MOTTATT_KRAVGRUNNLAG.toString(),
                    "kravgrunnlag" to mapKravgrunnlagToDbJson(tilbakrekrevingsbehanding.kravgrunnlag),
                    "kravgrunnlagMottatt" to tilbakrekrevingsbehanding.kravgrunnlagMottatt,
                ),
                session,
            )
    }

    internal fun lagreTilbakekrevingsbehandling(
        tilbakrekrevingsbehanding: TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.MedKravgrunnlag.SendtTilbakekrevingsvedtak,
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

    internal fun hentTilbakekrevingsbehandling(revurderingId: UUID, session: Session): TilbakekrevingsbehandlingUnderRevurdering? {
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

    private fun Row.toTilbakekrevingsbehandling(): TilbakekrevingsbehandlingUnderRevurdering {
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
        val kravgrunnlag: Kravgrunnlag? = getKravgrunnlag(revurderingId)
        val kravgrunnlagMottatt = tidspunktOrNull("kravgrunnlagMottatt")
        val tilbakekrevingsvedtakForsendelse =
            stringOrNull("tilbakekrevingsvedtakForsendelse")?.let { forsendelseJson ->
                deserialize<RåTilbakekrevingsvedtakForsendelseDb>(forsendelseJson).let {
                    RåTilbakekrevingsvedtakForsendelse(
                        requestXml = it.requestXml,
                        tidspunkt = it.tidspunkt,
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
                    avgjort = tilbakekrevingsbehandling as TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving.Avgjort,
                )
            }

            Tilstand.MOTTATT_KRAVGRUNNLAG -> {
                MottattKravgrunnlag(
                    avgjort = tilbakekrevingsbehandling as TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving.Avgjort,
                    kravgrunnlag = kravgrunnlag!!,
                    kravgrunnlagMottatt = kravgrunnlagMottatt!!,
                )
            }

            Tilstand.SENDT_TILBAKEKREVINGSVEDTAK -> {
                SendtTilbakekrevingsvedtak(
                    avgjort = tilbakekrevingsbehandling as TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving.Avgjort,
                    kravgrunnlag = kravgrunnlag!!,
                    kravgrunnlagMottatt = kravgrunnlagMottatt!!,
                    tilbakekrevingsvedtakForsendelse = tilbakekrevingsvedtakForsendelse!!,
                )
            }
        }
    }

    private fun Row.getKravgrunnlag(revurderingId: UUID): Kravgrunnlag? {
        return stringOrNull("kravgrunnlag")?.trim()?.let { dbJsonOrXml ->
            when {
                dbJsonOrXml.startsWith("<") -> {
                    log.info("revurdering_tilbakekreving.kravgrunnlag er xml; rått kravgrunnlag fra oppdrag.")
                    Either.catch {
                        // Virker som mapperen kan kaste exceptions, så vi wrapper den for ikke å logge innholdet i XMLen.
                        råttKravgrunnlagMapper(RåttKravgrunnlag(dbJsonOrXml)).getOrElse { throw it }
                    }.getOrElse {
                        sikkerLogg.error(
                            "Klarte ikke mappe rått kravgrunnlag til domenemodellen for revurdering $revurderingId. Rått kravgrunnlag: $dbJsonOrXml",
                            it,
                        )
                        throw IllegalStateException(
                            "Klarte ikke mappe det rå kravgrunnlaget til domenemodellen for revurdering $revurderingId. Se sikkerlogg for det rå kravgrunnlaget og stack trace",
                        )
                    }.also {
                        log.info("revurdering_tilbakekreving.kravgrunnlag mappet suksessfullt.")
                    }
                }

                dbJsonOrXml.startsWith("{") -> {
                    log.info("revurdering_tilbakekreving.kravgrunnlag er json; ny json-type for kravgrunnlag")
                    mapDbJsonToKravgrunnlag(dbJsonOrXml).getOrElse {
                        sikkerLogg.error(
                            "Klarte ikke deseralisere til den nye kravgrunnlag-json-typen. For revurdering $revurderingId. Rått kravgrunnlag: $dbJsonOrXml",
                            it,
                        )
                        throw IllegalStateException("Klarte ikke deseralisere til den nye kravgrunnlag-json-typen. For revurdering $revurderingId. Se sikkerlogg for mer informasjon.")
                    }.also {
                        log.info("revurdering_tilbakekreving.kravgrunnlag mappet suksessfullt.")
                    }
                }

                else -> {
                    sikkerLogg.error(
                        "Ukjent format på revurdering_tilbakekreving.kravgrunnlag, forventet json eller xml, men var: $dbJsonOrXml",
                        RuntimeException("Trigger en exception for å få stacktrace"),
                    )
                    throw IllegalStateException(
                        "Ukjent format på revurdering_tilbakekreving.kravgrunnlag, forventet json eller xml. Se sikkerlogg for det rå kravgrunnlaget.",
                    )
                }
            }
        }
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }

    override fun hentAvventerKravgrunnlag(sakId: UUID): List<TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag> {
        return sessionFactory.withSession { session ->
            "select * from revurdering_tilbakekreving where sakId = :sakId and tilstand = '${Tilstand.AVVENTER_KRAVGRUNNLAG}'"
                .hentListe(
                    params = mapOf(
                        "sakId" to sakId,
                    ),
                    session = session,
                ) {
                    it.toTilbakekrevingsbehandling()
                }.filterIsInstance<TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>()
        }
    }

    // TODO klarer vi å lage noe berdre test-support som lar oss teste denne typen sqls uten å kode seg ihjel? Mulig å bridge oppsett for testdata og TestDataHelper for db?
    override fun hentAvventerKravgrunnlag(utbetalingId: UUID30): TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag? {
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
                } as? TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag
        }
    }

    override fun hentAvventerKravgrunnlag(): List<TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag> {
        return sessionFactory.withSession { session ->
            "select * from revurdering_tilbakekreving where tilstand = '${Tilstand.AVVENTER_KRAVGRUNNLAG}'"
                .hentListe(
                    params = emptyMap(),
                    session = session,
                ) {
                    it.toTilbakekrevingsbehandling()
                }.filterIsInstance<TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag>()
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
