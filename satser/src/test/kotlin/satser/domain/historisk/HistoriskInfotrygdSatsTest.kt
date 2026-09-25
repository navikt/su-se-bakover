package satser.domain.historisk

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

internal class HistoriskInfotrygdSatsTest {
    @Test
    fun `inneholder alle 26 satsendringer i stigende rekkefølge`() {
        HistoriskInfotrygdSats.entries.size shouldBe 26
        HistoriskInfotrygdSats.entries.map { it.virkningstidspunkt }
            .zipWithNext()
            .all { (før, etter) -> før.isBefore(etter) } shouldBe true
    }

    @Test
    fun `bruker G-faktor til og med 2010`() {
        HistoriskInfotrygdSats.MAI_2010.satsFor(HistoriskInfotrygdSatskategori.EU) shouldBe
            HistoriskInfotrygdSatsverdi.Grunnbeløpsfaktor(BigDecimal("2.5000"))
        HistoriskInfotrygdSats.MAI_2010.satsFor(HistoriskInfotrygdSatskategori.EV) shouldBe null
    }

    @Test
    fun `bruker årsbeløp fra 2011`() {
        HistoriskInfotrygdSats.MAI_2011.satsFor(HistoriskInfotrygdSatskategori.EN) shouldBe
            HistoriskInfotrygdSatsverdi.Årsbeløp(BigDecimal(157_639))
        HistoriskInfotrygdSats.MAI_2011.satsFor(HistoriskInfotrygdSatskategori.EV) shouldBe null
    }

    @Test
    fun `finner siste sats som gjelder på dato`() {
        HistoriskInfotrygdSats.gjeldendePå(LocalDate.of(2017, 8, 31)) shouldBe
            HistoriskInfotrygdSats.MAI_2017
        HistoriskInfotrygdSats.gjeldendePå(LocalDate.of(2017, 9, 1)) shouldBe
            HistoriskInfotrygdSats.SEPTEMBER_2017
        HistoriskInfotrygdSats.gjeldendePå(LocalDate.of(2005, 12, 31)) shouldBe null
    }
}
