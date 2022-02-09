package no.nav.su.se.bakover.database.tilbakekreving

import kotliquery.Row
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.tidspunktOrNull
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.BurdeForstått
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Forsto
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.KunneIkkeForstå
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.MottattKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingRepo
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import java.util.UUID

internal class TilbakekrevingPostgresRepo(private val sessionFactory: PostgresSessionFactory) : TilbakekrevingRepo {

    override fun lagreMottattKravgrunnlag(tilbakekrevingsbehandling: Tilbakekrevingsbehandling.Ferdigbehandlet.MottattKravgrunnlag) {
        sessionFactory.withSession { session ->
            lagreTilbakekrevingsbehandling(tilbakekrevingsbehandling, session)
        }
    }

    override fun hentTilbakekrevingsbehandlingerMedUbesvartKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.MottattKravgrunnlag> {
        return sessionFactory.withSession { session ->
            "select * from tilbakekrevingsbehandling where tilstand = '${Tilstand.MOTTATT_KRAVGRUNNLAG}' and kravgrunnlagBesvart is null"
                .hentListe(
                    emptyMap(),
                    session,
                ) {
                    it.toTilbakekrevingsbehandling()
                }.filterIsInstance<Tilbakekrevingsbehandling.Ferdigbehandlet.MottattKravgrunnlag>()
        }
    }

    internal fun lagreTilbakekrevingsbehandling(
        tilbakrekrevingsbehanding: Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving,
        session: Session,
    ) {
        slettForRevurderingId(tilbakrekrevingsbehanding.revurderingId, session)

        "insert into tilbakekrevingsbehandling (id, opprettet, sakId, revurderingId, fraOgMed, tilOgMed, oversendtTidspunkt, avgjørelse, tilstand) values (:id, :opprettet, :sakId, :revurderingId, :fraOgMed, :tilOgMed, :oversendtTidspunkt, :avgjorelse, :tilstand)"
            .insert(
                mapOf(
                    "id" to tilbakrekrevingsbehanding.id,
                    "opprettet" to tilbakrekrevingsbehanding.opprettet,
                    "sakId" to tilbakrekrevingsbehanding.sakId,
                    "revurderingId" to tilbakrekrevingsbehanding.revurderingId,
                    "fraOgMed" to tilbakrekrevingsbehanding.periode.fraOgMed,
                    "tilOgMed" to tilbakrekrevingsbehanding.periode.tilOgMed,
                    "avgjorelse" to when (tilbakrekrevingsbehanding) {
                        is BurdeForstått -> Avgjørelsestype.BURDE_FORSTÅTT
                        is Forsto -> Avgjørelsestype.FORSTO
                        is KunneIkkeForstå -> Avgjørelsestype.FORSTO_IKKE_ELLER_KUNNE_IKKE_FORSTÅTT
                        is IkkeAvgjort -> Avgjørelsestype.IKKE_AVGJORT
                    }.toString(),
                    "tilstand" to Tilstand.UNDER_BEHANDLING.toString(),
                ),
                session,
            )
    }

    internal fun lagreTilbakekrevingsbehandling(
        tilbakrekrevingsbehanding: Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag,
        session: Session,
    ) {
        """
            update tilbakekrevingsbehandling set tilstand = :tilstand where id = :id
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
        tilbakrekrevingsbehanding: Tilbakekrevingsbehandling.Ferdigbehandlet.MottattKravgrunnlag,
        session: Session,
    ) {
        """
            update tilbakekrevingsbehandling set tilstand = :tilstand, kravgrunnlag = :kravgrunnlag, kravgrunnlagMottatt = :kravgrunnlagMottatt where id = :id
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

    internal fun slettForRevurderingId(revurderingId: UUID, session: Session) {
        """
                delete from tilbakekrevingsbehandling where revurderingId = :revurderingId
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
            select * from tilbakekrevingsbehandling where revurderingId = :revurderingId
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
        val periode = no.nav.su.se.bakover.common.periode.Periode.create(
            fraOgMed = localDate("fraOgMed"),
            tilOgMed = localDate("tilOgMed"),
        )
        val tilstand = Tilstand.fromValue(string("tilstand"))
        val avgjørelse = Avgjørelsestype.fromValue(string("avgjørelse"))
        val kravgrunnlag = stringOrNull("kravgrunnlag")?.let { RåttKravgrunnlag(it) }
        val kravgrunnlagMottatt = tidspunktOrNull("kravgrunnlagMottatt")

        val tilbakekrevingsbehandling = when (avgjørelse) {
            Avgjørelsestype.FORSTO -> Forsto(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                revurderingId = revurderingId,
                periode = periode,
            )
            Avgjørelsestype.BURDE_FORSTÅTT -> BurdeForstått(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                revurderingId = revurderingId,
                periode = periode,
            )
            Avgjørelsestype.FORSTO_IKKE_ELLER_KUNNE_IKKE_FORSTÅTT -> KunneIkkeForstå(
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

        return when {
            Tilstand.UNDER_BEHANDLING == tilstand -> {
                tilbakekrevingsbehandling
            }
            Tilstand.AVVENTER_KRAVGRUNNLAG == tilstand -> {
                AvventerKravgrunnlag(
                    avgjort = tilbakekrevingsbehandling as Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
                )
            }
            Tilstand.MOTTATT_KRAVGRUNNLAG == tilstand -> {
                MottattKravgrunnlag(
                    avgjort = tilbakekrevingsbehandling as Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort,
                    kravgrunnlag = kravgrunnlag!!,
                    kravgrunnlagMottatt = kravgrunnlagMottatt!!,
                )
            }
            else -> {
                throw IllegalStateException("Kunne ikke utlede tilstand for tilbakekrevingsgrunnlag fra parameterne: $tilstand og $avgjørelse")
            }
        }
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }

    override fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(sakId: UUID): List<Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag> {
        return sessionFactory.withSession { session ->
            "select * from tilbakekrevingsbehandling where sakId = :sakId and tilstand = '${Tilstand.AVVENTER_KRAVGRUNNLAG}'"
                .hentListe(
                    params = mapOf(
                        "sakId" to sakId,
                    ),
                    session = session,
                ) {
                    it.toTilbakekrevingsbehandling()
                }.filterIsInstance<Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag>()
        }
    }

    override fun hentTilbakekrevingsbehandlingerSomAvventerKravgrunnlag(): List<Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag> {
        return sessionFactory.withSession { session ->
            "select * from tilbakekrevingsbehandling where tilstand = '${Tilstand.AVVENTER_KRAVGRUNNLAG}'"
                .hentListe(
                    params = emptyMap(),
                    session = session,
                ) {
                    it.toTilbakekrevingsbehandling()
                }.filterIsInstance<Tilbakekrevingsbehandling.Ferdigbehandlet.AvventerKravgrunnlag>()
        }
    }

    private enum class Avgjørelsestype(private val value: String) {
        IKKE_AVGJORT("ikke_avgjort"),
        FORSTO("forsto"),
        BURDE_FORSTÅTT("burde_forstått"),
        FORSTO_IKKE_ELLER_KUNNE_IKKE_FORSTÅTT("forsto_ikke_eller_kunne_ikke_forstått");

        override fun toString() = value

        companion object {
            fun fromValue(value: String): Avgjørelsestype {
                return values().firstOrNull { it.value == value }
                    ?: throw IllegalStateException("Ukjent avgjørelsestype: $value")
            }
        }
    }

    private enum class Tilstand(private val value: String) {
        UNDER_BEHANDLING("under_behandling"),
        AVVENTER_KRAVGRUNNLAG("avventer_kravgrunnlag"),
        MOTTATT_KRAVGRUNNLAG("mottatt_kravgrunnlag");

        override fun toString() = value

        companion object {
            fun fromValue(value: String): Tilstand {
                return values().firstOrNull { it.value == value }
                    ?: throw IllegalStateException("Ukjent tilstand: $value")
            }
        }
    }
}
