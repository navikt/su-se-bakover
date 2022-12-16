package no.nav.su.se.bakover.domain.oppdrag.simulering

data class Kontooppstilling(
    val debetYtelse: Kontobeløp.Debet,
    val kreditYtelse: Kontobeløp.Kredit,
    val debetFeilkonto: Kontobeløp.Debet,
    val kreditFeilkonto: Kontobeløp.Kredit,
    val debetMotpostFeilkonto: Kontobeløp.Debet,
    val kreditMotpostFeilkonto: Kontobeløp.Kredit,
) {
    val sumUtbetaling = Kontobeløp.Summert(debetYtelse, kreditYtelse)
    val sumFeilkonto = Kontobeløp.Summert(debetFeilkonto, kreditFeilkonto)
    val sumMotpostFeilkonto = Kontobeløp.Summert(debetMotpostFeilkonto, kreditMotpostFeilkonto)
}
