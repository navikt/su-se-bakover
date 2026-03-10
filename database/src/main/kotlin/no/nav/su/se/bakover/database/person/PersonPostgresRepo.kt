package no.nav.su.se.bakover.database.person

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.person.Fnr
import person.domain.PersonRepo
import person.domain.PersonerOgSakstype
import java.util.UUID

internal class PersonPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : PersonRepo {

    /** Henter fødselsnumrene knyttet til saken. Dette inkluderer alle registrerte EPS. */
    override fun hentFnrOgSaktypeForSak(sakId: UUID): PersonerOgSakstype {
        return dbMetrics.timeQuery("hentFnrForSak") {
            sessionFactory.withSession { session ->
                val listeAvfnrs = """
                  SELECT
                    s.fnr søkersFnr,
                    gb.eps_fnr epsFnr,
                    s.sakstype
                  FROM sak s
                    LEFT JOIN behandling b ON b.sakid = s.id
                    LEFT JOIN revurdering r ON r.sakid = s.id
                    LEFT JOIN grunnlag_bosituasjon gb ON gb.behandlingId IN (b.id, r.id)
                  WHERE s.id=:sakId
                """
                    .trimIndent()
                    .hentListe(mapOf("sakId" to sakId), session) {
                        Triple(
                            Sakstype.from(it.string("sakstype")),
                            it.string("søkersFnr"),
                            it.stringOrNull("epsFnr"),
                        )
                    }
                val sakstype = listeAvfnrs.firstOrNull()?.first
                    ?: throw IllegalArgumentException("Fant ikke sak for sakId=$sakId")
                PersonerOgSakstype(
                    sakstype = sakstype,
                    fnr = listeAvfnrs
                        .flatMap { listOfNotNull(it.third, it.second) }
                        .distinct()
                        .sorted()
                        .map { Fnr(it) },
                )
            }
        }
    }

    override fun hentFnrForSøknad(søknadId: UUID): PersonerOgSakstype {
        return dbMetrics.timeQuery("hentFnrForSøknadId") {
            sessionFactory.withSession { session ->
                val listeAvfnrs = """
                SELECT
                    sak.fnr søkersFnr,
                    eps_fnr epsFnr,
                    s.sakstype
                FROM søknad
                INNER JOIN sak ON søknad.sakid = sak.id
                LEFT JOIN behandling ON behandling.sakid = sak.id
                LEFT JOIN grunnlag_bosituasjon ON grunnlag_bosituasjon.behandlingId = behandling.id
                WHERE søknad.id=:soknadId
                """
                    .trimMargin()
                    .hentListe(mapOf("soknadId" to søknadId), session) {
                        Triple(
                            Sakstype.from(it.string("sakstype")),
                            it.string("søkersFnr"),
                            it.stringOrNull("epsFnr"),
                        )
                    }
                val sakstype = listeAvfnrs.firstOrNull()?.first
                    ?: throw IllegalArgumentException("Fant ikke søknadId for søknadId=$søknadId")
                PersonerOgSakstype(
                    sakstype = sakstype,
                    fnr = listeAvfnrs
                        .flatMap { listOfNotNull(it.third, it.second) }
                        .distinct()
                        .sorted()
                        .map { Fnr(it) },
                )
            }
        }
    }

    override fun hentFnrForBehandling(behandlingId: UUID): PersonerOgSakstype {
        return dbMetrics.timeQuery("hentFnrForBehandlingId") {
            sessionFactory.withSession { session ->
                val listeAvfnrs = """
               SELECT
                    sak.fnr søkersFnr,
                    eps_fnr epsFnr,
                    s.sakstype
               FROM behandling
               INNER JOIN sak ON behandling.sakid = sak.id
               LEFT JOIN grunnlag_bosituasjon ON grunnlag_bosituasjon.behandlingId = behandling.id
               WHERE behandling.id=:behandlingId
                """
                    .trimMargin()
                    .hentListe(mapOf("behandlingId" to behandlingId), session) {
                        Triple(
                            Sakstype.from(it.string("sakstype")),
                            it.string("søkersFnr"),
                            it.stringOrNull("epsFnr"),
                        )
                    }
                val sakstype = listeAvfnrs.firstOrNull()?.first
                    ?: throw IllegalArgumentException("Fant ikke behandlingId for behandlingId=$behandlingId")
                PersonerOgSakstype(
                    sakstype = sakstype,
                    fnr = listeAvfnrs
                        .flatMap { listOfNotNull(it.third, it.second) }
                        .distinct()
                        .sorted()
                        .map { Fnr(it) },
                )
            }
        }
    }

    override fun hentFnrForUtbetaling(utbetalingId: UUID30): PersonerOgSakstype {
        return dbMetrics.timeQuery("hentFnrForUtbetalingId") {
            sessionFactory.withSession { session ->
                val listeAvfnrs = """
               SELECT
                    sak.fnr søkersFnr,
                    eps_fnr epsFnr,
                    s.sakstype
               FROM utbetaling
               INNER JOIN sak on sak.id = utbetaling.sakId
               LEFT JOIN behandling ON behandling.sakid = utbetaling.sakId
               LEFT JOIN grunnlag_bosituasjon ON grunnlag_bosituasjon.behandlingId = behandling.id
               WHERE utbetaling.id=:utbetalingId
                """
                    .trimMargin()
                    .hentListe(mapOf("utbetalingId" to utbetalingId), session) {
                        Triple(
                            Sakstype.from(it.string("sakstype")),
                            it.string("søkersFnr"),
                            it.stringOrNull("epsFnr"),
                        )
                    }
                val sakstype = listeAvfnrs.firstOrNull()?.first
                    ?: throw IllegalArgumentException("Fant ikke utbetalingId for utbetalingId=$utbetalingId")
                PersonerOgSakstype(
                    sakstype = sakstype,
                    fnr = listeAvfnrs
                        .flatMap { listOfNotNull(it.third, it.second) }
                        .distinct()
                        .sorted()
                        .map { Fnr(it) },
                )
            }
        }
    }

    override fun hentFnrForRevurdering(revurderingId: UUID): PersonerOgSakstype {
        return dbMetrics.timeQuery("hentFnrForRevurderingId") {
            sessionFactory.withSession { session ->
                val listeAvfnrs = """
               SELECT
                    s.fnr søkersFnr,
                    eps_fnr epsFnr,
                    s.sakstype
               FROM revurdering r
               INNER JOIN behandling_vedtak bv on bv.vedtakId = r.vedtakSomRevurderesId
               INNER JOIN sak s ON s.id = bv.sakId
               LEFT JOIN grunnlag_bosituasjon ON grunnlag_bosituasjon.behandlingId = r.id
               WHERE r.id=:revurderingId
                """
                    .trimMargin()
                    .hentListe(mapOf("revurderingId" to revurderingId), session) {
                        Triple(
                            Sakstype.from(it.string("sakstype")),
                            it.string("søkersFnr"),
                            it.stringOrNull("epsFnr"),
                        )
                    }
                val sakstype = listeAvfnrs.firstOrNull()?.first
                    ?: throw IllegalArgumentException("Fant ikke revurderingId for revurderingId=$revurderingId")
                PersonerOgSakstype(
                    sakstype = sakstype,
                    fnr = listeAvfnrs
                        .flatMap { listOfNotNull(it.third, it.second) }
                        .distinct()
                        .sorted()
                        .map { Fnr(it) },
                )
            }
        }
    }

    override fun hentFnrForVedtak(vedtakId: UUID): PersonerOgSakstype {
        return dbMetrics.timeQuery("hentFnrForVedtakId") {
            sessionFactory.withSession { session ->
                val listeAvfnrs = """
               SELECT
                    s.fnr søkersFnr,
                    eps_fnr epsFnr,
                    s.sakstype
                FROM behandling_vedtak bv
                LEFT JOIN sak s ON s.id = bv.sakId
                LEFT JOIN behandling b ON b.id = bv.søknadsbehandlingId
                LEFT JOIN revurdering r ON r.id = bv.revurderingId
                LEFT JOIN grunnlag_bosituasjon gb ON gb.behandlingId IN (b.id, r.id)
                WHERE bv.vedtakId = :vedtakId;
                """
                    .trimMargin()
                    .hentListe(mapOf("vedtakId" to vedtakId), session) {
                        Triple(
                            Sakstype.from(it.string("sakstype")),
                            it.string("søkersFnr"),
                            it.stringOrNull("epsFnr"),
                        )
                    }
                val sakstype = listeAvfnrs.firstOrNull()?.first
                    ?: throw IllegalArgumentException("Fant ikke vedtakId for vedtakId=$vedtakId")
                PersonerOgSakstype(
                    sakstype = sakstype,
                    fnr = listeAvfnrs
                        .flatMap { listOfNotNull(it.third, it.second) }
                        .distinct()
                        .sorted()
                        .map { Fnr(it) },
                )
            }
        }
    }

    override fun hentFnrForKlage(klageId: UUID): PersonerOgSakstype {
        return dbMetrics.timeQuery("hentFnrForKlageId") {
            sessionFactory.withSession { session ->
                val listeAvfnrs = """
               SELECT
                    s.fnr søkersFnr,
                    eps_fnr epsFnr,
                    s.sakstype
                FROM klage k
                INNER JOIN sak s ON s.id = k.sakId
                INNER JOIN behandling_vedtak bv ON bv.sakId = k.sakId
                INNER JOIN behandling b ON b.id = bv.søknadsbehandlingId
                LEFT JOIN revurdering r ON r.id = bv.revurderingId
                INNER JOIN grunnlag_bosituasjon gb ON gb.behandlingId IN (b.id, r.id)
                WHERE bv.vedtakId = :vedtakId;
                """
                    .trimMargin()
                    .hentListe(mapOf("klageId" to klageId), session) {
                        Triple(
                            Sakstype.from(it.string("sakstype")),
                            it.string("søkersFnr"),
                            it.stringOrNull("epsFnr"),
                        )
                    }
                val sakstype = listeAvfnrs.firstOrNull()?.first
                    ?: throw IllegalArgumentException("Fant ikke klageId for klageId=$klageId")
                PersonerOgSakstype(
                    sakstype = sakstype,
                    fnr = listeAvfnrs
                        .flatMap { listOfNotNull(it.third, it.second) }
                        .distinct()
                        .sorted()
                        .map { Fnr(it) },
                )
            }
        }
    }
}
