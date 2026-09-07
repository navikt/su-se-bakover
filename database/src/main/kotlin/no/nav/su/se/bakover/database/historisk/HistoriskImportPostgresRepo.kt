package no.nav.su.se.bakover.database.historisk

import kotliquery.Row
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunktOrNull
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.historisk.HistoriskImport
import no.nav.su.se.bakover.domain.historisk.HistoriskImportOversikt
import no.nav.su.se.bakover.domain.historisk.HistoriskImportRepo
import no.nav.su.se.bakover.domain.historisk.HistoriskImportTabellOversikt
import no.nav.su.se.bakover.domain.historisk.HistoriskRådataSide
import no.nav.su.se.bakover.domain.historisk.NyHistoriskTabellimport
import no.nav.su.se.bakover.domain.historisk.SlettImportResultat
import org.slf4j.LoggerFactory
import java.util.UUID

class HistoriskImportPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : HistoriskImportRepo {

    private val log = LoggerFactory.getLogger(this::class.java)

    private companion object {
        const val SLETT_BATCH_SIZE = 10_000
        const val LOGG_ETTER_ANTALL_SLETTEDE = 100_000
    }

    override fun hentPågåendeImport(): HistoriskImport? {
        return dbMetrics.timeQuery("hentPågåendeHistoriskImport") {
            sessionFactory.withSession { session ->
                """
                    SELECT id, status, opprettet
                    FROM historisk_import
                    WHERE status = '${HistoriskImport.Status.PÅGÅR.name}'
                """.trimIndent().hent(session = session) {
                    hentImport(it.uuid("id"), HistoriskImport.Status.valueOf(it.string("status")), it.tidspunkt("opprettet"), session)
                }
            }
        }
    }

    override fun opprettImport(tabeller: List<NyHistoriskTabellimport>): HistoriskImport {
        require(tabeller.isNotEmpty()) { "En historisk import må inneholde minst én tabell" }
        require(tabeller.map { it.tabellnavn }.distinct().size == tabeller.size) {
            "En historisk import kan ikke inneholde duplikate tabellnavn"
        }

        return dbMetrics.timeQuery("opprettHistoriskImport") {
            sessionFactory.withTransaction { tx ->
                val importId = UUID.randomUUID()
                val opprettet = """
                    INSERT INTO historisk_import (id, status)
                    VALUES (:id, :status)
                    RETURNING opprettet
                """.trimIndent().hent(
                    mapOf(
                        "id" to importId,
                        "status" to HistoriskImport.Status.PÅGÅR.name,
                    ),
                    tx,
                ) { it.tidspunkt("opprettet") }!!

                tabeller.forEach { tabell ->
                    """
                        INSERT INTO historisk_import_tabell (
                            import_id,
                            tabellnavn,
                            status,
                            forventet_antall,
                            kolonner
                        ) VALUES (
                            :import_id,
                            :tabellnavn,
                            :status,
                            :forventet_antall,
                            to_jsonb(:kolonner::jsonb)
                        )
                    """.trimIndent().insert(
                        mapOf(
                            "import_id" to importId,
                            "tabellnavn" to tabell.tabellnavn,
                            "status" to if (tabell.forventetAntall == 0L) {
                                HistoriskImport.Status.FULLFØRT.name
                            } else {
                                HistoriskImport.Status.PÅGÅR.name
                            },
                            "forventet_antall" to tabell.forventetAntall,
                            "kolonner" to serialize(tabell.kolonner),
                        ),
                        tx,
                    )
                }
                hentImport(importId, HistoriskImport.Status.PÅGÅR, opprettet, tx)
            }
        }
    }

    override fun lagreSide(side: HistoriskRådataSide): HistoriskImport.Tabell {
        return dbMetrics.timeQuery("lagreHistoriskImportside") {
            sessionFactory.withTransaction { tx ->
                val tabell = hentTabellForOppdatering(side.importId, side.tabellnavn, tx)
                    ?: throw IllegalArgumentException(
                        "Fant ikke tabell ${side.tabellnavn} for historisk import ${side.importId}",
                    )
                require(tabell.status == HistoriskImport.Status.PÅGÅR) {
                    "Kan ikke lagre side for ${side.tabellnavn} med status ${tabell.status}"
                }
                require(tabell.nesteSide == side.side) {
                    "Forventet side ${tabell.nesteSide} for ${side.tabellnavn}, men mottok ${side.side}"
                }

                val nyttAntall = tabell.importertAntall + side.rader.size
                require(nyttAntall <= tabell.forventetAntall) {
                    "Importert antall $nyttAntall overstiger forventet antall ${tabell.forventetAntall} " +
                        "for ${side.tabellnavn}"
                }
                val harRader = side.rader.isNotEmpty()
                val harNesteIterator = !side.nesteIterator.isNullOrBlank()
                // Kilden kan returnere samme iterator på den første tomme siden etter siste dataside.
                // Blank iterator beholdes også som slutt-signal i tråd med den dokumenterte API-kontrakten.
                val skalFortsette = harRader && harNesteIterator
                val erSisteSide = !skalFortsette
                require(!erSisteSide || nyttAntall == tabell.forventetAntall) {
                    "Siste side for ${side.tabellnavn} ga $nyttAntall rader, forventet ${tabell.forventetAntall}"
                }
                val iteratorForNesteSide = side.nesteIterator?.takeIf { skalFortsette }

                if (side.rader.isNotEmpty()) {
                    """
                        INSERT INTO historisk_import_rad (
                            import_id,
                            tabellnavn,
                            side,
                            radnummer,
                            data
                        )
                        SELECT
                            :import_id,
                            :tabellnavn,
                            :side,
                            (radnummer - 1)::integer,
                            data
                        FROM jsonb_array_elements(:rader::jsonb) WITH ORDINALITY AS rad(data, radnummer)
                        ON CONFLICT (import_id, tabellnavn, side, radnummer) DO NOTHING
                    """.trimIndent().insert(
                        mapOf(
                            "import_id" to side.importId,
                            "tabellnavn" to side.tabellnavn,
                            "side" to side.side,
                            "rader" to serialize(side.rader),
                        ),
                        tx,
                    )
                }

                val oppdaterteRader = """
                    UPDATE historisk_import_tabell
                    SET status = :status,
                        importert_antall = :importert_antall,
                        neste_iterator = :neste_iterator,
                        neste_side = neste_side + 1
                    WHERE import_id = :import_id
                      AND tabellnavn = :tabellnavn
                      AND status = '${HistoriskImport.Status.PÅGÅR.name}'
                """.trimIndent().oppdatering(
                    mapOf(
                        "status" to if (erSisteSide) {
                            HistoriskImport.Status.FULLFØRT.name
                        } else {
                            HistoriskImport.Status.PÅGÅR.name
                        },
                        "importert_antall" to nyttAntall,
                        "neste_iterator" to iteratorForNesteSide,
                        "import_id" to side.importId,
                        "tabellnavn" to side.tabellnavn,
                        "side" to side.side,
                    ),
                    tx,
                )
                check(oppdaterteRader == 1) {
                    "Checkpoint for ${side.tabellnavn} side ${side.side} ble ikke oppdatert"
                }

                tabell.copy(
                    status = if (erSisteSide) {
                        HistoriskImport.Status.FULLFØRT
                    } else {
                        HistoriskImport.Status.PÅGÅR
                    },
                    importertAntall = nyttAntall,
                    nesteIterator = iteratorForNesteSide,
                    nesteSide = side.side + 1,
                )
            }
        }
    }

    override fun fullførImport(importId: UUID) {
        dbMetrics.timeQuery("fullførHistoriskImport") {
            sessionFactory.withTransaction { tx ->
                val oppdaterteRader = """
                    UPDATE historisk_import
                    SET status = '${HistoriskImport.Status.FULLFØRT.name}',
                        fullført = NOW(),
                        feilbeskrivelse = NULL
                    WHERE id = :id
                      AND status = '${HistoriskImport.Status.PÅGÅR.name}'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM historisk_import_tabell
                          WHERE import_id = :id
                            AND status <> '${HistoriskImport.Status.FULLFØRT.name}'
                      )
                """.trimIndent().oppdatering(mapOf("id" to importId), tx)
                check(oppdaterteRader == 1) {
                    "Historisk import $importId kunne ikke fullføres fordi én eller flere tabeller ikke er fullført"
                }
            }
        }
    }

    override fun markerFeilet(importId: UUID, beskrivelse: String) {
        dbMetrics.timeQuery("markerHistoriskImportFeilet") {
            sessionFactory.withTransaction { tx ->
                """
                    UPDATE historisk_import_tabell
                    SET status = '${HistoriskImport.Status.FEILET.name}'
                    WHERE import_id = :id
                      AND status = '${HistoriskImport.Status.PÅGÅR.name}'
                """.trimIndent().oppdatering(mapOf("id" to importId), tx)
                """
                    UPDATE historisk_import
                    SET status = '${HistoriskImport.Status.FEILET.name}',
                        feilbeskrivelse = :feilbeskrivelse
                    WHERE id = :id
                      AND status = '${HistoriskImport.Status.PÅGÅR.name}'
                """.trimIndent().oppdatering(
                    mapOf(
                        "id" to importId,
                        "feilbeskrivelse" to beskrivelse.take(1000),
                    ),
                    tx,
                )
            }
        }
    }

    override fun hentAlleImporter(): List<HistoriskImportOversikt> {
        return dbMetrics.timeQuery("hentAlleHistoriskeImporter") {
            sessionFactory.withSession { session ->
                val importer = """
                    SELECT id, status, opprettet, fullført, feilbeskrivelse
                    FROM historisk_import
                    WHERE status IN (
                        '${HistoriskImport.Status.FULLFØRT.name}',
                        '${HistoriskImport.Status.FEILET.name}'
                    )
                    ORDER BY opprettet DESC
                """.trimIndent().hentListe(emptyMap(), session) { row ->
                    HistoriskImportOversikt(
                        id = row.uuid("id"),
                        status = HistoriskImport.Status.valueOf(row.string("status")),
                        opprettet = row.tidspunkt("opprettet"),
                        fullført = row.tidspunktOrNull("fullført"),
                        feilbeskrivelse = row.stringOrNull("feilbeskrivelse"),
                        tabeller = emptyList(),
                    )
                }

                if (importer.isEmpty()) return@withSession emptyList()

                val importIds = importer.map { it.id }.toSet()
                val tabellerPerImport = """
                    SELECT import_id, tabellnavn, status, forventet_antall, importert_antall
                    FROM historisk_import_tabell
                    WHERE import_id = ANY(:ids)
                    ORDER BY tabellnavn
                """.trimIndent().hentListe(mapOf("ids" to session.connection.underlying.createArrayOf("uuid", importIds.toTypedArray())), session) { row ->
                    row.uuid("import_id") to HistoriskImportTabellOversikt(
                        tabellnavn = row.string("tabellnavn"),
                        status = HistoriskImport.Status.valueOf(row.string("status")),
                        forventetAntall = row.long("forventet_antall"),
                        importertAntall = row.long("importert_antall"),
                    )
                }.groupBy({ it.first }, { it.second })

                importer.map { it.copy(tabeller = tabellerPerImport[it.id] ?: emptyList()) }
            }
        }
    }

    override fun slettImport(importId: UUID): SlettImportResultat {
        return dbMetrics.timeQuery("slettHistoriskImport") {
            val import = sessionFactory.withTransaction { tx ->
                val importstatus = """
                    SELECT status
                    FROM historisk_import
                    WHERE id = :id
                    FOR UPDATE
                """.trimIndent().hent(mapOf("id" to importId), tx) {
                    HistoriskImport.Status.valueOf(it.string("status"))
                } ?: return@withTransaction null

                if (importstatus == HistoriskImport.Status.PÅGÅR) {
                    return@withTransaction importstatus to emptyList<Pair<String, Long>>()
                }

                val tabeller = """
                    SELECT tabellnavn, importert_antall
                    FROM historisk_import_tabell
                    WHERE import_id = :id
                """.trimIndent().hentListe(mapOf("id" to importId), tx) {
                    it.string("tabellnavn") to it.long("importert_antall")
                }
                importstatus to tabeller
            } ?: return@timeQuery SlettImportResultat.IKKE_FUNNET

            if (import.first == HistoriskImport.Status.PÅGÅR) {
                return@timeQuery SlettImportResultat.PÅGÅR
            }

            val totaltAntallRådataRader = import.second.sumOf { it.second }
            val tabellnavn = import.second.map { it.first }
            var totaltSlettedeRådataRader = 0L
            var antallVedSisteLogg = -1L
            var slettedeRådataRader: Int
            do {
                slettedeRådataRader = sessionFactory.withTransaction { tx ->
                    """
                        DELETE FROM historisk_import_rad
                        WHERE ctid IN (
                            SELECT ctid
                            FROM historisk_import_rad
                            WHERE import_id = :id
                            LIMIT $SLETT_BATCH_SIZE
                        )
                    """.trimIndent().oppdatering(mapOf("id" to importId), tx)
                }
                totaltSlettedeRådataRader += slettedeRådataRader
                val harSlettetSidenSisteLogg = totaltSlettedeRådataRader != antallVedSisteLogg
                val harNåddLoggepunkt =
                    totaltSlettedeRådataRader % LOGG_ETTER_ANTALL_SLETTEDE == 0L ||
                        slettedeRådataRader < SLETT_BATCH_SIZE
                val skalLoggeFremdrift = harSlettetSidenSisteLogg && harNåddLoggepunkt
                if (skalLoggeFremdrift) {
                    log.info(
                        "Sletter historisk import {}: {} rådata-rader slettet, omtrent {} gjenstår",
                        importId,
                        totaltSlettedeRådataRader,
                        (totaltAntallRådataRader - totaltSlettedeRådataRader).coerceAtLeast(0),
                    )
                    antallVedSisteLogg = totaltSlettedeRådataRader
                }
            } while (slettedeRådataRader == SLETT_BATCH_SIZE)

            tabellnavn.forEach { navn ->
                sessionFactory.withTransaction { tx ->
                    """
                        DELETE FROM historisk_import_tabell
                        WHERE import_id = :id
                          AND tabellnavn = :tabellnavn
                    """.trimIndent().oppdatering(
                        mapOf(
                            "id" to importId,
                            "tabellnavn" to navn,
                        ),
                        tx,
                    )
                }
            }

            sessionFactory.withTransaction { tx ->
                val slettedeRader = """
                    DELETE FROM historisk_import WHERE id = :id
                """.trimIndent().oppdatering(mapOf("id" to importId), tx)
                check(slettedeRader == 1) {
                    "Historisk import $importId ble ikke slettet"
                }
            }
            SlettImportResultat.SLETTET
        }
    }

    private fun hentImport(
        importId: UUID,
        status: HistoriskImport.Status,
        opprettet: no.nav.su.se.bakover.common.tid.Tidspunkt,
        session: Session,
    ): HistoriskImport {
        return HistoriskImport(
            id = importId,
            status = status,
            opprettet = opprettet,
            tabeller = """
                SELECT *
                FROM historisk_import_tabell
                WHERE import_id = :import_id
                ORDER BY tabellnavn
            """.trimIndent().hentListe(mapOf("import_id" to importId), session) { it.toHistoriskImportTabell() },
        )
    }

    private fun hentTabellForOppdatering(
        importId: UUID,
        tabellnavn: String,
        session: Session,
    ): HistoriskImport.Tabell? {
        return """
            SELECT *
            FROM historisk_import_tabell
            WHERE import_id = :import_id
              AND tabellnavn = :tabellnavn
            FOR UPDATE
        """.trimIndent().hent(
            mapOf(
                "import_id" to importId,
                "tabellnavn" to tabellnavn,
            ),
            session,
        ) { it.toHistoriskImportTabell() }
    }
}

private fun Row.toHistoriskImportTabell() = HistoriskImport.Tabell(
    tabellnavn = string("tabellnavn"),
    status = HistoriskImport.Status.valueOf(string("status")),
    forventetAntall = long("forventet_antall"),
    importertAntall = long("importert_antall"),
    nesteIterator = stringOrNull("neste_iterator"),
    nesteSide = long("neste_side"),
    kolonner = string("kolonner").deserializeList(),
)
