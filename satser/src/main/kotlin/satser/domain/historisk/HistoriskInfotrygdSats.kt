package satser.domain.historisk

import java.math.BigDecimal
import java.time.LocalDate

enum class HistoriskInfotrygdSats(
    val virkningstidspunkt: LocalDate,
    val iverksatt: LocalDate,
    private val satsverdier: Map<HistoriskInfotrygdSatskategori, HistoriskInfotrygdSatsverdi>,
) {
    JANUAR_2006(dato(2006, 1, 1), dato(2006, 1, 1), faktorer("1.7933", "2.2933", "1.6433")),
    MAI_2006(dato(2006, 5, 1), dato(2006, 5, 1), faktorer("1.7933", "2.2933", "1.6433")),
    MAI_2007(dato(2007, 5, 1), dato(2007, 5, 1), faktorer("1.7933", "2.2933", "1.6433")),
    MAI_2008(dato(2008, 5, 1), dato(2008, 5, 1), faktorer("1.9400", "2.4400", "1.7900")),
    MAI_2009(dato(2009, 5, 1), dato(2009, 5, 1), faktorer("1.9700", "2.4700", "1.8200")),
    MAI_2010(dato(2010, 5, 1), dato(2010, 6, 25), faktorer("2.0000", "2.5000", "1.8500")),
    MAI_2011(dato(2011, 5, 1), dato(2011, 6, 25), årsbeløp(157_639, 197_049, 145_822)),
    MAI_2012(dato(2012, 5, 1), dato(2012, 6, 23), årsbeløp(162_615, 203_269, 150_425)),
    MAI_2013(dato(2013, 5, 1), dato(2013, 6, 15), årsbeløp(167_963, 209_954, 155_372)),
    MAI_2014(dato(2014, 5, 1), dato(2014, 6, 14), årsbeløp(173_274, 216_593, 160_285)),
    MAI_2015(dato(2015, 5, 1), dato(2015, 5, 25), årsbeløp(175_739, 219_674, 162_566)),
    JANUAR_2016(dato(2016, 1, 1), dato(2016, 5, 22), årsbeløpMedEv(175_739, 175_739, 162_566, 162_566)),
    MAI_2016(dato(2016, 5, 1), dato(2016, 5, 22), årsbeløpMedEv(179_748, 179_748, 166_274, 166_274)),
    SEPTEMBER_2016(dato(2016, 9, 1), dato(2016, 8, 10), årsbeløpMedEv(179_748, 179_748, 170_765, 170_765)),
    MAI_2017(dato(2017, 5, 1), dato(2017, 5, 21), årsbeløpMedEv(180_744, 180_744, 171_711, 171_711)),
    SEPTEMBER_2017(dato(2017, 9, 1), dato(2017, 8, 20), årsbeløpMedEv(181_744, 181_744, 172_711, 172_711)),
    MAI_2018(dato(2018, 5, 1), dato(2018, 5, 27), årsbeløpMedEv(186_968, 186_968, 177_675, 177_675)),
    MAI_2019(dato(2019, 5, 1), dato(2019, 5, 25), årsbeløpMedEv(191_422, 191_422, 181_908, 181_908)),
    MAI_2020(dato(2020, 5, 1), dato(2020, 9, 18), årsbeløpMedEv(193_188, 193_188, 183_587, 183_587)),
    JANUAR_2021(dato(2021, 1, 1), dato(2020, 12, 31), årsbeløpMedEv(192_125, 192_125, 177_724, 177_724)),
    MAI_2021(dato(2021, 5, 1), dato(2021, 11, 19), årsbeløpMedEv(202_425, 202_425, 187_252, 187_252)),
    MAI_2022(dato(2022, 5, 1), dato(2022, 5, 21), årsbeløpMedEv(209_571, 209_571, 193_862, 193_862)),
    MAI_2023(dato(2023, 5, 1), dato(2023, 5, 27), årsbeløpMedEv(227_468, 227_468, 210_418, 210_418)),
    MAI_2024(dato(2024, 5, 1), dato(2024, 5, 25), årsbeløpMedEv(233_746, 233_746, 216_226, 216_226)),
    MAI_2025(dato(2025, 5, 1), dato(2025, 5, 24), årsbeløpMedEv(242_418, 242_418, 224_248, 224_248)),
    MAI_2026(dato(2026, 5, 1), dato(2026, 5, 23), årsbeløpMedEv(253_787, 253_787, 234_765, 234_765)),
    ;

    fun satsFor(kategori: HistoriskInfotrygdSatskategori): HistoriskInfotrygdSatsverdi? =
        satsverdier[kategori]

    companion object {
        fun gjeldendePå(dato: LocalDate): HistoriskInfotrygdSats? =
            entries.lastOrNull { !it.virkningstidspunkt.isAfter(dato) }
    }
}

enum class HistoriskInfotrygdSatskategori {
    EN,
    EU,
    EO,
    EV,
}

sealed interface HistoriskInfotrygdSatsverdi {
    data class Årsbeløp(val beløp: BigDecimal) : HistoriskInfotrygdSatsverdi
    data class Grunnbeløpsfaktor(val faktor: BigDecimal) : HistoriskInfotrygdSatsverdi
}

private fun dato(år: Int, måned: Int, dag: Int): LocalDate = LocalDate.of(år, måned, dag)

private fun faktorer(
    enslig: String,
    ektefelleUnder67: String,
    ektefelleOver67: String,
): Map<HistoriskInfotrygdSatskategori, HistoriskInfotrygdSatsverdi> = mapOf(
    HistoriskInfotrygdSatskategori.EN to HistoriskInfotrygdSatsverdi.Grunnbeløpsfaktor(BigDecimal(enslig)),
    HistoriskInfotrygdSatskategori.EU to HistoriskInfotrygdSatsverdi.Grunnbeløpsfaktor(BigDecimal(ektefelleUnder67)),
    HistoriskInfotrygdSatskategori.EO to HistoriskInfotrygdSatsverdi.Grunnbeløpsfaktor(BigDecimal(ektefelleOver67)),
)

private fun årsbeløp(
    enslig: Int,
    ektefelleUnder67: Int,
    ektefelleOver67: Int,
): Map<HistoriskInfotrygdSatskategori, HistoriskInfotrygdSatsverdi> = mapOf(
    HistoriskInfotrygdSatskategori.EN to HistoriskInfotrygdSatsverdi.Årsbeløp(BigDecimal(enslig)),
    HistoriskInfotrygdSatskategori.EU to HistoriskInfotrygdSatsverdi.Årsbeløp(BigDecimal(ektefelleUnder67)),
    HistoriskInfotrygdSatskategori.EO to HistoriskInfotrygdSatsverdi.Årsbeløp(BigDecimal(ektefelleOver67)),
)

private fun årsbeløpMedEv(
    enslig: Int,
    ektefelleUnder67: Int,
    ektefelleOver67: Int,
    ensligMedBofellesskap: Int,
): Map<HistoriskInfotrygdSatskategori, HistoriskInfotrygdSatsverdi> =
    årsbeløp(enslig, ektefelleUnder67, ektefelleOver67) +
        (
            HistoriskInfotrygdSatskategori.EV to
                HistoriskInfotrygdSatsverdi.Årsbeløp(BigDecimal(ensligMedBofellesskap))
            )
