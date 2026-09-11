package no.nav.su.se.bakover.database.statistikk

import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregat
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregatstatus
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkVisningsrad
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkgrunnlag
import no.nav.su.se.bakover.domain.statistikk.StatistikkVisningRepo
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkAggregat
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkAggregatstatus
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkAggregertRad
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkBestandsendringRad
import statistikk.domain.StønadsklassifiseringDto
import statistikk.domain.StønadstatistikkDto
import java.time.YearMonth
import java.util.UUID

class StatistikkVisningPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : StatistikkVisningRepo {

    override fun hentEllerOpprettSakstatistikkAggregat(måned: YearMonth): SakStatistikkAggregat {
        return dbMetrics.timeQuery("hentEllerOpprettSakstatistikkAggregat") {
            sessionFactory.withSession { session ->
                """
                    INSERT INTO sak_statistikk_aggregat (
                        id, maaned, status, opprettet
                    ) VALUES (
                        :id, :maaned, 'VENTER', NOW()
                    )
                    ON CONFLICT (maaned) DO NOTHING
                """.trimIndent().insert(
                    params = mapOf(
                        "id" to UUID.randomUUID(),
                        "maaned" to måned.atDay(1),
                    ),
                    session = session,
                )
                hentAggregat(måned, session)
                    ?: throw IllegalStateException("Fant ikke sakstatistikkaggregat etter opprettelse")
            }
        }
    }

    override fun markerSakstatistikkAggregatForRegenerering(id: UUID) {
        dbMetrics.timeQuery("markerSakstatistikkAggregatForRegenerering") {
            sessionFactory.withSession { session ->
                """
                    UPDATE sak_statistikk_aggregat
                    SET status = 'VENTER',
                        startet = NULL,
                        ferdig = NULL,
                        grunnlag = NULL,
                        feilmelding = NULL
                    WHERE id = :id
                      AND status <> 'PÅGÅR'
                """.trimIndent().oppdatering(
                    params = mapOf("id" to id),
                    session = session,
                )
            }
        }
    }

    override fun hentNesteSakstatistikkAggregatTilGenerering(
        aggregatId: UUID?,
    ): SakStatistikkAggregat? {
        return dbMetrics.timeQuery("hentNesteSakstatistikkAggregatTilGenerering") {
            sessionFactory.withTransaction { tx ->
                val aggregatIdFilter = if (aggregatId == null) "" else "AND id = :aggregat_id"
                """
                    WITH kandidat AS (
                        SELECT id
                        FROM sak_statistikk_aggregat
                        WHERE (
                            status IN ('VENTER', 'FEILET')
                            OR (status = 'PÅGÅR' AND startet < NOW() - INTERVAL '30 minutes')
                        )
                          $aggregatIdFilter
                        ORDER BY opprettet
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                    )
                    UPDATE sak_statistikk_aggregat aggregat
                    SET status = 'PÅGÅR',
                        startet = NOW(),
                        ferdig = NULL,
                        feilmelding = NULL
                    FROM kandidat
                    WHERE aggregat.id = kandidat.id
                    RETURNING aggregat.*
                """.trimIndent().hent(
                    params = if (aggregatId == null) {
                        emptyMap()
                    } else {
                        mapOf("aggregat_id" to aggregatId)
                    },
                    session = tx,
                ) { it.toAggregat() }
            }
        }
    }

    override fun hentSakstatistikkgrunnlag(
        måned: YearMonth,
        maksSekvensId: Long?,
    ): List<SakStatistikkVisningsrad> {
        return dbMetrics.timeQuery("hentSakstatistikkgrunnlagForVisning") {
            sessionFactory.withSession { session ->
                buildString {
                    append(
                        """
                    WITH siste_status AS (
                        SELECT DISTINCT ON (behandling_id)
                            behandling_id,
                            behandling_status
                        FROM sak_statistikk
                        WHERE funksjonell_tid < :til_eksklusiv
                        """.trimIndent(),
                    )
                    if (maksSekvensId != null) append("\n  AND id_sekvens <= :maks_sekvens_id")
                    append(
                        """
                        
                        ORDER BY behandling_id, id_sekvens DESC
                    ),
                    relevante_behandlinger AS (
                        SELECT DISTINCT behandling_id
                        FROM sak_statistikk
                        WHERE funksjonell_tid >= :fra_og_med
                          AND funksjonell_tid < :til_eksklusiv
                        """.trimIndent(),
                    )
                    if (maksSekvensId != null) append("\n  AND id_sekvens <= :maks_sekvens_id")
                    append(
                        """

                        UNION

                        SELECT behandling_id
                        FROM siste_status
                        WHERE behandling_status NOT IN ('IVERKSATT', 'AVSLUTTET', 'AVBRUTT', 'OVERSENDT')
                    )
                    SELECT
                        ss.id_sekvens,
                        ss.behandling_id,
                        ss.sak_ytelse,
                        ss.behandling_type,
                        ss.behandling_aarsak,
                        ss.behandling_status,
                        ss.behandling_resultat,
                        ss.behandling_begrunnelse,
                        ss.mottatt_tid,
                        ss.registrert_tid,
                        ss.funksjonell_tid,
                        ss.teknisk_tid,
                        r.revurderingstype
                    FROM sak_statistikk ss
                    JOIN relevante_behandlinger rb USING (behandling_id)
                    LEFT JOIN revurdering r ON r.id = ss.behandling_id
                    WHERE ss.funksjonell_tid < :til_eksklusiv
                        """.trimIndent(),
                    )
                    if (maksSekvensId != null) append("\n  AND ss.id_sekvens <= :maks_sekvens_id")
                    append(
                        """
                        
                    ORDER BY ss.behandling_id, ss.id_sekvens
                        """.trimIndent(),
                    )
                }.hentListe(
                    params = buildMap<String, Any> {
                        put("fra_og_med", måned.atDay(1))
                        put("til_eksklusiv", måned.plusMonths(1).atDay(1))
                        if (maksSekvensId != null) put("maks_sekvens_id", maksSekvensId)
                    },
                    session = session,
                ) { it.toVisningsrad() }
            }
        }
    }

    override fun hentMaksSakstatistikkSekvensId(måned: YearMonth): Long? {
        return dbMetrics.timeQuery("hentMaksSakstatistikkSekvensId") {
            sessionFactory.withSession { session ->
                """
                    SELECT max(id_sekvens) AS maks_sekvens_id
                    FROM sak_statistikk
                    WHERE funksjonell_tid < :til_eksklusiv
                """.trimIndent().hent(
                    params = mapOf("til_eksklusiv" to måned.plusMonths(1).atDay(1)),
                    session = session,
                ) { it.longOrNull("maks_sekvens_id") }
            }
        }
    }

    override fun ferdigstillSakstatistikkAggregat(
        id: UUID,
        startet: java.time.Instant,
        grunnlag: SakStatistikkgrunnlag,
        maksSekvensId: Long?,
    ) {
        dbMetrics.timeQuery("ferdigstillSakstatistikkAggregat") {
            sessionFactory.withSession { session ->
                """
                    UPDATE sak_statistikk_aggregat
                    SET status = 'FERDIG',
                        maks_sekvens_id = :maks_sekvens_id,
                        ferdig = NOW(),
                        grunnlag = to_jsonb(:grunnlag::jsonb),
                        feilmelding = NULL
                    WHERE id = :id
                      AND status = 'PÅGÅR'
                      AND startet = :startet
                """.trimIndent().oppdatering(
                    params = mapOf(
                        "id" to id,
                        "startet" to startet,
                        "maks_sekvens_id" to maksSekvensId,
                        "grunnlag" to serialize(grunnlag),
                    ),
                    session = session,
                )
            }
        }
    }

    override fun markerSakstatistikkAggregatFeilet(id: UUID, startet: java.time.Instant, feilmelding: String?) {
        dbMetrics.timeQuery("markerSakstatistikkAggregatFeilet") {
            sessionFactory.withSession { session ->
                """
                    UPDATE sak_statistikk_aggregat
                    SET status = 'FEILET',
                        ferdig = NOW(),
                        feilmelding = :feilmelding
                    WHERE id = :id
                      AND status = 'PÅGÅR'
                      AND startet = :startet
                """.trimIndent().oppdatering(
                    params = mapOf(
                        "id" to id,
                        "startet" to startet,
                        "feilmelding" to feilmelding?.take(1000),
                    ),
                    session = session,
                )
            }
        }
    }

    override fun hentStønadstatistikk(måned: YearMonth): List<StønadStatistikkAggregertRad> {
        return dbMetrics.timeQuery("hentStønadstatistikkvisning") {
            sessionFactory.withSession { session ->
                """
                    WITH siste_per_sak_og_maaned AS (
                        SELECT DISTINCT ON (sak_id, maaned)
                            maaned,
                            stonadstype,
                            vedtakstype,
                            vedtaksresultat,
                            stonadsklassifisering
                        FROM stoenad_maaned_statistikk
                        WHERE maaned = :maaned
                        ORDER BY sak_id, maaned, teknisk_tid DESC, id DESC
                    )
                    SELECT
                        maaned,
                        stonadstype,
                        vedtakstype,
                        vedtaksresultat,
                        stonadsklassifisering,
                        count(*) AS antall
                    FROM siste_per_sak_og_maaned
                    GROUP BY maaned, stonadstype, vedtakstype, vedtaksresultat, stonadsklassifisering
                    ORDER BY maaned, stonadstype, vedtakstype, vedtaksresultat, stonadsklassifisering
                """.trimIndent().hentListe(
                    params = mapOf("maaned" to måned.atDay(1)),
                    session = session,
                ) {
                    StønadStatistikkAggregertRad(
                        måned = YearMonth.from(it.localDate("maaned")),
                        stønadstype = StønadstatistikkDto.Stønadstype.valueOf(it.string("stonadstype")),
                        vedtakstype = StønadstatistikkDto.Vedtakstype.valueOf(it.string("vedtakstype")),
                        vedtaksresultat = StønadstatistikkDto.Vedtaksresultat.valueOf(it.string("vedtaksresultat")),
                        stønadsklassifisering = it.stringOrNull("stonadsklassifisering")
                            ?.let(StønadsklassifiseringDto::valueOf),
                        antall = it.int("antall"),
                    )
                }
            }
        }
    }

    override fun hentStønadstatistikkBestandsendringer(
        måned: YearMonth,
    ): List<StønadStatistikkBestandsendringRad> {
        return dbMetrics.timeQuery("hentStønadstatistikkBestandsendringer") {
            sessionFactory.withSession { session ->
                """
                    WITH siste_per_sak_og_maaned AS (
                        SELECT DISTINCT ON (sak_id, maaned)
                            sak_id,
                            maaned,
                            stonadstype,
                            stonadsklassifisering
                        FROM stoenad_maaned_statistikk
                        WHERE maaned IN (:forrige_maaned, :maaned)
                        ORDER BY sak_id, maaned, teknisk_tid DESC, id DESC
                    ),
                    gjeldende AS (
                        SELECT * FROM siste_per_sak_og_maaned WHERE maaned = :maaned
                    ),
                    forrige AS (
                        SELECT * FROM siste_per_sak_og_maaned WHERE maaned = :forrige_maaned
                    )
                    SELECT
                        CAST(:maaned AS date) AS maaned,
                        COALESCE(gjeldende.stonadstype, forrige.stonadstype) AS stonadstype,
                        count(*) FILTER (
                            WHERE gjeldende.sak_id IS NOT NULL
                              AND forrige.sak_id IS NULL
                        ) AS nye,
                        count(*) FILTER (
                            WHERE gjeldende.sak_id IS NOT NULL
                              AND forrige.sak_id IS NOT NULL
                        ) AS viderefort,
                        count(*) FILTER (
                            WHERE gjeldende.sak_id IS NULL
                              AND forrige.sak_id IS NOT NULL
                        ) AS utgaatt,
                        count(*) FILTER (
                            WHERE gjeldende.sak_id IS NOT NULL
                              AND forrige.sak_id IS NOT NULL
                              AND gjeldende.stonadsklassifisering
                                  IS DISTINCT FROM forrige.stonadsklassifisering
                        ) AS endret_stonadsklassifisering
                    FROM gjeldende
                    FULL OUTER JOIN forrige USING (sak_id)
                    GROUP BY COALESCE(gjeldende.stonadstype, forrige.stonadstype)
                    ORDER BY stonadstype
                """.trimIndent().hentListe(
                    params = mapOf(
                        "forrige_maaned" to måned.minusMonths(1).atDay(1),
                        "maaned" to måned.atDay(1),
                    ),
                    session = session,
                ) {
                    StønadStatistikkBestandsendringRad(
                        måned = YearMonth.from(it.localDate("maaned")),
                        stønadstype = StønadstatistikkDto.Stønadstype.valueOf(it.string("stonadstype")),
                        nye = it.int("nye"),
                        videreført = it.int("viderefort"),
                        utgått = it.int("utgaatt"),
                        endretStønadsklassifisering = it.int("endret_stonadsklassifisering"),
                    )
                }
            }
        }
    }

    override fun hentStønadstatistikkAggregater(
        fraOgMed: YearMonth,
        tilOgMed: YearMonth,
    ): List<StønadStatistikkAggregat> {
        return dbMetrics.timeQuery("hentStønadstatistikkAggregater") {
            sessionFactory.withSession { session ->
                """
                    SELECT *
                    FROM stoenad_statistikk_aggregat
                    WHERE maaned >= :fra_og_med
                      AND maaned <= :til_og_med
                    ORDER BY maaned
                """.trimIndent().hentListe(
                    params = mapOf(
                        "fra_og_med" to fraOgMed.atDay(1),
                        "til_og_med" to tilOgMed.atEndOfMonth(),
                    ),
                    session = session,
                ) { it.toStønadstatistikkAggregat() }
            }
        }
    }

    override fun hentNesteStønadstatistikkAggregatTilGenerering(
        aggregatId: UUID?,
    ): StønadStatistikkAggregat? {
        return dbMetrics.timeQuery("hentNesteStønadstatistikkAggregatTilGenerering") {
            sessionFactory.withTransaction { tx ->
                val aggregatIdFilter = if (aggregatId == null) "" else "AND id = :aggregat_id"
                """
                    WITH kandidat AS (
                        SELECT id
                        FROM stoenad_statistikk_aggregat
                        WHERE (
                            status IN ('VENTER', 'FEILET')
                            OR (status = 'PÅGÅR' AND startet < NOW() - INTERVAL '30 minutes')
                        )
                          $aggregatIdFilter
                        ORDER BY maaned
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                    )
                    UPDATE stoenad_statistikk_aggregat aggregat
                    SET status = 'PÅGÅR',
                        startet = NOW(),
                        feilmelding = NULL
                    FROM kandidat
                    WHERE aggregat.id = kandidat.id
                    RETURNING aggregat.*
                """.trimIndent().hent(
                    params = if (aggregatId == null) emptyMap() else mapOf("aggregat_id" to aggregatId),
                    session = tx,
                ) { it.toStønadstatistikkAggregat() }
            }
        }
    }

    override fun ferdigstillStønadstatistikkAggregat(
        id: UUID,
        startet: java.time.Instant,
        payloadJson: String,
    ) {
        dbMetrics.timeQuery("ferdigstillStønadstatistikkAggregat") {
            sessionFactory.withSession { session ->
                """
                    UPDATE stoenad_statistikk_aggregat
                    SET status = 'FERDIG',
                        payload = to_jsonb(:payload::jsonb),
                        feilmelding = NULL
                    WHERE id = :id
                      AND status = 'PÅGÅR'
                      AND startet = :startet
                """.trimIndent().oppdatering(
                    params = mapOf(
                        "id" to id,
                        "startet" to startet,
                        "payload" to payloadJson,
                    ),
                    session = session,
                )
            }
        }
    }

    override fun markerStønadstatistikkAggregatFeilet(
        id: UUID,
        startet: java.time.Instant,
        feilmelding: String?,
    ) {
        dbMetrics.timeQuery("markerStønadstatistikkAggregatFeilet") {
            sessionFactory.withSession { session ->
                """
                    UPDATE stoenad_statistikk_aggregat
                    SET status = 'FEILET',
                        feilmelding = :feilmelding
                    WHERE id = :id
                      AND status = 'PÅGÅR'
                      AND startet = :startet
                """.trimIndent().oppdatering(
                    params = mapOf(
                        "id" to id,
                        "startet" to startet,
                        "feilmelding" to feilmelding?.take(1000),
                    ),
                    session = session,
                )
            }
        }
    }

    private fun hentAggregat(
        måned: YearMonth,
        session: Session,
    ): SakStatistikkAggregat? {
        return """
            SELECT *
            FROM sak_statistikk_aggregat
            WHERE maaned = :maaned
        """.trimIndent().hent(
            params = mapOf("maaned" to måned.atDay(1)),
            session = session,
        ) { it.toAggregat() }
    }

    private fun Row.toAggregat() = SakStatistikkAggregat(
        id = uuid("id"),
        måned = YearMonth.from(localDate("maaned")),
        status = SakStatistikkAggregatstatus.valueOf(string("status")),
        maksSekvensId = longOrNull("maks_sekvens_id"),
        opprettet = instant("opprettet"),
        startet = instantOrNull("startet"),
        ferdig = instantOrNull("ferdig"),
        grunnlag = stringOrNull("grunnlag")?.let { deserialize<SakStatistikkgrunnlag>(it) },
    )

    private fun Row.toStønadstatistikkAggregat() = StønadStatistikkAggregat(
        id = uuid("id"),
        måned = YearMonth.from(localDate("maaned")),
        status = StønadStatistikkAggregatstatus.valueOf(string("status")),
        startet = instantOrNull("startet"),
        payloadJson = stringOrNull("payload"),
    )

    private fun Row.toVisningsrad() = SakStatistikkVisningsrad(
        sekvensId = long("id_sekvens"),
        behandlingId = uuid("behandling_id"),
        sakYtelse = string("sak_ytelse"),
        behandlingType = string("behandling_type"),
        behandlingAarsak = stringOrNull("behandling_aarsak"),
        behandlingStatus = string("behandling_status"),
        behandlingResultat = stringOrNull("behandling_resultat"),
        resultatBegrunnelse = stringOrNull("behandling_begrunnelse"),
        mottattTid = tidspunkt("mottatt_tid"),
        registrertTid = tidspunkt("registrert_tid"),
        funksjonellTid = tidspunkt("funksjonell_tid"),
        tekniskTid = tidspunkt("teknisk_tid"),
        revurderingstype = stringOrNull("revurderingstype"),
    )
}
