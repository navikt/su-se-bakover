package no.nav.su.se.bakover.database.historisk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.domain.historisk.InfotrygdTabeller
import no.nav.su.se.bakover.domain.historisk.NyHistoriskTabellimport
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAldersberegning
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAldersstønad
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAldersvedtak
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBeløp
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskDato
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskKlassifiseringsnivå
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskKode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskMånedsbeløp
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskPeriode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskResultat
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskSaksreferanse
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskSakstype
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadsklassifisering
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskSuDetalj
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.LocalDate
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class HistoriskAlderProjeksjonPostgresRepoTest(
    private val dataSource: DataSource,
) {
    @Test
    fun `persisterer siste gjeldende vedtak som komprimert tidslinje`() {
        val helper = TestDataHelper(dataSource)
        val importRepo = HistoriskImportPostgresRepo(helper.sessionFactory, helper.dbMetrics)
        val import =
            importRepo.opprettImport(
                listOf(NyHistoriskTabellimport(InfotrygdTabeller.T_STONAD, 0, listOf("STONAD_ID"))),
            )
        importRepo.fullførImport(import.id)

        val repo = HistoriskAlderProjeksjonPostgresRepo(helper.sessionFactory, helper.dbMetrics)
        val projeksjonId = repo.startProjeksjon(import.id)
        repo.lagreBatch(
            projeksjonId,
            import.id,
            listOf(
                HistoriskAldersstønad(
                    stønadId = HistoriskStønadId("20"),
                    personLøpenummer = "10",
                    personident = "12345678910",
                    startdato = null,
                    oppdragId = "30",
                    opphør = null,
                    vedtak =
                    listOf(
                        vedtak(
                            id = "40",
                            periode = periode("2020-01-01", "2020-12-31"),
                            registrert = "2020-01-10T10:00:00",
                            resultat = HistoriskResultat.INNVILGET,
                            sats = "15010",
                            fradrag = "0",
                        ),
                        vedtak(
                            id = "41",
                            periode = periode("2020-07-01", "2020-12-31"),
                            registrert = "2021-01-17T07:47:13",
                            resultat = HistoriskResultat.FORTSATT_INNVILGET,
                            sats = "16869",
                            fradrag = "5622",
                        ),
                        vedtak(
                            id = "42",
                            periode = periode("2020-05-01", "2020-12-31"),
                            registrert = "2021-02-01T10:00:00",
                            resultat = HistoriskResultat.UENDRET,
                            sats = null,
                            fradrag = null,
                        ),
                        vedtak(
                            id = "43",
                            periode = periode("2020-09-01", "2020-12-31"),
                            registrert = "2021-03-01T10:00:00",
                            resultat = HistoriskResultat.ANNULLERT,
                            sats = "20000",
                            fradrag = "0",
                        ),
                        vedtak(
                            id = "44",
                            periode = HistoriskPeriode(dato("2020-10-01"), null),
                            registrert = "2021-04-01T10:00:00",
                            resultat = HistoriskResultat.INNVILGET,
                            sats = "21000",
                            fradrag = "0",
                        ),
                    ),
                ),
            ),
        )
        repo.fullførProjeksjon(projeksjonId, 1)

        repo.harSak("12345678910") shouldBe true
        repo.harSak("10987654321") shouldBe false
        repo.hentVedtaksperioder("12345678910").map { it.vedtakId.value } shouldBe
            listOf("40", "42", "41", "43", "44")
        repo.hentVedtaksperioder("12345678910").single { it.vedtakId.value == "41" }.also {
            it.bosituasjon shouldBe HistoriskKode("EO", HistoriskBosituasjon.EPS_OVER_67)
            it.årligYtelsesbeløp shouldBe BigDecimal("202428")
        }
        repo.hentVedtaksperioder("12345678910").single { it.vedtakId.value == "43" }.gyldig shouldBe false
        repo.hentVedtaksperioder("12345678910").single { it.vedtakId.value == "44" }.gyldig shouldBe false
        repo
            .hentYtelsesperioder(
                personident = "12345678910",
                fraOgMed = LocalDate.of(2020, 1, 1),
                tilOgMed = LocalDate.of(2020, 12, 31),
            ).also {
                it.size shouldBe 2
                it[0].fraOgMed shouldBe LocalDate.of(2020, 1, 1)
                it[0].tilOgMed shouldBe LocalDate.of(2020, 6, 30)
                it[0].vedtakId.value shouldBe "40"
                it[1].fraOgMed shouldBe LocalDate.of(2020, 7, 1)
                it[1].tilOgMed shouldBe LocalDate.of(2020, 12, 31)
                it[1].vedtakId.value shouldBe "41"
                it[1].beløpTilUtbetaling shouldBe BigDecimal("11247")
                it[1].bosituasjon shouldBe HistoriskKode("EO", HistoriskBosituasjon.EPS_OVER_67)
                it[1].årligYtelsesbeløp shouldBe BigDecimal("202428")
            }
    }

    @Test
    fun `avviser ny start når projeksjon for samme import allerede pågår`() {
        val helper = TestDataHelper(dataSource)
        val importRepo = HistoriskImportPostgresRepo(helper.sessionFactory, helper.dbMetrics)
        val import =
            importRepo
                .opprettImport(
                    listOf(NyHistoriskTabellimport(InfotrygdTabeller.T_STONAD, 0, listOf("STONAD_ID"))),
                ).also { importRepo.fullførImport(it.id) }
        val repo = HistoriskAlderProjeksjonPostgresRepo(helper.sessionFactory, helper.dbMetrics)

        val projeksjonId = repo.startProjeksjon(import.id)
        repo.lagreBatch(projeksjonId, import.id, listOf(stønad("20", "12345678910")))

        assertThrows<IllegalStateException> {
            repo.startProjeksjon(import.id)
        }

        repo.fullførProjeksjon(projeksjonId, 1)
        repo.harSak("12345678910") shouldBe true
    }

    @Test
    fun `leser siste fullførte projeksjon mens en nyere projeksjon pågår`() {
        val helper = TestDataHelper(dataSource)
        val importRepo = HistoriskImportPostgresRepo(helper.sessionFactory, helper.dbMetrics)
        val repo = HistoriskAlderProjeksjonPostgresRepo(helper.sessionFactory, helper.dbMetrics)
        val førsteImport =
            importRepo
                .opprettImport(
                    listOf(
                        NyHistoriskTabellimport(
                            InfotrygdTabeller.T_STONAD,
                            0,
                            listOf("STONAD_ID"),
                        ),
                    ),
                ).also { importRepo.fullførImport(it.id) }
        val andreImport =
            importRepo
                .opprettImport(
                    listOf(
                        NyHistoriskTabellimport(
                            InfotrygdTabeller.T_STONAD,
                            0,
                            listOf("STONAD_ID"),
                        ),
                    ),
                ).also { importRepo.fullførImport(it.id) }

        val førsteProjeksjonId = repo.startProjeksjon(førsteImport.id)
        repo.lagreBatch(førsteProjeksjonId, førsteImport.id, listOf(stønad("20", "12345678910")))
        repo.fullførProjeksjon(førsteProjeksjonId, 1)
        val andreProjeksjonId = repo.startProjeksjon(andreImport.id)
        repo.lagreBatch(andreProjeksjonId, andreImport.id, listOf(stønad("21", "10987654321")))

        repo.harSak("12345678910") shouldBe true
        repo.harSak("10987654321") shouldBe false

        repo.fullførProjeksjon(andreProjeksjonId, 1)

        repo.harSak("12345678910") shouldBe false
        repo.harSak("10987654321") shouldBe true
    }

    @Test
    fun `beholder dry-run med deldata uten å gjøre den gjeldende for oppslag`() {
        val helper = TestDataHelper(dataSource)
        val importRepo = HistoriskImportPostgresRepo(helper.sessionFactory, helper.dbMetrics)
        val repo = HistoriskAlderProjeksjonPostgresRepo(helper.sessionFactory, helper.dbMetrics)
        val import =
            importRepo
                .opprettImport(
                    listOf(NyHistoriskTabellimport(InfotrygdTabeller.T_STONAD, 0, listOf("STONAD_ID"))),
                ).also { importRepo.fullførImport(it.id) }

        val ordinærProjeksjonId = repo.startProjeksjon(import.id)
        repo.lagreBatch(ordinærProjeksjonId, import.id, listOf(stønad("20", "12345678910")))
        repo.fullførProjeksjon(ordinærProjeksjonId, 1)

        val dryRunId = repo.startProjeksjon(import.id, dryRun = true, maksAntallStønader = 1)
        repo.lagreBatch(dryRunId, import.id, listOf(stønad("21", "10987654321")))
        repo.fullførProjeksjon(
            projeksjonId = dryRunId,
            antallStønader = 1,
            avviksoppsummering = mapOf("UgyldigDato" to 2),
            forbehold = setOf("FAKTISK_UTBETALING_MÅ_EVENTUELT_HENTES_FRA_OS_ELLER_UR"),
        )

        repo.harSak("12345678910") shouldBe true
        repo.harSak("10987654321") shouldBe false
        repo.hentProjeksjoner(import.id).first().also {
            it.id shouldBe dryRunId
            it.antallStønader shouldBe 1
            it.avviksoppsummering shouldBe mapOf("UgyldigDato" to 2)
            it.forbehold shouldBe setOf("FAKTISK_UTBETALING_MÅ_EVENTUELT_HENTES_FRA_OS_ELLER_UR")
        }
        helper.sessionFactory.withSession { session ->
            """
            SELECT
                dry_run::text AS dry_run,
                maks_antall_stonader::text AS maks_antall_stonader,
                antall_stonader::text AS antall_stonader,
                status
            FROM historisk_alder_projeksjon
            WHERE id = :id
            """.trimIndent().hent(mapOf("id" to dryRunId), session) {
                listOf(
                    it.string("dry_run"),
                    it.string("maks_antall_stonader"),
                    it.string("antall_stonader"),
                    it.string("status"),
                )
            }
        } shouldBe listOf("true", "1", "1", "FULLFØRT")
        helper.sessionFactory.withSession { session ->
            """
            SELECT COUNT(*)::text AS antall
            FROM historisk_alder_projeksjon
            WHERE import_id = :import_id
            """.trimIndent().hent(mapOf("import_id" to import.id), session) {
                it.string("antall")
            }
        } shouldBe "2"
    }

    private fun stønad(stønadId: String, personident: String) =
        HistoriskAldersstønad(
            stønadId = HistoriskStønadId(stønadId),
            personLøpenummer = stønadId,
            personident = personident,
            startdato = dato("2020-01-01"),
            oppdragId = null,
            opphør = null,
            vedtak = emptyList(),
        )

    private fun vedtak(
        id: String,
        periode: HistoriskPeriode,
        registrert: String,
        resultat: HistoriskResultat,
        sats: String?,
        fradrag: String?,
    ): HistoriskAldersvedtak =
        HistoriskAldersvedtak(
            vedtakId = HistoriskVedtakId(id),
            stønadId = HistoriskStønadId("20"),
            sakstype = HistoriskKode("R", HistoriskSakstype.REVURDERING),
            resultat = HistoriskKode("", resultat),
            periode = periode,
            mottattDato = null,
            registrertTidspunkt = registrert,
            registrertAv = null,
            saksreferanse = HistoriskSaksreferanse(null, null, null, null),
            beregningstype = null,
            nøkkelDl1 = null,
            klassifiseringer =
            listOf(
                HistoriskStønadsklassifisering(
                    nivå = HistoriskKlassifiseringsnivå("02", "Klassifisering 2 (STK2)"),
                    kode = "EO",
                    bosituasjon = HistoriskBosituasjon.EPS_OVER_67,
                ),
            ),
            roller = emptyList(),
            beregning =
            HistoriskAldersberegning(
                suDetaljer =
                listOf(
                    HistoriskSuDetalj(
                        årligYtelsesbeløp =
                        sats?.let {
                            val årsbeløp = BigDecimal(it).multiply(BigDecimal(12))
                            HistoriskBeløp(årsbeløp.toPlainString(), årsbeløp)
                        },
                        revurderingsdato = null,
                        registrertTidspunkt = registrert,
                    ),
                ),
                inntekter = emptyList(),
                delytelser = emptyList(),
                månedsbeløp =
                if (sats == null || fradrag == null) {
                    emptyList()
                } else {
                    listOf(
                        HistoriskMånedsbeløp(
                            periode = periode,
                            sats = BigDecimal(sats),
                            fradrag = BigDecimal(fradrag),
                            linjeId = "1",
                        ),
                    )
                },
            ),
            endringskoder = emptyList(),
            beslutninger = emptyList(),
        )

    private fun periode(fraOgMed: String, tilOgMed: String) =
        HistoriskPeriode(dato(fraOgMed), dato(tilOgMed))

    private fun dato(verdi: String) = HistoriskDato(verdi, LocalDate.parse(verdi))
}
