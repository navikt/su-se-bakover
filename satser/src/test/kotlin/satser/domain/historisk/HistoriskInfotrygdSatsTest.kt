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
    fun `avrunder månedssatsen til hele kroner slik Infotrygd registrerte MS`() {
        // 191 422 / 12 = 15 951,83
        HistoriskInfotrygdSats.beregnMånedssats(LocalDate.of(2020, 1, 1), HistoriskInfotrygdSatskategori.EN)!!
            .månedssats shouldBe BigDecimal(15_952)
    }

    @Test
    fun `finner halvt grunnbeløp også før 2020`() {
        // G fra 1. mai 2012: 82 122
        HistoriskInfotrygdSats.halvtGrunnbeløpPerÅrAvrundet(LocalDate.of(2013, 2, 1)) shouldBe 41_061
        HistoriskInfotrygdSats.halvtGrunnbeløpPerÅrAvrundet(LocalDate.of(2005, 4, 30)) shouldBe null
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
