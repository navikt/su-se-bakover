package no.nav.su.se.bakover.service.historisk

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.historisk.InfotrygdTabeller
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAldersstønad
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskOpphørsgrunn
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskResultat
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskSakstype
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

internal class HistoriskAlderProjectorTest {

    @Test
    fun `konverterer dokumenterte aldersdata og kobler tabellene via kildeidene`() {
        val personLøpenummer = "10"
        val relatertPersonLøpenummer = "11"
        val stønadId = "20"
        val vedtakId = "40"
        val fraOgMed = "2020-01-01"
        val tilOgMed = "2020-12-31"
        val linjeId = "50"
        val satstype = "M"
        val utbetalingstype = "L"
        val projeksjon = HistoriskAlderDataConverter().konverterRådataBatch(
            tomtDatasett() + mapOf(
                InfotrygdTabeller.T_LOPENR_FNR to listOf(
                    rad("PERSON_LOPENR" to personLøpenummer, "PERSONNR" to "12345678910"),
                    rad("PERSON_LOPENR" to relatertPersonLøpenummer, "PERSONNR" to "10987654321"),
                ),
                InfotrygdTabeller.T_STONAD to listOf(
                    rad(
                        "STONAD_ID" to stønadId,
                        "PERSON_LOPENR" to personLøpenummer,
                        "DATO_START" to "2019-06-01",
                        "KODE_OPPHOR" to "HI",
                        "DATO_OPPHOR" to "2020-12-31",
                        "OPPDRAG_ID" to "30",
                    ),
                ),
                InfotrygdTabeller.T_VEDTAK to listOf(
                    rad(
                        "VEDTAK_ID" to vedtakId,
                        "STONAD_ID" to stønadId,
                        "TYPE_SAK" to "R",
                        "KODE_RESULTAT" to "FI",
                        "DATO_INNV_FOM" to fraOgMed,
                        "DATO_INNV_TOM" to tilOgMed,
                        "DATO_MOTTATT_SAK" to "2019-12-10",
                        "TKNR" to "1234",
                        "SAKSNR" to "99",
                    ),
                ),
                InfotrygdTabeller.T_STONADSKLASSE to listOf(
                    rad("VEDTAK_ID" to vedtakId, "KODE_NIVAA" to "OR", "KODE_KLASSE" to "EO"),
                ),
                InfotrygdTabeller.T_KLASSENIVAA to listOf(
                    rad("KODE" to "OR", "TEKST" to "Ordinær"),
                ),
                InfotrygdTabeller.T_ROLLE to listOf(
                    rad(
                        "VEDTAK_ID" to vedtakId,
                        "TYPE" to "EP",
                        "FOM" to fraOgMed,
                        "TOM" to tilOgMed,
                        "PERSON_LOPENR_R" to relatertPersonLøpenummer,
                        "BOR_SAMMEN_MED" to "1",
                    ),
                ),
                InfotrygdTabeller.T_SU to listOf(
                    rad(
                        "VEDTAK_ID" to vedtakId,
                        "BELOP_BER_GRUNNLAG" to "191424.00",
                        "REVURDERING_DATO" to "2020-08-01",
                    ),
                ),
                InfotrygdTabeller.T_BELOPSTYPE to listOf(
                    rad("TYPE" to "ARB", "TEKST" to "Arbeidsinntekt", "BEHANDLING" to "S"),
                ),
                InfotrygdTabeller.T_BEREGN_GRL to listOf(
                    rad(
                        "VEDTAK_ID" to vedtakId,
                        "TYPE_BELOP" to "ARB",
                        "FOM" to fraOgMed,
                        "TOM" to tilOgMed,
                        "BELOP" to "12000.00",
                    ),
                ),
                InfotrygdTabeller.T_DELYTELSESTYPE to listOf(
                    rad("TYPE" to "MS", "TEKST" to "Månedsats", "FRADRAG_TILLEGG" to "T"),
                    rad("TYPE" to "FM", "TEKST" to "Fradrag månedsats", "FRADRAG_TILLEGG" to "F"),
                ),
                InfotrygdTabeller.T_DELYTELSE to listOf(
                    rad(
                        "VEDTAK_ID" to vedtakId,
                        "TYPE_DELYTELSE" to "MS",
                        "FOM" to fraOgMed,
                        "TOM" to tilOgMed,
                        "BELOP" to "15010.00",
                        "MOTTAKER_LOPENR" to personLøpenummer,
                        "TYPE_SATS" to satstype,
                        "TYPE_UTBETALING" to utbetalingstype,
                        "LINJE_ID" to linjeId,
                    ),
                    rad(
                        "VEDTAK_ID" to vedtakId,
                        "TYPE_DELYTELSE" to "FM",
                        "FOM" to fraOgMed,
                        "TOM" to tilOgMed,
                        "BELOP" to "5010.00",
                        "MOTTAKER_LOPENR" to personLøpenummer,
                        "TYPE_SATS" to satstype,
                        "TYPE_UTBETALING" to utbetalingstype,
                        "LINJE_ID" to linjeId,
                    ),
                ),
                InfotrygdTabeller.T_ENDRING to listOf(rad("VEDTAK_ID" to vedtakId, "KODE" to "EB")),
                InfotrygdTabeller.T_BESLUT to listOf(
                    rad(
                        "BESLUTNING_ID" to "60",
                        "VEDTAK_ID" to vedtakId,
                        "SAKSBEHANDLER1" to "A123456",
                        "GODKJENT1" to "J",
                    ),
                ),
            ),
        )

        projeksjon.avvik shouldBe emptyList()
        val stønad = projeksjon.stønader.single()
        stønad.personident shouldBe "12345678910"
        stønad.opphør!!.kode.tolketVerdi shouldBe HistoriskOpphørsgrunn.HØY_INNTEKT

        val vedtak = stønad.vedtak.single()
        vedtak.sakstype.tolketVerdi shouldBe HistoriskSakstype.REVURDERING
        vedtak.resultat.tolketVerdi shouldBe HistoriskResultat.FORTSATT_INNVILGET
        vedtak.klassifiseringer.single().klasse.tolketVerdi shouldBe HistoriskBosituasjon.EPS_OVER_67
        vedtak.klassifiseringer.single().nivå!!.tekst shouldBe "Ordinær"
        vedtak.roller.single().relatertPersonident shouldBe "10987654321"
        vedtak.beregning.suDetaljer.single().årligYtelsesbeløp!!.beløp shouldBe BigDecimal("191424.00")
        vedtak.beregning.inntekter.single().also {
            it.type.tekst shouldBe "Arbeidsinntekt"
            it.årligBeløp!!.beløp shouldBe BigDecimal("12000.00")
        }
        vedtak.beregning.delytelser.first().also {
            it.type.fradragEllerTillegg shouldBe "T"
            it.mottakerPersonident shouldBe "12345678910"
        }
        vedtak.beregning.månedsbeløp.single().also {
            it.sats shouldBe BigDecimal("15010.00")
            it.fradrag shouldBe BigDecimal("5010.00")
            it.beløpTilUtbetaling shouldBe BigDecimal("10000.00")
        }
        vedtak.beslutninger.single().førsteSaksbehandler shouldBe "A123456"
    }

    @Test
    fun `bevarer ukjente koder og rapporterer dem som avvik`() {
        val projeksjon = HistoriskAlderDataConverter().konverterRådataBatch(
            tomtDatasett() + mapOf(
                InfotrygdTabeller.T_LOPENR_FNR to listOf(
                    rad("PERSON_LOPENR" to "10", "PERSONNR" to "12345678910"),
                ),
                InfotrygdTabeller.T_STONAD to listOf(rad("STONAD_ID" to "20", "PERSON_LOPENR" to "10")),
                InfotrygdTabeller.T_VEDTAK to listOf(
                    rad(
                        "VEDTAK_ID" to "40",
                        "STONAD_ID" to "20",
                        "TYPE_SAK" to "NY",
                        "KODE_RESULTAT" to "X",
                    ),
                ),
                InfotrygdTabeller.T_STONADSKLASSE to listOf(
                    rad("VEDTAK_ID" to "40", "KODE_NIVAA" to "OR", "KODE_KLASSE" to "ZZ"),
                ),
            ),
        )

        val vedtak = projeksjon.stønader.single().vedtak.single()
        vedtak.sakstype.råverdi shouldBe "NY"
        vedtak.sakstype.tolketVerdi shouldBe null
        vedtak.resultat.råverdi shouldBe "X"
        vedtak.klassifiseringer.single().klasse.råverdi shouldBe "ZZ"
        projeksjon.avvik shouldContain HistoriskAlderProjeksjonsavvik.UkjentKode(
            InfotrygdTabeller.T_VEDTAK,
            "TYPE_SAK",
            "NY",
        )
    }

    @Test
    fun `tolker sakstyper og resultater bekreftet i reelle SU-data`() {
        val vedtak = listOf(
            Triple("40", "MG", "FI"),
            Triple("41", "MO", "FI"),
            Triple("42", "GO", "IN"),
            Triple("43", "MS", "Ø"),
            Triple("44", "K", "R"),
            Triple("45", "MB", "FI"),
            Triple("46", "FL", "FI"),
        )
        val projeksjon = HistoriskAlderDataConverter().konverterRådataBatch(
            tomtDatasett() + mapOf(
                InfotrygdTabeller.T_LOPENR_FNR to listOf(
                    rad("PERSON_LOPENR" to "10", "PERSONNR" to "12345678910"),
                ),
                InfotrygdTabeller.T_STONAD to listOf(rad("STONAD_ID" to "20", "PERSON_LOPENR" to "10")),
                InfotrygdTabeller.T_VEDTAK to vedtak.map { (vedtakId, sakstype, resultat) ->
                    rad(
                        "VEDTAK_ID" to vedtakId,
                        "STONAD_ID" to "20",
                        "TYPE_SAK" to sakstype,
                        "KODE_RESULTAT" to resultat,
                    )
                },
            ),
        )

        projeksjon.avvik shouldBe emptyList()
        projeksjon.stønader.single().vedtak.also {
            it.map { vedtak -> vedtak.sakstype.tolketVerdi } shouldBe listOf(
                HistoriskSakstype.MASKINELL_OMREGNING,
                HistoriskSakstype.MANUELL_OMREGNING,
                HistoriskSakstype.MANUELL_G_REGULERING,
                HistoriskSakstype.MASKINELL_SATSOMREGNING,
                HistoriskSakstype.KLAGE,
                HistoriskSakstype.MASKINELL_BEREGNING,
                HistoriskSakstype.FLYTTESAK,
            )
            it.map { vedtak -> vedtak.resultat.tolketVerdi } shouldBe listOf(
                HistoriskResultat.FORTSATT_INNVILGET,
                HistoriskResultat.FORTSATT_INNVILGET,
                HistoriskResultat.INNVILGET_NY_SITUASJON,
                HistoriskResultat.ØKNING,
                HistoriskResultat.REDUSERT,
                HistoriskResultat.FORTSATT_INNVILGET,
                HistoriskResultat.FORTSATT_INNVILGET,
            )
        }
    }

    @Test
    fun `bruker null kroner i fradrag når månedsatsen ikke har en fradragslinje`() {
        val stønadId = "20"
        val vedtakId = "40"
        val fraOgMed = "2020-01-01"
        val tilOgMed = "2020-12-31"
        val projeksjon = HistoriskAlderDataConverter().konverterRådataBatch(
            tomtDatasett() + mapOf(
                InfotrygdTabeller.T_STONAD to listOf(
                    rad("STONAD_ID" to stønadId, "PERSON_LOPENR" to "10"),
                ),
                InfotrygdTabeller.T_VEDTAK to listOf(
                    rad(
                        "VEDTAK_ID" to vedtakId,
                        "STONAD_ID" to stønadId,
                        "TYPE_SAK" to "S",
                        "KODE_RESULTAT" to "I",
                    ),
                ),
                InfotrygdTabeller.T_DELYTELSESTYPE to listOf(
                    rad("TYPE" to "MS", "TEKST" to "Månedsats", "FRADRAG_TILLEGG" to "T"),
                ),
                InfotrygdTabeller.T_DELYTELSE to listOf(
                    rad(
                        "VEDTAK_ID" to vedtakId,
                        "TYPE_DELYTELSE" to "MS",
                        "FOM" to fraOgMed,
                        "TOM" to tilOgMed,
                        "BELOP" to "15010.00",
                        "TYPE_SATS" to "M",
                        "TYPE_UTBETALING" to "L",
                        "LINJE_ID" to "50",
                    ),
                ),
            ),
        )

        projeksjon.avvik shouldBe emptyList()
        projeksjon.stønader.single().vedtak.single().beregning.månedsbeløp.single().also {
            it.sats shouldBe BigDecimal("15010.00")
            it.fradrag shouldBe BigDecimal.ZERO
            it.beløpTilUtbetaling shouldBe BigDecimal("15010.00")
        }
    }

    @Test
    fun `forkaster månedsbeløp når fradragslinjen har ugyldig beløp`() {
        val projeksjon = HistoriskAlderDataConverter().konverterRådataBatch(
            tomtDatasett() + mapOf(
                InfotrygdTabeller.T_STONAD to listOf(
                    rad("STONAD_ID" to "20", "PERSON_LOPENR" to "10"),
                ),
                InfotrygdTabeller.T_VEDTAK to listOf(
                    rad(
                        "VEDTAK_ID" to "40",
                        "STONAD_ID" to "20",
                        "TYPE_SAK" to "S",
                        "KODE_RESULTAT" to "I",
                    ),
                ),
                InfotrygdTabeller.T_DELYTELSESTYPE to listOf(
                    rad("TYPE" to "MS", "TEKST" to "Månedsats", "FRADRAG_TILLEGG" to "T"),
                    rad("TYPE" to "FM", "TEKST" to "Fradrag månedsats", "FRADRAG_TILLEGG" to "F"),
                ),
                InfotrygdTabeller.T_DELYTELSE to listOf(
                    rad(
                        "VEDTAK_ID" to "40",
                        "TYPE_DELYTELSE" to "MS",
                        "FOM" to "2020-01-01",
                        "TOM" to "2020-12-31",
                        "BELOP" to "15010.00",
                        "TYPE_SATS" to "M",
                        "TYPE_UTBETALING" to "L",
                        "LINJE_ID" to "50",
                    ),
                    rad(
                        "VEDTAK_ID" to "40",
                        "TYPE_DELYTELSE" to "FM",
                        "FOM" to "2020-01-01",
                        "TOM" to "2020-12-31",
                        "BELOP" to "ugyldig",
                        "TYPE_SATS" to "M",
                        "TYPE_UTBETALING" to "L",
                        "LINJE_ID" to "50",
                    ),
                ),
            ),
        )

        projeksjon.stønader.single().vedtak.single().beregning.månedsbeløp shouldBe emptyList()
        projeksjon.avvik shouldContain HistoriskAlderProjeksjonsavvik.UgyldigBeløp(
            InfotrygdTabeller.T_DELYTELSE,
            "BELOP",
            "40",
            "ugyldig",
        )
        projeksjon.avvik shouldContain HistoriskAlderProjeksjonsavvik.UgyldigDelytelsesbeløp(
            vedtakId = "40",
            fraOgMed = "2020-01-01",
            tilOgMed = "2020-12-31",
            linjeId = "50",
            sats = BigDecimal("15010.00"),
            fradrag = null,
        )
    }

    @Test
    fun `forkaster månedsbeløp med baklengs delytelsesperiode`() {
        val projeksjon = HistoriskAlderDataConverter().konverterRådataBatch(
            tomtDatasett() + mapOf(
                InfotrygdTabeller.T_STONAD to listOf(
                    rad("STONAD_ID" to "20", "PERSON_LOPENR" to "10"),
                ),
                InfotrygdTabeller.T_VEDTAK to listOf(
                    rad(
                        "VEDTAK_ID" to "40",
                        "STONAD_ID" to "20",
                        "TYPE_SAK" to "S",
                        "KODE_RESULTAT" to "I",
                    ),
                ),
                InfotrygdTabeller.T_DELYTELSESTYPE to listOf(
                    rad("TYPE" to "MS", "TEKST" to "Månedsats", "FRADRAG_TILLEGG" to "T"),
                ),
                InfotrygdTabeller.T_DELYTELSE to listOf(
                    rad(
                        "VEDTAK_ID" to "40",
                        "TYPE_DELYTELSE" to "MS",
                        "FOM" to "2020-12-31",
                        "TOM" to "2020-01-01",
                        "BELOP" to "15010.00",
                        "TYPE_SATS" to "M",
                        "TYPE_UTBETALING" to "L",
                        "LINJE_ID" to "50",
                    ),
                ),
            ),
        )

        projeksjon.stønader.single().vedtak.single().beregning.månedsbeløp shouldBe emptyList()
        projeksjon.avvik shouldContain HistoriskAlderProjeksjonsavvik.UgyldigDelytelsesperiode(
            vedtakId = "40",
            fraOgMed = "2020-12-31",
            tilOgMed = "2020-01-01",
            linjeId = "50",
        )
    }

    @Test
    fun `konverterInfotrygdRådata prosesserer stønader batchvis uten å laste alt i minnet`() {
        val importId = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001")
        val leser = FakeHistoriskRådataLeser(
            referansetabeller = mapOf(
                InfotrygdTabeller.T_LOPENR_FNR to listOf(
                    mapOf("PERSON_LOPENR" to "10", "PERSONNR" to "12345678910"),
                    mapOf("PERSON_LOPENR" to "12", "PERSONNR" to "12121212121"),
                ),
                InfotrygdTabeller.T_BELOPSTYPE to emptyList(),
                InfotrygdTabeller.T_DELYTELSESTYPE to emptyList(),
                InfotrygdTabeller.T_KLASSENIVAA to emptyList(),
            ),
            stønader = listOf(
                mapOf("STONAD_ID" to "20", "PERSON_LOPENR" to "10", "DATO_START" to "2019-06-01"),
                mapOf("STONAD_ID" to "21", "PERSON_LOPENR" to "10", "DATO_START" to "2020-01-01"),
            ),
            vedtakPerStønad = mapOf(
                "20" to listOf(
                    mapOf("VEDTAK_ID" to "40", "STONAD_ID" to "20", "TYPE_SAK" to "S", "KODE_RESULTAT" to "I"),
                ),
                "21" to listOf(
                    mapOf("VEDTAK_ID" to "41", "STONAD_ID" to "21", "TYPE_SAK" to "R", "KODE_RESULTAT" to "FI"),
                ),
            ),
            raderPerVedtak = emptyMap(),
            delytelserPerVedtak = mapOf(
                "40" to listOf(mapOf("VEDTAK_ID" to "40", "MOTTAKER_LOPENR" to "12")),
            ),
        )

        val samlet = mutableListOf<HistoriskAldersstønad>()
        val resultat = HistoriskAlderDataConverter()
            .konverterInfotrygdRådata(importId, leser, batchSize = 1) { samlet.addAll(it) }

        resultat.antallStønader shouldBe 2
        samlet.size shouldBe 2
        samlet[0].stønadId.value shouldBe "20"
        samlet[1].stønadId.value shouldBe "21"
        leser.antallBatchkall shouldBe 2
        leser.oppslåtteLopenummer shouldContain "12"
    }

    private fun tomtDatasett(): Map<String, List<Map<String, String?>>> = setOf(
        InfotrygdTabeller.T_BELOPSTYPE,
        InfotrygdTabeller.T_BEREGN_GRL,
        InfotrygdTabeller.T_BESLUT,
        InfotrygdTabeller.T_DELYTELSE,
        InfotrygdTabeller.T_DELYTELSESTYPE,
        InfotrygdTabeller.T_ENDRING,
        InfotrygdTabeller.T_KLASSENIVAA,
        InfotrygdTabeller.T_LOPENR_FNR,
        InfotrygdTabeller.T_ROLLE,
        InfotrygdTabeller.T_STONAD,
        InfotrygdTabeller.T_STONADSKLASSE,
        InfotrygdTabeller.T_SU,
        InfotrygdTabeller.T_VEDTAK,
    ).associateWith { emptyList() }

    private fun rad(vararg verdier: Pair<String, String?>): Map<String, String?> = mapOf(*verdier)
}

private class FakeHistoriskRådataLeser(
    private val referansetabeller: Map<String, List<Map<String, String?>>>,
    private val stønader: List<Map<String, String?>>,
    private val vedtakPerStønad: Map<String, List<Map<String, String?>>>,
    private val raderPerVedtak: Map<String, List<Map<String, String?>>>,
    private val delytelserPerVedtak: Map<String, List<Map<String, String?>>> = emptyMap(),
) : no.nav.su.se.bakover.domain.historisk.HistoriskRådataLeser {
    var antallBatchkall = 0
        private set
    val oppslåtteLopenummer = mutableSetOf<String>()

    override fun verifiserFullførtImport(importId: UUID) {
        // Antar fullført i tester
    }

    override fun hentReferansetabell(importId: UUID, tabellnavn: String): List<Map<String, String?>> =
        referansetabeller[tabellnavn].orEmpty()

    override fun hentStønaderBatchvis(importId: UUID, batchSize: Int, handler: (List<Map<String, String?>>) -> Unit) {
        stønader.chunked(batchSize).forEach {
            antallBatchkall++
            handler(it)
        }
    }

    override fun hentVedtakForStønader(importId: UUID, stønadIder: Set<String>): List<Map<String, String?>> =
        stønadIder.flatMap { vedtakPerStønad[it].orEmpty() }

    override fun hentRaderForVedtak(
        importId: UUID,
        tabellnavn: String,
        vedtakIder: Set<String>,
    ): List<Map<String, String?>> =
        vedtakIder.flatMap {
            if (tabellnavn == InfotrygdTabeller.T_DELYTELSE) {
                delytelserPerVedtak[it].orEmpty()
            } else {
                raderPerVedtak[it].orEmpty()
            }
        }

    override fun hentPersonerForLopenummer(
        importId: UUID,
        lopenummer: Set<String>,
    ): Map<String, Map<String, String?>> {
        oppslåtteLopenummer.addAll(lopenummer)
        return referansetabeller[InfotrygdTabeller.T_LOPENR_FNR].orEmpty()
            .map { rad -> rad.entries.associate { (k, v) -> k.uppercase() to v } }
            .filter { it["PERSON_LOPENR"] in lopenummer }
            .associateBy { it["PERSON_LOPENR"]!! }
    }
}
