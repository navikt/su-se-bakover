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
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingRepo
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import java.util.UUID

internal class TilbakekrevingPostgresRepo(private val sessionFactory: PostgresSessionFactory) : TilbakekrevingRepo {

    override fun lagreKravgrunnlag(kravgrunnlag: RåttKravgrunnlag) {
        sessionFactory.withSession { session ->
            "insert into kravgrunnlag (id, opprettet, melding, type) values (:id, :opprettet, :melding, :type)".insert(
                mapOf(
                    "id" to kravgrunnlag.id,
                    "opprettet" to kravgrunnlag.opprettet,
                    "melding" to kravgrunnlag.melding,
                    "type" to when (kravgrunnlag) {
                        is RåttKravgrunnlag.Ferdigbehandlet -> Kravgrunnlagtype.FERDIGBEHANDLET
                        is RåttKravgrunnlag.Ubehandlet -> Kravgrunnlagtype.UBEHANDLET
                    }.toString(),
                ),
                session,
            )
        }
    }

    override fun hentUbehandlaKravgrunnlag(): List<RåttKravgrunnlag> {
        return sessionFactory.withSession { session ->
            "select * from kravgrunnlag where type = '${Kravgrunnlagtype.UBEHANDLET}'".hentListe(
                emptyMap(),
                session,
            ) {
                val id = it.uuid("id")
                val opprettet = it.tidspunkt("opprettet")
                val melding = it.string("melding")

                when (Kravgrunnlagtype.fromValue(it.string("type"))) {
                    Kravgrunnlagtype.UBEHANDLET -> RåttKravgrunnlag.Ubehandlet.persistert(
                        id = id,
                        opprettet = opprettet,
                        melding = melding,
                    )
                    Kravgrunnlagtype.FERDIGBEHANDLET -> throw IllegalStateException("Vi ba eksplisitt om kun ubehandla (sjekk sql, skriv tester)")
                }
            }
        }
    }

    internal fun lagreTilbakekrevingsbehandling(
        tilbakrekrevingsbehanding: Tilbakekrevingsbehandling,
        session: Session,
    ) {
        when (tilbakrekrevingsbehanding) {
            is Tilbakekrevingsbehandling.IkkeBehovForTilbakekreving -> {
                // noop
            }
            is Tilbakekrevingsbehandling.VurderTilbakekreving -> {
                slettForRevurderingId(tilbakrekrevingsbehanding.revurderingId, session)

                "insert into tilbakekrevingsbehandling (id, opprettet, sakId, revurderingId, fraOgMed, tilOgMed, oversendtTidspunkt, type) values (:id, :opprettet, :sakId, :revurderingId, :fraOgMed, :tilOgMed, :oversendtTidspunkt, :type)"
                    .insert(
                        mapOf(
                            "id" to tilbakrekrevingsbehanding.id,
                            "opprettet" to tilbakrekrevingsbehanding.opprettet,
                            "sakId" to tilbakrekrevingsbehanding.sakId,
                            "revurderingId" to tilbakrekrevingsbehanding.revurderingId,
                            "fraOgMed" to tilbakrekrevingsbehanding.periode.fraOgMed,
                            "tilOgMed" to tilbakrekrevingsbehanding.periode.tilOgMed,
                            "type" to when (tilbakrekrevingsbehanding) {
                                is Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort.BurdeForstått -> Avgjørelsestype.BURDE_FORSTÅTT
                                is Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort.Forsto -> Avgjørelsestype.FORSTO
                                is Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort.KunneIkkeForstått -> Avgjørelsestype.FORSTO_IKKE_ELLER_KUNNE_IKKE_FORSTÅTT
                                is Tilbakekrevingsbehandling.VurderTilbakekreving.IkkeAvgjort -> Avgjørelsestype.IKKE_AVGJORT
                            }.toString(),
                        ),
                        session,
                    )
            }
        }
    }

    override fun hentTilbakekrevingsbehandling(saksnummer: Saksnummer): Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort {
        TODO()
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

        return when (Avgjørelsestype.fromValue(string("type"))) {
            Avgjørelsestype.FORSTO -> Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort.Forsto(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                revurderingId = revurderingId,
                periode = periode,
            )
            Avgjørelsestype.BURDE_FORSTÅTT -> Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort.BurdeForstått(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                revurderingId = revurderingId,
                periode = periode,
            )
            Avgjørelsestype.FORSTO_IKKE_ELLER_KUNNE_IKKE_FORSTÅTT -> Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort.KunneIkkeForstått(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                revurderingId = revurderingId,
                periode = periode,
            )
            Avgjørelsestype.IKKE_AVGJORT -> Tilbakekrevingsbehandling.VurderTilbakekreving.IkkeAvgjort(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                revurderingId = revurderingId,
                periode = periode,
            )
        }
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }

    override fun hentIkkeOversendteTilbakekrevingsbehandlinger(sakId: UUID): List<Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort> {
        return sessionFactory.withSession { session ->
            "select * from tilbakekrevingsbehandling where sakId = :sakId and oversendtTidspunkt is null".hentListe(
                emptyMap(),
                session,
            ) {
                it.toTilbakekrevingsbehandling()
            }.filterIsInstance<Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort>()
        }
    }

    private enum class Kravgrunnlagtype(private val value: String) {
        UBEHANDLET("ubehandlet"),
        FERDIGBEHANDLET("ferdigbehandlet");

        override fun toString() = value

        companion object {
            fun fromValue(value: String): Kravgrunnlagtype {
                return values().firstOrNull { it.value == value }
                    ?: throw IllegalStateException("Ukjent kravgrunnlagstype: $value")
            }
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
}
