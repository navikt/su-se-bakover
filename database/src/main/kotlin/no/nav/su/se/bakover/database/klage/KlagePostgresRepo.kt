package no.nav.su.se.bakover.database.klage

import kotliquery.Row
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.database.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.booleanOrNull
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.Typer.Companion.databasetype
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuidOrNull
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurderingerTilKlage
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import java.util.UUID

internal class KlagePostgresRepo(private val sessionFactory: PostgresSessionFactory) : KlageRepo {

    override fun lagre(klage: Klage) {
        sessionFactory.withSession { session ->
            when (klage) {
                is OpprettetKlage -> lagreOpprettetKlage(klage, session)
                is VilkårsvurdertKlage -> lagreVilkårsvurdertKlage(klage, session)
            }
        }
    }

    private fun lagreOpprettetKlage(klage: OpprettetKlage, session: Session) {
        """
            insert into klage(id,  sakid,  opprettet,  journalpostid,  saksbehandler,  type)
                      values(:id, :sakid, :opprettet, :journalpostid, :saksbehandler, :type)
        """.trimIndent()
            .insert(
                params = mapOf(
                    "id" to klage.id,
                    "sakid" to klage.sakId,
                    "opprettet" to klage.opprettet,
                    "journalpostid" to klage.journalpostId,
                    "saksbehandler" to klage.saksbehandler,
                    "type" to klage.databasetype(),
                ),
                session = session,
            )
    }

    private fun lagreVilkårsvurdertKlage(klage: VilkårsvurdertKlage, session: Session) {
        """
            update klage set
                saksbehandler=:saksbehandler,
                type=:type,
                vedtakId=:vedtakId,
                innenforFristen=:innenforFristen,
                klagesDetPåKonkreteElementerIVedtaket=:klagesDetPaaKonkreteElementerIVedtaket,
                erUnderskrevet=:erUnderskrevet,
                begrunnelse=:begrunnelse
            where id=:id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to klage.id,
                    "saksbehandler" to klage.saksbehandler,
                    "type" to klage.databasetype(),
                    "vedtakId" to klage.vilkårsvurderinger.vedtakId,
                    "innenforFristen" to klage.vilkårsvurderinger.innenforFristen,
                    "klagesDetPaaKonkreteElementerIVedtaket" to klage.vilkårsvurderinger.klagesDetPåKonkreteElementerIVedtaket,
                    "erUnderskrevet" to klage.vilkårsvurderinger.erUnderskrevet,
                    "begrunnelse" to klage.vilkårsvurderinger.begrunnelse,
                ),
                session,
            )
    }

    override fun hentKlage(klageId: UUID): Klage? {
        return sessionFactory.withSession { session ->
            "select * from klage where id=:id".trimIndent().hent(
                params = mapOf("id" to klageId),
                session = session,
            ) { rowToKlage(it) }
        }
    }

    override fun hentKlager(sakid: UUID, sessionContext: SessionContext): List<Klage> {
        return sessionContext.withSession { session ->
            """
                    select * from klage where sakid=:sakid
            """.trimIndent().hentListe(
                mapOf(
                    "sakid" to sakid,
                ),
                session,
            ) { rowToKlage(it) }
        }
    }

    override fun defaultSessionContext(): SessionContext {
        return sessionFactory.newSessionContext()
    }

    private fun rowToKlage(row: Row): Klage {

        val vedtakId = row.uuidOrNull("vedtakId")
        val innenforFristen = row.booleanOrNull("innenforFristen")
        val klagesDetPåKonkreteElementerIVedtaket = row.booleanOrNull("klagesDetPåKonkreteElementerIVedtaket")
        val erUnderskrevet = row.booleanOrNull("erUnderskrevet")
        val begrunnelse = row.stringOrNull("begrunnelse")

        return when (Typer.fromString(row.string("type"))) {
            Typer.OPPRETTET -> OpprettetKlage.create(
                id = row.uuid("id"),
                opprettet = row.tidspunkt("opprettet"),
                sakId = row.uuid("sakid"),
                journalpostId = JournalpostId(row.string("journalpostid")),
                saksbehandler = NavIdentBruker.Saksbehandler(row.string("saksbehandler")),
            )
            Typer.VILKÅRSVURDERT_PÅBEGYNT -> VilkårsvurdertKlage.Påbegynt.create(
                id = row.uuid("id"),
                opprettet = row.tidspunkt("opprettet"),
                sakId = row.uuid("sakid"),
                journalpostId = JournalpostId(row.string("journalpostid")),
                saksbehandler = NavIdentBruker.Saksbehandler(row.string("saksbehandler")),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.Påbegynt(
                    vedtakId = vedtakId,
                    innenforFristen = innenforFristen,
                    klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket,
                    erUnderskrevet = erUnderskrevet,
                    begrunnelse = begrunnelse,
                ),
            )
            Typer.VILKÅRSVURDERT_FERDIG -> VilkårsvurdertKlage.Ferdig.create(
                id = row.uuid("id"),
                opprettet = row.tidspunkt("opprettet"),
                sakId = row.uuid("sakid"),
                journalpostId = JournalpostId(row.string("journalpostid")),
                saksbehandler = NavIdentBruker.Saksbehandler(row.string("saksbehandler")),
                vilkårsvurderinger = VilkårsvurderingerTilKlage.Ferdig(
                    vedtakId = vedtakId!!,
                    innenforFristen = innenforFristen!!,
                    klagesDetPåKonkreteElementerIVedtaket = klagesDetPåKonkreteElementerIVedtaket!!,
                    erUnderskrevet = erUnderskrevet!!,
                    begrunnelse = begrunnelse!!,
                ),
            )
        }
    }

    private enum class Typer(val verdi: String) {
        OPPRETTET("opprettet"),
        VILKÅRSVURDERT_PÅBEGYNT("vilkårsvurdert_påbegynt"),
        VILKÅRSVURDERT_FERDIG("vilkårsvurdert_ferdig");

        companion object {
            fun Klage.databasetype(): String {
                return when (this) {
                    is OpprettetKlage -> OPPRETTET
                    is VilkårsvurdertKlage.Påbegynt -> VILKÅRSVURDERT_PÅBEGYNT
                    is VilkårsvurdertKlage.Ferdig -> VILKÅRSVURDERT_FERDIG
                }.toString()
            }

            fun fromString(value: String): Typer {
                return values().find { it.verdi == value }
                    ?: throw IllegalStateException("Ukjent typeverdi i klage-tabellen: $value")
            }
        }

        override fun toString() = verdi
    }
}
