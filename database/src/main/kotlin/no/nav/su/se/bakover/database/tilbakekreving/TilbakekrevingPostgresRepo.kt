package no.nav.su.se.bakover.database.tilbakekreving

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.tidspunktOrNull
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingRepo
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsavgjørelse
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

    override fun lagreTilbakekrevingsavgjørelse(tilbakekrevingsavgjørelse: Tilbakekrevingsavgjørelse) {
        sessionFactory.withSession { session ->
            "insert into tilbakekrevingsavgjørelse (id, opprettet, sakId, revurderingId, periode, oversendtTidspunkt, type) values (:id, :opprettet, :sakId, :periode, :oversendtTidspunkt, :type)".insert(
                mapOf(
                    "id" to tilbakekrevingsavgjørelse.id,
                    "opprettet" to tilbakekrevingsavgjørelse.opprettet,
                    "sakId" to tilbakekrevingsavgjørelse.sakId,
                    "revurderingId" to tilbakekrevingsavgjørelse.revurderingId,
                    "fraOgMed" to tilbakekrevingsavgjørelse.periode.fraOgMed,
                    "tilOgMed" to tilbakekrevingsavgjørelse.periode.tilOgMed,
                    "oversendtTidspunkt" to tilbakekrevingsavgjørelse.oversendtTidspunkt,
                    "type" to when (tilbakekrevingsavgjørelse) {
                        is Tilbakekrevingsavgjørelse.SkalTilbakekreve.Forsto -> Avgjørelsestype.FORSTO
                        is Tilbakekrevingsavgjørelse.SkalTilbakekreve.BurdeForstått -> Avgjørelsestype.BURDE_FORSTÅTT
                        is Tilbakekrevingsavgjørelse.SkalIkkeTilbakekreve -> Avgjørelsestype.FORSTO_IKKE_ELLER_KUNNE_IKKE_FORSTÅTT
                    }.toString(),
                ),
                session,
            )
        }
    }

    override fun hentTilbakekrevingsavgjørelse(): Tilbakekrevingsavgjørelse {
        TODO()
    }

    override fun hentUoversendteTilbakekrevingsavgjørelser(sakId: UUID): List<Tilbakekrevingsavgjørelse> {
        return sessionFactory.withSession { session ->
            "select * from tilbakekrevingsavgjørelse where sakId = :sakId and oversendtTidspunkt is null".hentListe(
                emptyMap(),
                session,
            ) {
                val id = it.uuid("id")
                val opprettet = it.tidspunkt("opprettet")
                val revurderingId = it.uuid("revurderingId")
                val periode = Periode.create(
                    fraOgMed = it.localDate("fraOgMed"),
                    tilOgMed = it.localDate("tilOgMed"),
                )
                val oversendtTidspunkt = it.tidspunktOrNull("oversendtTidspunkt")

                when (Avgjørelsestype.fromValue(it.string("type"))) {
                    Avgjørelsestype.FORSTO -> Tilbakekrevingsavgjørelse.SkalTilbakekreve.Forsto(
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        revurderingId = revurderingId,
                        periode = periode,
                        oversendtTidspunkt = oversendtTidspunkt,
                    )
                    Avgjørelsestype.BURDE_FORSTÅTT -> Tilbakekrevingsavgjørelse.SkalTilbakekreve.BurdeForstått(
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        revurderingId = revurderingId,
                        periode = periode,
                        oversendtTidspunkt = oversendtTidspunkt,
                    )
                    Avgjørelsestype.FORSTO_IKKE_ELLER_KUNNE_IKKE_FORSTÅTT -> Tilbakekrevingsavgjørelse.SkalIkkeTilbakekreve(
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        revurderingId = revurderingId,
                        periode = periode,
                        oversendtTidspunkt = oversendtTidspunkt,
                    )
                }
            }
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
