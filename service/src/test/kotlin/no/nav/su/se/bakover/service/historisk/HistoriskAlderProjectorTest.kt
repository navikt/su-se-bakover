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
        val projeksjon = HistoriskAlderDataConverter().konverterRådataBatch(
            tomtDatasett() + mapOf(
                InfotrygdTabeller.T_LOPENR_FNR to listOf(
                    rad("PERSON_LOPENR" to "10", "PERSONNR" to "12345678910"),
                    rad("PERSON_LOPENR" to "11", "PERSONNR" to "10987654321"),
                ),
                InfotrygdTabeller.T_STONAD to listOf(
                    rad(
                        "STONAD_ID" to "20",
                        "PERSON_LOPENR" to "10",
                        "DATO_START" to "2019-06-01",
                        "KODE_OPPHOR" to "HI",
                        "DATO_OPPHOR" to "2020-12-31",
                        "OPPDRAG_ID" to "30",
                    ),
                ),
                InfotrygdTabeller.T_VEDTAK to listOf(
                    rad(
                        "VEDTAK_ID" to "40",
                        "STONAD_ID" to "20",
                        "TYPE_SAK" to "R",
                        "KODE_RESULTAT" to "FI",
                        "DATO_INNV_FOM" to "2020-01-01",
                        "DATO_INNV_TOM" to "2020-12-31",
                        "DATO_MOTTATT_SAK" to "2019-12-10",
                        "TKNR" to "1234",
                        "SAKSNR" to "99",
                    ),
                ),
                InfotrygdTabeller.T_STONADSKLASSE to listOf(
                    rad("VEDTAK_ID" to "40", "KODE_NIVAA" to "OR", "KODE_KLASSE" to "EO"),
                ),
                InfotrygdTabeller.T_KLASSENIVAA to listOf(
                    rad("KODE" to "OR", "TEKST" to "Ordinær"),
                ),
                InfotrygdTabeller.T_ROLLE to listOf(
                    rad(
                        "VEDTAK_ID" to "40",
                        "TYPE" to "EP",
                        "FOM" to "2020-01-01",
                        "TOM" to "2020-12-31",
                        "PERSON_LOPENR_R" to "11",
                        "BOR_SAMMEN_MED" to "1",
                    ),
                ),
                InfotrygdTabeller.T_SU to listOf(
                    rad(
                        "VEDTAK_ID" to "40",
                        "BELOP_BER_GRUNNLAG" to "192125.00",
                        "REVURDERING_DATO" to "2020-08-01",
                    ),
                ),
                InfotrygdTabeller.T_BELOPSTYPE to listOf(
                    rad("TYPE" to "ARB", "TEKST" to "Arbeidsinntekt", "BEHANDLING" to "S"),
                ),
                InfotrygdTabeller.T_BEREGN_GRL to listOf(
                    rad(
                        "VEDTAK_ID" to "40",
                        "TYPE_BELOP" to "ARB",
                        "FOM" to "2020-01-01",
                        "TOM" to "2020-12-31",
                        "BELOP" to "12000.00",
                    ),
                ),
                InfotrygdTabeller.T_DELYTELSESTYPE to listOf(
                    rad("TYPE" to "SU", "TEKST" to "Supplerende stønad", "FRADRAG_TILLEGG" to "+"),
                ),
                InfotrygdTabeller.T_DELYTELSE to listOf(
                    rad(
                        "VEDTAK_ID" to "40",
                        "TYPE_DELYTELSE" to "SU",
                        "FOM" to "2020-01-01",
                        "TOM" to "2020-12-31",
                        "BELOP" to "15010.00",
                        "MOTTAKER_LOPENR" to "10",
                        "LINJE_ID" to "50",
                    ),
                ),
                InfotrygdTabeller.T_ENDRING to listOf(rad("VEDTAK_ID" to "40", "KODE" to "EB")),
                InfotrygdTabeller.T_BESLUT to listOf(
                    rad(
                        "BESLUTNING_ID" to "60",
                        "VEDTAK_ID" to "40",
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
        vedtak.beregning.suDetaljer.single().valgtBeregningsgrunnlag!!.beløp shouldBe BigDecimal("192125.00")
        vedtak.beregning.inntekter.single().also {
            it.type.tekst shouldBe "Arbeidsinntekt"
            it.årligBeløp!!.beløp shouldBe BigDecimal("12000.00")
        }
        vedtak.beregning.delytelser.single().also {
            it.type.fradragEllerTillegg shouldBe "+"
            it.mottakerPersonident shouldBe "12345678910"
        }
        vedtak.beslutninger.single().førsteSaksbehandler shouldBe "A123456"
    }

    @Test
    fun `bevarer ukjente koder og rapporterer dem som avvik`() {
        val projeksjon = HistoriskAlderDataConverter().konverterRådataBatch(
            tomtDatasett() + mapOf(
                "T_LOPENR_FNR" to listOf(rad("PERSON_LOPENR" to "10", "PERSONNR" to "12345678910")),
                "T_STONAD" to listOf(rad("STONAD_ID" to "20", "PERSON_LOPENR" to "10")),
                "T_VEDTAK" to listOf(
                    rad(
                        "VEDTAK_ID" to "40",
                        "STONAD_ID" to "20",
                        "TYPE_SAK" to "NY",
                        "KODE_RESULTAT" to "X",
                    ),
                ),
                "T_STONADSKLASSE" to listOf(
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
            "T_VEDTAK",
            "TYPE_SAK",
            "NY",
        )
    }

    @Test
    fun `konverterInfotrygdRådata prosesserer stønader batchvis uten å laste alt i minnet`() {
        val importId = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001")
        val leser = FakeHistoriskRådataLeser(
            referansetabeller = mapOf(
                InfotrygdTabeller.T_LOPENR_FNR to listOf(
                    mapOf("PERSON_LOPENR" to "10", "PERSONNR" to "12345678910"),
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
        )

        val samlet = mutableListOf<HistoriskAldersstønad>()
        val resultat = HistoriskAlderDataConverter()
            .konverterInfotrygdRådata(importId, leser, batchSize = 1) { samlet.addAll(it) }

        resultat.antallStønader shouldBe 2
        samlet.size shouldBe 2
        samlet[0].stønadId.value shouldBe "20"
        samlet[1].stønadId.value shouldBe "21"
        leser.antallBatchkall shouldBe 2
    }

    private fun tomtDatasett(): Map<String, List<Map<String, String?>>> = setOf(
        "T_BELOPSTYPE",
        "T_BEREGN_GRL",
        "T_BESLUT",
        "T_DELYTELSE",
        "T_DELYTELSESTYPE",
        "T_ENDRING",
        "T_KLASSENIVAA",
        "T_LOPENR_FNR",
        "T_ROLLE",
        "T_STONAD",
        "T_STONADSKLASSE",
        "T_SU",
        "T_VEDTAK",
    ).associateWith { emptyList() }

    private fun rad(vararg verdier: Pair<String, String?>): Map<String, String?> = mapOf(*verdier)
}

private class FakeHistoriskRådataLeser(
    private val referansetabeller: Map<String, List<Map<String, String?>>>,
    private val stønader: List<Map<String, String?>>,
    private val vedtakPerStønad: Map<String, List<Map<String, String?>>>,
    private val raderPerVedtak: Map<String, List<Map<String, String?>>>,
) : no.nav.su.se.bakover.domain.historisk.HistoriskRådataLeser {
    var antallBatchkall = 0
        private set

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
        vedtakIder.flatMap { raderPerVedtak[it].orEmpty() }

    override fun hentPersonerForLopenummer(importId: UUID, lopenummer: Set<String>): Map<String, Map<String, String?>> =
        referansetabeller[InfotrygdTabeller.T_LOPENR_FNR].orEmpty()
            .map { rad -> rad.entries.associate { (k, v) -> k.uppercase() to v } }
            .filter { it["PERSON_LOPENR"] in lopenummer }
            .associateBy { it["PERSON_LOPENR"]!! }
}
