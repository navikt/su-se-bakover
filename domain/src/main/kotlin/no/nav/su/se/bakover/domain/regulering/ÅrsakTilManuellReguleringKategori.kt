package no.nav.su.se.bakover.domain.regulering

enum class ÅrsakTilManuellReguleringKategori {
    FradragMåHåndteresManuelt,
    YtelseErMidlertidigStanset,
    UtbetalingFeilet,
    SupplementInneholderIkkeFradraget,
    FinnesFlerePerioderAvFradrag,
    FradragErUtenlandsinntekt,
    SupplementHarFlereVedtaksperioderForFradrag,
    DifferanseFørRegulering,
    DifferanseEtterRegulering,
    FantIkkeVedtakForApril,
    VedtakstidslinjeErIkkeSammenhengende,
    DelvisOpphør,
    MerEnn1Eps,
    ManglerRegulertBeløpForFradrag,
    ManglerIeuFraPesys,
    EtAutomatiskFradragHarFremtidigPeriode,

    // Gammel
    BrukerManglerSupplement,
    ForventetInntektErStørreEnn0,
    AutomatiskSendingTilUtbetalingFeilet,
}
