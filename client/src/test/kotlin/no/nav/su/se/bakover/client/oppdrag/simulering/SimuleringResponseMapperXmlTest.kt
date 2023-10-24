package no.nav.su.se.bakover.client.oppdrag.simulering

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.infrastructure.xml.xmlMapper
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.common.tid.periode.september
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.simulering.simuleringDobbelTilbakeføringMedTrekkXml
import no.nav.su.se.bakover.test.simulering.simuleringUtbetalingRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse
import org.junit.jupiter.api.Test
import økonomi.domain.simulering.Kontobeløp
import økonomi.domain.simulering.Kontooppstilling
import økonomi.domain.simulering.PeriodeOppsummering
import økonomi.domain.simulering.SimuleringsOppsummering

/**
 * Tester XML vi har mottatt fra oppdrag ved å simulere ting i preprod/prod. Filene ligger i tilknytning til SimuleringTestData.
 */
internal class SimuleringResponseMapperXmlTest {
    @Test
    fun `dobbel tilbakeføring med trekk`() {
        // Da skal oktober bli etterbetalt, mens ikke månedene etter.
        val clock = fixedClockAt(1.november(2023))
        val response = xmlMapper.readValue<SimulerBeregningResponse>(simuleringDobbelTilbakeføringMedTrekkXml)
        val request = simuleringUtbetalingRequest(
            saksnummer = Saksnummer(2021),
            clock = clock,
        )
        val actualSimulering = response.toSimulering(
            request = request,
            clock = clock,
            soapRequest = SimuleringRequestBuilder(request).build(),
        ).getOrFail()
        actualSimulering.kontooppstilling() shouldBe mapOf(
            september(2023) to Kontooppstilling.EMPTY.copy(
                // Dette kan leses som bruker har fått utbetalt 4755 (tilbakeføringen). Brukeren skal ende opp med 4755 (debet). Summert skal det ikke utbetales noe.
                debetYtelse = Kontobeløp.Debet(4755), // Ingen positiv tilbakeføring her, dvs. brukeren har fått utbetalt pengene.
                kreditYtelse = Kontobeløp.Kredit( -4755), // negativ tilbakeføring
            ),
            oktober(2023) to Kontooppstilling.EMPTY.copy(
                // Dette kan leses som brukeren har ikke fått utbetalt noe (tilbakeføringene utligner hverandre). Brukeren skal ende opp med 4755 (debet). Summert skal det utbetales 4755.
                debetYtelse = Kontobeløp.Debet(9510), // 4755 * 2 (Den ene er en positiv tilbakeføring (litt uvanlig), det andre er det brukeren til slutt skal enda opp med på kontoen (uavhengig av hva vi allerede har utbetalt )
                kreditYtelse = Kontobeløp.Kredit( -4755), // negativ tilbakeføring
            ).also { it.sumUtbetaling shouldBe Kontobeløp.Summert(4755) },
            november(2023) to Kontooppstilling.EMPTY.copy(
                debetYtelse = Kontobeløp.Debet(4755),
            ),
            desember(2023) to Kontooppstilling.EMPTY.copy(
                debetYtelse = Kontobeløp.Debet(4755),
            ),
            januar(2024) to Kontooppstilling.EMPTY.copy(
                debetYtelse = Kontobeløp.Debet(4755),
            ),
            februar(2024) to Kontooppstilling.EMPTY.copy(
                debetYtelse = Kontobeløp.Debet(4755),
            ),
        )
        actualSimulering.oppsummering() shouldBe SimuleringsOppsummering(
            totalOppsummering = PeriodeOppsummering(
                periode = Periode.create(
                    fraOgMed = 1.september(2023),
                    tilOgMed = 29.februar(2024),
                ),
                sumTilUtbetaling = 4755*5, // okt, nov, des, jan, feb
                sumEtterbetaling = 4755, // okt
                sumFramtidigUtbetaling = 4755*5, // okt, nov, des, jan, feb
                sumTotalUtbetaling = 4755*6,
                sumTidligereUtbetalt = 4755,
                sumFeilutbetaling = 0,
                sumReduksjonFeilkonto = 0
            ),
            periodeOppsummering = listOf(
                PeriodeOppsummering(
                    periode = september(2023),
                    sumTilUtbetaling = 0,
                    sumEtterbetaling = 0,
                    sumFramtidigUtbetaling = 0,
                    sumTotalUtbetaling = 4755,
                    sumTidligereUtbetalt = 4755,
                    sumFeilutbetaling = 0,
                    sumReduksjonFeilkonto = 0

                ),
                PeriodeOppsummering(
                    periode = oktober(2023),
                    sumTilUtbetaling = 4755,
                    sumEtterbetaling = 4755,
                    sumFramtidigUtbetaling = 0,
                    sumTotalUtbetaling = 4755,
                    sumTidligereUtbetalt = 0,
                    sumFeilutbetaling = 0,
                    sumReduksjonFeilkonto = 0
                ),
                PeriodeOppsummering(
                    periode = november(2023),
                    sumTilUtbetaling = 4755,
                    sumEtterbetaling = 0,
                    sumFramtidigUtbetaling = 4755,
                    sumTotalUtbetaling = 4755,
                    sumTidligereUtbetalt = 0,
                    sumFeilutbetaling = 0,
                    sumReduksjonFeilkonto = 0

                ),
                PeriodeOppsummering(
                    periode = desember(2023),
                    sumTilUtbetaling = 4755,
                    sumEtterbetaling = 0,
                    sumFramtidigUtbetaling = 4755,
                    sumTotalUtbetaling = 4755,
                    sumTidligereUtbetalt = 0,
                    sumFeilutbetaling = 0,
                    sumReduksjonFeilkonto = 0

                ),
                PeriodeOppsummering(
                    periode = januar(2024),
                    sumTilUtbetaling = 4755,
                    sumEtterbetaling = 0,
                    sumFramtidigUtbetaling = 4755,
                    sumTotalUtbetaling = 4755,
                    sumTidligereUtbetalt = 0,
                    sumFeilutbetaling = 0,
                    sumReduksjonFeilkonto = 0

                ),
                PeriodeOppsummering(
                    periode = februar(2024),
                    sumTilUtbetaling = 4755,
                    sumEtterbetaling = 0,
                    sumFramtidigUtbetaling = 4755,
                    sumTotalUtbetaling = 4755,
                    sumTidligereUtbetalt = 0,
                    sumFeilutbetaling = 0,
                    sumReduksjonFeilkonto = 0
                ),
            )
        )
    }
}
