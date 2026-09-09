package no.nav.su.se.bakover.database.statistikk

import kotliquery.Row
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregat
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregatnøkkel
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkAggregatstatus
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkVisningsrad
import no.nav.su.se.bakover.domain.statistikk.StatistikkVisningRepo
import no.nav.su.se.bakover.domain.statistikk.StønadStatistikkAggregertRad
import statistikk.domain.StønadsklassifiseringDto
import statistikk.domain.StønadstatistikkDto
import java.time.YearMonth
import java.util.UUID

class StatistikkVisningPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : StatistikkVisningRepo {

    override fun hentEllerOpprettSakstatistikkAggregat(
        nøkkel: SakStatistikkAggregatnøkkel,
        versjon: Int,
    ): SakStatistikkAggregat {
        return dbMetrics.timeQuery("hentEllerOpprettSakstatistikkAggregat") {
            sessionFactory.withSession { session ->
                """
                    INSERT INTO sak_statistikk_aggregat (
                        id, fra_og_med, til_og_med, opplosning, status, versjon, opprettet
                    ) VALUES (
                        :id, :fra_og_med, :til_og_med, :opplosning, 'VENTER', :versjon, NOW()
                    )
                    ON CONFLICT (fra_og_med, til_og_med, opplosning) DO NOTHING
                """.trimIndent().insert(
                    params = mapOf(
                        "id" to UUID.randomUUID(),
                        "fra_og_med" to nøkkel.fraOgMed,
                        "til_og_med" to nøkkel.tilOgMed,
                        "opplosning" to nøkkel.oppløsning.name,
                        "versjon" to versjon,
                    ),
                    session = session,
                )
                hentAggregat(nøkkel, session)
                    ?: throw IllegalStateException("Fant ikke sakstatistikkaggregat etter opprettelse")
            }
        }
    }

    override fun markerSakstatistikkAggregatForRegenerering(id: UUID, versjon: Int) {
        dbMetrics.timeQuery("markerSakstatistikkAggregatForRegenerering") {
            sessionFactory.withSession { session ->
                """
                    UPDATE sak_statistikk_aggregat
                    SET status = 'VENTER',
                        versjon = :versjon,
                        startet = NULL,
                        ferdig = NULL,
                        payload = NULL,
                        feilmelding = NULL
                    WHERE id = :id
                      AND status <> 'PÅGÅR'
                """.trimIndent().oppdatering(
                    params = mapOf("id" to id, "versjon" to versjon),
                    session = session,
                )
            }
        }
    }

    override fun hentNesteSakstatistikkAggregatTilGenerering(bareId: UUID?): SakStatistikkAggregat? {
        return dbMetrics.timeQuery("hentNesteSakstatistikkAggregatTilGenerering") {
            sessionFactory.withTransaction { tx ->
                buildString {
                    append(
                        """
                    WITH kandidat AS (
                        SELECT id
                        FROM sak_statistikk_aggregat
                        WHERE (
                            status IN ('VENTER', 'FEILET')
                            OR (status = 'PÅGÅR' AND startet < NOW() - INTERVAL '30 minutes')
                        )
                        """.trimIndent(),
                    )
                    if (bareId != null) append("\n  AND id = :bare_id")
                    append(
                        """
                        
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
                        """.trimIndent(),
                    )
                }.hent(
                    params = if (bareId == null) emptyMap() else mapOf("bare_id" to bareId),
                    session = tx,
                ) { it.toAggregat() }
            }
        }
    }

    override fun hentSakstatistikkgrunnlag(
        nøkkel: SakStatistikkAggregatnøkkel,
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
                        WHERE behandling_status NOT IN ('IVERKSATT', 'AVSLUTTET', 'AVBRUTT')
                    )
                    SELECT
                        ss.id_sekvens,
                        ss.behandling_id,
                        ss.sak_ytelse,
                        ss.behandling_type,
                        ss.behandling_metode,
                        ss.behandling_aarsak,
                        ss.behandling_status,
                        ss.behandling_resultat,
                        ss.funksjonell_tid,
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
                        putAll(nøkkel.periodeparametre())
                        if (maksSekvensId != null) put("maks_sekvens_id", maksSekvensId)
                    },
                    session = session,
                ) { it.toVisningsrad() }
            }
        }
    }

    override fun hentMaksSakstatistikkSekvensId(nøkkel: SakStatistikkAggregatnøkkel): Long? {
        return dbMetrics.timeQuery("hentMaksSakstatistikkSekvensId") {
            sessionFactory.withSession { session ->
                """
                    SELECT max(id_sekvens) AS maks_sekvens_id
                    FROM sak_statistikk
                    WHERE funksjonell_tid < :til_eksklusiv
                """.trimIndent().hent(
                    params = mapOf("til_eksklusiv" to nøkkel.tilOgMed.plusDays(1)),
                    session = session,
                ) { it.longOrNull("maks_sekvens_id") }
            }
        }
    }

    override fun ferdigstillSakstatistikkAggregat(
        id: UUID,
        payload: String,
        maksSekvensId: Long?,
        versjon: Int,
    ) {
        dbMetrics.timeQuery("ferdigstillSakstatistikkAggregat") {
            sessionFactory.withSession { session ->
                """
                    UPDATE sak_statistikk_aggregat
                    SET status = 'FERDIG',
                        versjon = :versjon,
                        maks_sekvens_id = :maks_sekvens_id,
                        ferdig = NOW(),
                        payload = to_jsonb(:payload::jsonb),
                        feilmelding = NULL
                    WHERE id = :id
                """.trimIndent().oppdatering(
                    params = mapOf(
                        "id" to id,
                        "versjon" to versjon,
                        "maks_sekvens_id" to maksSekvensId,
                        "payload" to payload,
                    ),
                    session = session,
                )
            }
        }
    }

    override fun markerSakstatistikkAggregatFeilet(id: UUID, feilmelding: String?) {
        dbMetrics.timeQuery("markerSakstatistikkAggregatFeilet") {
            sessionFactory.withSession { session ->
                """
                    UPDATE sak_statistikk_aggregat
                    SET status = 'FEILET',
                        ferdig = NOW(),
                        feilmelding = :feilmelding
                    WHERE id = :id
                """.trimIndent().oppdatering(
                    params = mapOf(
                        "id" to id,
                        "feilmelding" to feilmelding?.take(1000),
                    ),
                    session = session,
                )
            }
        }
    }

    override fun hentStønadstatistikk(
        fraOgMed: YearMonth,
        tilOgMed: YearMonth,
    ): List<StønadStatistikkAggregertRad> {
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
                        WHERE maaned >= :fra_og_med
                          AND maaned <= :til_og_med
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
                    params = mapOf(
                        "fra_og_med" to fraOgMed.atDay(1),
                        "til_og_med" to tilOgMed.atEndOfMonth(),
                    ),
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

    private fun hentAggregat(
        nøkkel: SakStatistikkAggregatnøkkel,
        session: Session,
    ): SakStatistikkAggregat? {
        return """
            SELECT *
            FROM sak_statistikk_aggregat
            WHERE fra_og_med = :fra_og_med
              AND til_og_med = :til_og_med
              AND opplosning = :opplosning
        """.trimIndent().hent(
            params = mapOf(
                "fra_og_med" to nøkkel.fraOgMed,
                "til_og_med" to nøkkel.tilOgMed,
                "opplosning" to nøkkel.oppløsning.name,
            ),
            session = session,
        ) { it.toAggregat() }
    }

    private fun Row.toAggregat() = SakStatistikkAggregat(
        id = uuid("id"),
        nøkkel = SakStatistikkAggregatnøkkel(
            fraOgMed = localDate("fra_og_med"),
            tilOgMed = localDate("til_og_med"),
            oppløsning = no.nav.su.se.bakover.domain.statistikk.Statistikkoppløsning.valueOf(string("opplosning")),
        ),
        status = SakStatistikkAggregatstatus.valueOf(string("status")),
        versjon = int("versjon"),
        maksSekvensId = longOrNull("maks_sekvens_id"),
        opprettet = instant("opprettet"),
        startet = instantOrNull("startet"),
        ferdig = instantOrNull("ferdig"),
        payload = stringOrNull("payload"),
    )

    private fun Row.toVisningsrad() = SakStatistikkVisningsrad(
        sekvensId = long("id_sekvens"),
        behandlingId = uuid("behandling_id"),
        sakYtelse = string("sak_ytelse"),
        behandlingType = string("behandling_type"),
        behandlingMetode = string("behandling_metode"),
        behandlingAarsak = stringOrNull("behandling_aarsak"),
        behandlingStatus = string("behandling_status"),
        behandlingResultat = stringOrNull("behandling_resultat"),
        funksjonellTid = tidspunkt("funksjonell_tid"),
        revurderingstype = stringOrNull("revurderingstype"),
    )

    private fun SakStatistikkAggregatnøkkel.periodeparametre() = mapOf(
        "fra_og_med" to fraOgMed,
        "til_eksklusiv" to tilOgMed.plusDays(1),
    )
}
