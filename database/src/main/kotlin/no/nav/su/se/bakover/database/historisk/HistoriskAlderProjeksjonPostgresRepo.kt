package no.nav.su.se.bakover.database.historisk

import kotliquery.Row
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.deserializeMap
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
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAlderProjeksjonOversikt
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAlderProjeksjonPågårException
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAlderProjeksjonRepo
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAlderProjeksjonStatus
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAldersstønad
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskImportIkkeFunnetException
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskKode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskResultat
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskSakstype
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtaksperiode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskYtelsesperiode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.SlettHistoriskAlderProjeksjonResultat
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

class HistoriskAlderProjeksjonPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val ferdigstillingsBatchSize: Int = 100,
) : HistoriskAlderProjeksjonRepo {
    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        require(ferdigstillingsBatchSize > 0) { "ferdigstillingsBatchSize må være større enn 0" }
    }

    override fun startProjeksjon(
        importId: UUID,
        dryRun: Boolean,
        maksAntallStønader: Int?,
    ): UUID {
        require(dryRun == (maksAntallStønader != null)) {
            "Dry-run må ha maksAntallStønader, og full konvertering kan ikke ha en grense"
        }
        require(maksAntallStønader == null || maksAntallStønader > 0) {
            "maksAntallStønader må være større enn 0"
        }
        return dbMetrics.timeQuery("startHistoriskAlderProjeksjon") {
            sessionFactory.withTransaction { tx ->
                val importFinnes =
                    """
                    SELECT id
                    FROM historisk_import
                    WHERE id = :import_id
                    FOR UPDATE
                    """.trimIndent().hent(mapOf("import_id" to importId), tx) {
                        it.uuid("id")
                    }
                if (importFinnes == null) {
                    throw HistoriskImportIkkeFunnetException(importId)
                }
                val pågåendeProjeksjon =
                    """
                    SELECT id
                    FROM historisk_alder_projeksjon
                    WHERE status = 'PÅGÅR'
                    """.trimIndent().hent(emptyMap(), tx) {
                        it.uuid("id")
                    }
                if (pågåendeProjeksjon != null) {
                    throw HistoriskAlderProjeksjonPågårException(pågåendeProjeksjon)
                }
                val projeksjonId = UUID.randomUUID()
                """
                INSERT INTO historisk_alder_projeksjon (
                    id,
                    import_id,
                    status,
                    dry_run,
                    maks_antall_stonader
                ) VALUES (
                    :id,
                    :import_id,
                    'PÅGÅR',
                    :dry_run,
                    :maks_antall_stonader
                )
                """.trimIndent().insert(
                    mapOf(
                        "id" to projeksjonId,
                        "import_id" to importId,
                        "dry_run" to dryRun,
                        "maks_antall_stonader" to maksAntallStønader,
                    ),
                    tx,
                )
                projeksjonId
            }
        }
    }

    override fun lagreBatch(projeksjonId: UUID, importId: UUID, stønader: List<HistoriskAldersstønad>) {
        if (stønader.isEmpty()) return
        dbMetrics.timeQuery("lagreHistoriskAlderBatch") {
            sessionFactory.withTransaction { tx ->
                krevPågåendeProjeksjon(projeksjonId, tx)
                tx.batchPreparedNamedStatement(
                    """
                    INSERT INTO historisk_alder_stonad (
                        projeksjon_id,
                        import_id,
                        stonad_id,
                        person_lopenummer,
                        personident,
                        startdato,
                        opphorsdato
                    ) VALUES (
                        :projeksjon_id,
                        :import_id,
                        :stonad_id,
                        :person_lopenummer,
                        :personident,
                        :startdato,
                        :opphorsdato
                    )
                    """.trimIndent(),
                    stønader.map {
                        mapOf(
                            "projeksjon_id" to projeksjonId,
                            "import_id" to importId,
                            "stonad_id" to it.stønadId.value,
                            "person_lopenummer" to it.personLøpenummer,
                            "personident" to it.personident,
                            "startdato" to it.startdato?.dato,
                            "opphorsdato" to it.opphør?.dato?.dato,
                        )
                    },
                )

                val vedtak = stønader.flatMap { it.vedtak }
                if (vedtak.isNotEmpty()) {
                    tx.batchPreparedNamedStatement(
                        """
                        INSERT INTO historisk_alder_vedtak (
                            projeksjon_id,
                            import_id,
                            vedtak_id,
                            stonad_id,
                            sakstype_raw,
                            sakstype,
                            resultat_raw,
                            resultat,
                            fra_og_med,
                            til_og_med,
                            bosituasjon_raw,
                            bosituasjon,
                            aarlig_ytelsesbelop,
                            registrert_tidspunkt,
                            registrert_av,
                            gyldig
                        ) VALUES (
                            :projeksjon_id,
                            :import_id,
                            :vedtak_id,
                            :stonad_id,
                            :sakstype_raw,
                            :sakstype,
                            :resultat_raw,
                            :resultat,
                            :fra_og_med,
                            :til_og_med,
                            :bosituasjon_raw,
                            :bosituasjon,
                            :aarlig_ytelsesbelop,
                            CAST(:registrert_tidspunkt AS TIMESTAMP),
                            :registrert_av,
                            :gyldig
                        )
                        """.trimIndent(),
                        vedtak.map {
                            val fraOgMed = it.periode.fraOgMed?.dato
                            val tilOgMed = it.periode.tilOgMed?.dato
                            val bosituasjon = it.klassifiseringer.singleOrNull { klassifisering ->
                                klassifisering.nivå?.kode == "02"
                            }
                            mapOf(
                                "projeksjon_id" to projeksjonId,
                                "import_id" to importId,
                                "vedtak_id" to it.vedtakId.value,
                                "stonad_id" to it.stønadId.value,
                                "sakstype_raw" to it.sakstype.råverdi,
                                "sakstype" to it.sakstype.tolketVerdi?.name,
                                "resultat_raw" to it.resultat.råverdi,
                                "resultat" to it.resultat.tolketVerdi?.name,
                                "fra_og_med" to fraOgMed,
                                "til_og_med" to tilOgMed,
                                "bosituasjon_raw" to bosituasjon?.kode,
                                "bosituasjon" to bosituasjon?.bosituasjon?.name,
                                "aarlig_ytelsesbelop" to
                                    it.beregning.suDetaljer.singleOrNull()?.årligYtelsesbeløp?.beløp,
                                "registrert_tidspunkt" to it.registrertTidspunkt,
                                "registrert_av" to it.registrertAv,
                                "gyldig" to (
                                    fraOgMed != null &&
                                        tilOgMed != null &&
                                        fraOgMed <= tilOgMed &&
                                        it.resultat.tolketVerdi != HistoriskResultat.ANNULLERT &&
                                        it.endringskoder.none { kode -> kode == "AN" || kode == "UA" }
                                    ),
                            )
                        },
                    )
                }

                val månedsbeløp =
                    vedtak.flatMap { historiskVedtak ->
                        historiskVedtak.beregning.månedsbeløp.map { historiskVedtak to it }
                    }
                if (månedsbeløp.isNotEmpty()) {
                    tx.batchPreparedNamedStatement(
                        """
                        INSERT INTO historisk_alder_manedsbelop (
                            projeksjon_id,
                            import_id,
                            vedtak_id,
                            linje_id,
                            fra_og_med,
                            til_og_med,
                            sats,
                            fradrag
                        ) VALUES (
                            :projeksjon_id,
                            :import_id,
                            :vedtak_id,
                            :linje_id,
                            :fra_og_med,
                            :til_og_med,
                            :sats,
                            :fradrag
                        )
                        """.trimIndent(),
                        månedsbeløp.map { (historiskVedtak, beløp) ->
                            mapOf(
                                "projeksjon_id" to projeksjonId,
                                "import_id" to importId,
                                "vedtak_id" to historiskVedtak.vedtakId.value,
                                "linje_id" to beløp.linjeId,
                                "fra_og_med" to beløp.periode.fraOgMed?.dato,
                                "til_og_med" to beløp.periode.tilOgMed?.dato,
                                "sats" to beløp.sats,
                                "fradrag" to beløp.fradrag,
                            )
                        },
                    )
                }
                """
                UPDATE historisk_alder_projeksjon
                SET antall_stonader = antall_stonader + :antall_stonader
                WHERE id = :projeksjon_id
                  AND import_id = :import_id
                  AND status = 'PÅGÅR'
                """.trimIndent().oppdatering(
                    mapOf(
                        "projeksjon_id" to projeksjonId,
                        "import_id" to importId,
                        "antall_stonader" to stønader.size,
                    ),
                    tx,
                ).also {
                    check(it == 1) { "Kunne ikke oppdatere fremdrift for historisk aldersprojeksjon $projeksjonId" }
                }
            }
        }
    }

    override fun fullførProjeksjon(
        projeksjonId: UUID,
        antallStønader: Int,
        avviksoppsummering: Map<String, Int>,
        forbehold: Set<String>,
    ) {
        require(antallStønader >= 0) { "antallStønader kan ikke være negativt" }
        dbMetrics.timeQuery("fullførHistoriskAlderProjeksjon") {
            var sistePersonident = ""
            var antallFerdigstiltePersoner = 0
            var antallYtelsesperioder = 0
            while (true) {
                val (personidenter, opprettedeYtelsesperioder) = sessionFactory.withTransaction { tx ->
                    krevPågåendeProjeksjon(projeksjonId, tx)
                    val personidenter =
                        """
                        SELECT DISTINCT personident
                        FROM historisk_alder_stonad
                        WHERE projeksjon_id = :projeksjon_id
                          AND personident IS NOT NULL
                          AND personident > :siste_personident
                        ORDER BY personident
                        LIMIT :batch_size
                        """.trimIndent().hentListe(
                            mapOf(
                                "projeksjon_id" to projeksjonId,
                                "siste_personident" to sistePersonident,
                                "batch_size" to ferdigstillingsBatchSize,
                            ),
                            tx,
                        ) { it.string("personident") }
                    personidenter to if (personidenter.isEmpty()) {
                        0
                    } else {
                        lagreYtelsesperioder(projeksjonId, personidenter, tx)
                    }
                }
                if (personidenter.isEmpty()) break

                antallYtelsesperioder += opprettedeYtelsesperioder
                antallFerdigstiltePersoner += personidenter.size
                sistePersonident = personidenter.last()
                log.info(
                    "Historisk aldersprojeksjon {}: ferdigstilt ytelsesperioder for {} personer, " +
                        "{} perioder opprettet totalt",
                    projeksjonId,
                    antallFerdigstiltePersoner,
                    antallYtelsesperioder,
                )
            }

            sessionFactory.withTransaction { tx ->
                krevPågåendeProjeksjon(projeksjonId, tx)
                """
                UPDATE historisk_alder_projeksjon
                SET status = 'FULLFØRT',
                    fullført = NOW(),
                    avviksoppsummering = to_jsonb(:avviksoppsummering::jsonb),
                    forbehold = to_jsonb(:forbehold::jsonb),
                    feilbeskrivelse = NULL
                WHERE id = :projeksjon_id
                  AND status = 'PÅGÅR'
                  AND antall_stonader = :antall_stonader
                """.trimIndent().oppdatering(
                    mapOf(
                        "projeksjon_id" to projeksjonId,
                        "antall_stonader" to antallStønader,
                        "avviksoppsummering" to serialize(avviksoppsummering),
                        "forbehold" to serialize(forbehold.sorted()),
                    ),
                    tx,
                ).also {
                    check(it == 1) { "Kunne ikke fullføre historisk aldersprojeksjon $projeksjonId" }
                }
            }
        }
    }

    private fun lagreYtelsesperioder(
        projeksjonId: UUID,
        personidenter: List<String>,
        tx: Session,
    ): Int =
        """
            WITH belopsperioder AS (
                    SELECT
                        s.personident,
                        v.stonad_id,
                        v.vedtak_id,
                        v.registrert_tidspunkt,
                        CASE
                            WHEN v.vedtak_id ~ '^[0-9]+$' THEN v.vedtak_id::numeric
                            ELSE NULL
                        END AS numerisk_vedtak_id,
                        b.sats,
                        b.fradrag,
                        GREATEST(v.fra_og_med, b.fra_og_med, s.startdato) AS fra_og_med,
                        LEAST(
                            COALESCE(v.til_og_med, (DATE_TRUNC('month', i.opprettet) + INTERVAL '1 month - 1 day')::date),
                            COALESCE(b.til_og_med, (DATE_TRUNC('month', i.opprettet) + INTERVAL '1 month - 1 day')::date),
                            COALESCE(s.opphorsdato, (DATE_TRUNC('month', i.opprettet) + INTERVAL '1 month - 1 day')::date)
                        ) AS til_og_med
                    FROM historisk_alder_vedtak v
                    JOIN historisk_alder_stonad s
                      ON s.projeksjon_id = v.projeksjon_id
                     AND s.stonad_id = v.stonad_id
                    JOIN historisk_alder_manedsbelop b
                      ON b.projeksjon_id = v.projeksjon_id
                     AND b.vedtak_id = v.vedtak_id
                    JOIN historisk_import i ON i.id = v.import_id
                    WHERE v.projeksjon_id = :projeksjon_id
                      AND s.personident = ANY(:personidenter)
                      AND v.gyldig
                      AND v.resultat IN (
                          'INNVILGET',
                          'DELVIS_INNVILGET',
                          'FORTSATT_INNVILGET',
                          'INNVILGET_NY_SITUASJON',
                          'ØKNING',
                          'REDUSERT'
                      )
                      AND s.personident IS NOT NULL
                      AND b.fra_og_med IS NOT NULL
                ),
                kandidater AS (
                    SELECT
                        personident,
                        stonad_id,
                        vedtak_id,
                        registrert_tidspunkt,
                        numerisk_vedtak_id,
                        sats,
                        fradrag,
                        måned::date
                    FROM belopsperioder
                    CROSS JOIN LATERAL GENERATE_SERIES(
                        DATE_TRUNC('month', fra_og_med),
                        DATE_TRUNC('month', til_og_med),
                        INTERVAL '1 month'
                    ) måned
                    WHERE fra_og_med <= til_og_med
                ),
                rangerte AS (
                    SELECT
                        *,
                        ROW_NUMBER() OVER (
                            PARTITION BY personident, måned
                            ORDER BY
                                registrert_tidspunkt DESC NULLS LAST,
                                numerisk_vedtak_id DESC NULLS LAST,
                                vedtak_id DESC
                        ) AS rang
                    FROM kandidater
                ),
                valgte AS (
                    SELECT personident, stonad_id, vedtak_id, sats, fradrag, måned
                    FROM rangerte
                    WHERE rang = 1
                ),
                markerte AS (
                    SELECT
                        *,
                        CASE
                            WHEN LAG(måned) OVER personvindu = måned - INTERVAL '1 month'
                             AND LAG(stonad_id) OVER personvindu = stonad_id
                             AND LAG(vedtak_id) OVER personvindu = vedtak_id
                             AND LAG(sats) OVER personvindu = sats
                             AND LAG(fradrag) OVER personvindu = fradrag
                            THEN 0
                            ELSE 1
                        END AS ny_gruppe
                    FROM valgte
                    WINDOW personvindu AS (PARTITION BY personident ORDER BY måned)
                ),
                grupperte AS (
                    SELECT
                        *,
                        SUM(ny_gruppe) OVER (
                            PARTITION BY personident
                            ORDER BY måned
                        ) AS gruppe
                    FROM markerte
                )
                INSERT INTO historisk_alder_ytelsesperiode (
                    projeksjon_id,
                    import_id,
                    personident,
                    stonad_id,
                    vedtak_id,
                    fra_og_med,
                    til_og_med,
                    sats,
                    fradrag
                )
                SELECT
                    :projeksjon_id,
                    v.import_id,
                    g.personident,
                    g.stonad_id,
                    g.vedtak_id,
                    MIN(g.måned),
                    (MAX(g.måned) + INTERVAL '1 month - 1 day')::date,
                    g.sats,
                    g.fradrag
                FROM grupperte g
                JOIN historisk_alder_vedtak v
                  ON v.projeksjon_id = :projeksjon_id
                 AND v.vedtak_id = g.vedtak_id
                GROUP BY v.import_id, g.personident, g.gruppe, g.stonad_id, g.vedtak_id, g.sats, g.fradrag
        """.trimIndent().insert(
            mapOf(
                "projeksjon_id" to projeksjonId,
                "personidenter" to tx.connection.underlying.createArrayOf("text", personidenter.toTypedArray()),
            ),
            tx,
        )

    override fun hentProjeksjoner(importId: UUID): List<HistoriskAlderProjeksjonOversikt> =
        dbMetrics.timeQuery("hentHistoriskeAlderProjeksjoner") {
            sessionFactory.withSession { session ->
                """
                SELECT
                    id,
                    import_id,
                    status,
                    dry_run,
                    maks_antall_stonader,
                    antall_stonader,
                    avviksoppsummering,
                    forbehold,
                    opprettet,
                    fullført,
                    feilbeskrivelse
                FROM historisk_alder_projeksjon
                WHERE import_id = :import_id
                ORDER BY opprettet DESC, id DESC
                """.trimIndent().hentListe(mapOf("import_id" to importId), session, ::tilProjeksjonOversikt)
            }
        }

    override fun slettProjeksjon(
        importId: UUID,
        projeksjonId: UUID,
    ): SlettHistoriskAlderProjeksjonResultat =
        dbMetrics.timeQuery("slettHistoriskAlderProjeksjon") {
            sessionFactory.withTransaction { tx ->
                val status =
                    """
                    SELECT status
                    FROM historisk_alder_projeksjon
                    WHERE id = :projeksjon_id
                      AND import_id = :import_id
                    FOR UPDATE
                    """.trimIndent().hent(
                        mapOf(
                            "projeksjon_id" to projeksjonId,
                            "import_id" to importId,
                        ),
                        tx,
                    ) {
                        HistoriskAlderProjeksjonStatus.valueOf(it.string("status"))
                    } ?: return@withTransaction SlettHistoriskAlderProjeksjonResultat.IKKE_FUNNET

                if (status == HistoriskAlderProjeksjonStatus.PÅGÅR) {
                    return@withTransaction SlettHistoriskAlderProjeksjonResultat.PÅGÅR
                }

                """
                DELETE FROM historisk_alder_projeksjon
                WHERE id = :projeksjon_id
                  AND import_id = :import_id
                """.trimIndent().oppdatering(
                    mapOf(
                        "projeksjon_id" to projeksjonId,
                        "import_id" to importId,
                    ),
                    tx,
                ).also {
                    check(it == 1) { "Historisk aldersprojeksjon $projeksjonId ble ikke slettet" }
                }
                SlettHistoriskAlderProjeksjonResultat.SLETTET
            }
        }

    override fun markerFeilet(projeksjonId: UUID, beskrivelse: String) {
        dbMetrics.timeQuery("markerHistoriskAlderProjeksjonFeilet") {
            sessionFactory.withTransaction { tx ->
                """
                UPDATE historisk_alder_projeksjon
                SET status = 'FEILET',
                    fullført = NOW(),
                    feilbeskrivelse = :feilbeskrivelse
                WHERE id = :projeksjon_id
                  AND status = 'PÅGÅR'
                """.trimIndent().oppdatering(
                    mapOf(
                        "projeksjon_id" to projeksjonId,
                        "feilbeskrivelse" to beskrivelse.take(2000),
                    ),
                    tx,
                )
            }
        }
    }

    override fun harSak(personident: String): Boolean =
        dbMetrics.timeQuery("harHistoriskAlderSak") {
            sessionFactory.withSession { session ->
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM historisk_alder_stonad s
                    JOIN siste_fullførte_historiske_alder_projeksjon() p
                      ON p.projeksjon_id = s.projeksjon_id
                    WHERE s.personident = :personident
                ) AS har_sak
                """.trimIndent().hent(mapOf("personident" to personident), session) {
                    it.boolean("har_sak")
                } ?: false
            }
        }

    override fun hentVedtaksperioder(personident: String): List<HistoriskVedtaksperiode> =
        dbMetrics.timeQuery("hentHistoriskeAlderVedtaksperioder") {
            sessionFactory.withSession { session ->
                """
                SELECT
                    v.stonad_id,
                    v.vedtak_id,
                    v.fra_og_med,
                    v.til_og_med,
                    v.sakstype_raw,
                    v.sakstype,
                    v.resultat_raw,
                    v.resultat,
                    v.bosituasjon_raw,
                    v.bosituasjon,
                    v.aarlig_ytelsesbelop,
                    v.registrert_tidspunkt,
                    v.gyldig
                FROM historisk_alder_vedtak v
                JOIN historisk_alder_stonad s
                  ON s.projeksjon_id = v.projeksjon_id
                 AND s.stonad_id = v.stonad_id
                JOIN siste_fullførte_historiske_alder_projeksjon() p
                  ON p.projeksjon_id = v.projeksjon_id
                WHERE s.personident = :personident
                ORDER BY v.fra_og_med, v.registrert_tidspunkt, v.vedtak_id
                """.trimIndent().hentListe(mapOf("personident" to personident), session, ::tilVedtaksperiode)
            }
        }

    override fun hentYtelsesperioder(
        personident: String,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
    ): List<HistoriskYtelsesperiode> {
        require(fraOgMed <= tilOgMed) { "fraOgMed må være før eller lik tilOgMed" }
        return dbMetrics.timeQuery("hentHistoriskeAlderYtelsesperioder") {
            sessionFactory.withSession { session ->
                """
                SELECT
                    y.stonad_id,
                    y.vedtak_id,
                    GREATEST(y.fra_og_med, :fra_og_med) AS fra_og_med,
                    LEAST(y.til_og_med, :til_og_med) AS til_og_med,
                    y.sats,
                    y.fradrag,
                    v.bosituasjon_raw,
                    v.bosituasjon,
                    v.aarlig_ytelsesbelop
                FROM historisk_alder_ytelsesperiode y
                JOIN historisk_alder_vedtak v
                  ON v.projeksjon_id = y.projeksjon_id
                 AND v.vedtak_id = y.vedtak_id
                JOIN siste_fullførte_historiske_alder_projeksjon() p
                  ON p.projeksjon_id = y.projeksjon_id
                WHERE y.personident = :personident
                  AND y.fra_og_med <= :til_og_med
                  AND y.til_og_med >= :fra_og_med
                ORDER BY y.fra_og_med
                """.trimIndent().hentListe(
                    mapOf(
                        "personident" to personident,
                        "fra_og_med" to fraOgMed,
                        "til_og_med" to tilOgMed,
                    ),
                    session,
                ) {
                    HistoriskYtelsesperiode(
                        stønadId = HistoriskStønadId(it.string("stonad_id")),
                        vedtakId = HistoriskVedtakId(it.string("vedtak_id")),
                        fraOgMed = it.localDate("fra_og_med"),
                        tilOgMed = it.localDate("til_og_med"),
                        sats = it.bigDecimal("sats"),
                        fradrag = it.bigDecimal("fradrag"),
                        bosituasjon = it.tilBosituasjon(),
                        årligYtelsesbeløp = it.anyOrNull("aarlig_ytelsesbelop")?.let { _ ->
                            it.bigDecimal("aarlig_ytelsesbelop")
                        },
                    )
                }
            }
        }
    }

    private fun krevPågåendeProjeksjon(
        projeksjonId: UUID,
        tx: no.nav.su.se.bakover.common.infrastructure.persistence.Session,
    ) {
        val status =
            """
            SELECT status
            FROM historisk_alder_projeksjon
            WHERE id = :projeksjon_id
            FOR UPDATE
            """.trimIndent().hent(mapOf("projeksjon_id" to projeksjonId), tx) {
                it.string("status")
            }
        check(status == "PÅGÅR") {
            "Historisk aldersprojeksjon $projeksjonId har status $status, forventet PÅGÅR"
        }
    }

    private fun tilVedtaksperiode(row: Row): HistoriskVedtaksperiode =
        HistoriskVedtaksperiode(
            stønadId = HistoriskStønadId(row.string("stonad_id")),
            vedtakId = HistoriskVedtakId(row.string("vedtak_id")),
            fraOgMed = row.localDateOrNull("fra_og_med"),
            tilOgMed = row.localDateOrNull("til_og_med"),
            sakstype =
            HistoriskKode(
                råverdi = row.string("sakstype_raw"),
                tolketVerdi = row.stringOrNull("sakstype")?.let(HistoriskSakstype::valueOf),
            ),
            resultat =
            HistoriskKode(
                råverdi = row.string("resultat_raw"),
                tolketVerdi = row.stringOrNull("resultat")?.let(HistoriskResultat::valueOf),
            ),
            bosituasjon = row.tilBosituasjon(),
            årligYtelsesbeløp = row.anyOrNull("aarlig_ytelsesbelop")?.let {
                row.bigDecimal("aarlig_ytelsesbelop")
            },
            registrertTidspunkt =
            row
                .anyOrNull("registrert_tidspunkt")
                ?.let { row.localDateTime("registrert_tidspunkt").toString() },
            gyldig = row.boolean("gyldig"),
        )

    private fun tilProjeksjonOversikt(row: Row): HistoriskAlderProjeksjonOversikt =
        HistoriskAlderProjeksjonOversikt(
            id = row.uuid("id"),
            importId = row.uuid("import_id"),
            status = HistoriskAlderProjeksjonStatus.valueOf(row.string("status")),
            dryRun = row.boolean("dry_run"),
            maksAntallStønader =
            row.anyOrNull("maks_antall_stonader")?.let { row.int("maks_antall_stonader") },
            antallStønader = row.int("antall_stonader"),
            avviksoppsummering = deserializeMap<String, Int>(row.string("avviksoppsummering")),
            forbehold = deserializeList<String>(row.string("forbehold")).toSet(),
            opprettet = row.tidspunkt("opprettet"),
            fullført = row.tidspunktOrNull("fullført"),
            feilbeskrivelse = row.stringOrNull("feilbeskrivelse"),
        )

    private fun Row.tilBosituasjon(): HistoriskKode<HistoriskBosituasjon>? =
        stringOrNull("bosituasjon_raw")?.let { råverdi ->
            HistoriskKode(
                råverdi = råverdi,
                tolketVerdi = stringOrNull("bosituasjon")?.let(HistoriskBosituasjon::valueOf),
            )
        }
}
