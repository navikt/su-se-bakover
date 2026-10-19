package no.nav.su.se.bakover.database.historisk

import kotliquery.Row
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdBeløpsperiode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdTidslinjeRepo
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdTidslinjegrunnlag
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskInfotrygdTidslinjevedtak
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskResultat
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadsavgrensning
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import java.util.UUID

class HistoriskInfotrygdTidslinjePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : HistoriskInfotrygdTidslinjeRepo {

    override fun hentSisteFullførteProjeksjonIdForPerson(personident: String): UUID? =
        dbMetrics.timeQuery("hentSisteHistoriskeAlderProjeksjonForPerson") {
            sessionFactory.withSession { session ->
                """
                SELECT p.id
                FROM historisk_alder_projeksjon p
                JOIN historisk_alder_stonad s ON s.projeksjon_id = p.id
                WHERE p.status = 'FULLFØRT'
                  AND p.dry_run = FALSE
                  AND s.personident = :personident
                ORDER BY p.fullført DESC, p.opprettet DESC, p.id DESC
                LIMIT 1
                """.trimIndent().hent(mapOf("personident" to personident), session) { it.uuid("id") }
            }
        }

    /**
     * Én rad per vedtak og overlappende månedsbeløp. Vedtak uten overlappende månedsbeløp gir én rad
     * uten beløp (LEFT JOIN), slik at tidslinjen kan vise at måneden mangler beløp.
     */
    override fun hentOriginalTidslinjegrunnlag(
        projeksjonId: UUID,
        personident: String,
        periode: Periode,
    ): List<HistoriskInfotrygdTidslinjegrunnlag> =
        dbMetrics.timeQuery("hentHistoriskInfotrygdTidslinjegrunnlag") {
            sessionFactory.withSession { session ->
                """
                SELECT
                    v.stonad_id, v.vedtak_id, s.oppdrag_id,
                    v.fra_og_med, v.til_og_med, v.resultat, v.bosituasjon,
                    v.registrert_tidspunkt, v.endringskoder,
                    s.startdato AS stonad_fra_og_med, s.opphorsdato AS stonad_til_og_med,
                    b.fra_og_med AS belop_fra_og_med, b.til_og_med AS belop_til_og_med,
                    b.sats, b.fradrag, b.fradragskoder
                FROM historisk_alder_projeksjon p
                JOIN historisk_alder_stonad s ON s.projeksjon_id = p.id
                JOIN historisk_alder_vedtak v ON v.projeksjon_id = s.projeksjon_id AND v.stonad_id = s.stonad_id
                LEFT JOIN historisk_alder_manedsbelop b
                  ON b.projeksjon_id = v.projeksjon_id
                 AND b.vedtak_id = v.vedtak_id
                 AND b.fra_og_med <= :til_og_med
                 AND (b.til_og_med IS NULL OR b.til_og_med >= :fra_og_med)
                WHERE p.id = :projeksjon_id
                  AND p.status = 'FULLFØRT'
                  AND p.dry_run = FALSE
                  AND s.personident = :personident
                  AND v.fra_og_med <= :til_og_med
                  AND v.til_og_med >= :fra_og_med
                ORDER BY v.vedtak_id, b.fra_og_med, b.id
                """.trimIndent().hentListe(
                    mapOf(
                        "projeksjon_id" to projeksjonId,
                        "personident" to personident,
                        "fra_og_med" to periode.fraOgMed,
                        "til_og_med" to periode.tilOgMed,
                    ),
                    session,
                ) { row -> Rad(row.tilVedtak(), row.tilStønadsavgrensning(), row.tilBeløpsperiode()) }
                    .groupBy { it.vedtak.vedtakId }
                    .values
                    .map { rader ->
                        HistoriskInfotrygdTidslinjegrunnlag(
                            vedtak = rader.first().vedtak,
                            stønadsavgrensning = rader.first().stønadsavgrensning,
                            månedsbeløp = rader.mapNotNull { it.beløp },
                        )
                    }
            }
        }

    private data class Rad(
        val vedtak: HistoriskInfotrygdTidslinjevedtak,
        val stønadsavgrensning: HistoriskStønadsavgrensning,
        val beløp: HistoriskInfotrygdBeløpsperiode?,
    )

    private fun Row.tilVedtak() = HistoriskInfotrygdTidslinjevedtak(
        stønadId = HistoriskStønadId(long("stonad_id")),
        vedtakId = HistoriskVedtakId(long("vedtak_id")),
        oppdragId = stringOrNull("oppdrag_id"),
        fraOgMed = localDate("fra_og_med"),
        tilOgMed = localDate("til_og_med"),
        resultat = stringOrNull("resultat")?.let(HistoriskResultat::valueOf),
        bosituasjon = stringOrNull("bosituasjon")?.let(HistoriskBosituasjon::valueOf),
        registrertTidspunkt = localDateTimeOrNull("registrert_tidspunkt"),
        endringskoder = array<String>("endringskoder").toList(),
    )

    private fun Row.tilStønadsavgrensning() = HistoriskStønadsavgrensning(
        stønadId = HistoriskStønadId(long("stonad_id")),
        fraOgMed = localDateOrNull("stonad_fra_og_med"),
        tilOgMed = localDateOrNull("stonad_til_og_med"),
    )

    private fun Row.tilBeløpsperiode(): HistoriskInfotrygdBeløpsperiode? =
        localDateOrNull("belop_fra_og_med")?.let { fraOgMed ->
            HistoriskInfotrygdBeløpsperiode(
                fraOgMed = fraOgMed,
                tilOgMed = localDateOrNull("belop_til_og_med"),
                sats = bigDecimal("sats"),
                fradrag = bigDecimal("fradrag"),
                fradragskoder = array<String>("fradragskoder").toList(),
            )
        }
}
