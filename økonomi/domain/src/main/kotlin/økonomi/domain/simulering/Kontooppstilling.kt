//package økonomi.domain.simulering
//
//data class Kontooppstilling(
//    val debetYtelse: Kontobeløp.Debet,
//    val kreditYtelse: Kontobeløp.Kredit,
//    val debetFeilkonto: Kontobeløp.Debet,
//    val kreditFeilkonto: Kontobeløp.Kredit,
//    val debetMotpostFeilkonto: Kontobeløp.Debet,
//    val kreditMotpostFeilkonto: Kontobeløp.Kredit,
//) {
//    val sumUtbetaling = Kontobeløp.Summert(debetYtelse, kreditYtelse)
//    val sumFeilkonto = Kontobeløp.Summert(debetFeilkonto, kreditFeilkonto)
//    val sumMotpostFeilkonto = Kontobeløp.Summert(debetMotpostFeilkonto, kreditMotpostFeilkonto)
//
//    companion object {
//        val EMPTY = Kontooppstilling(
//            debetYtelse = Kontobeløp.Debet(0),
//            kreditYtelse = Kontobeløp.Kredit(0),
//            debetFeilkonto = Kontobeløp.Debet(0),
//            kreditFeilkonto = Kontobeløp.Kredit(0),
//            debetMotpostFeilkonto = Kontobeløp.Debet(0),
//            kreditMotpostFeilkonto = Kontobeløp.Kredit(0),
//        )
//    }
//}
