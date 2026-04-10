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
    AutomatiskSendingTilUtbetalingFeilet,
    VedtakstidslinjeErIkkeSammenhengende,
    DelvisOpphør,
    MerEnn1Eps,
    ManglerRegulertBeløpForFradrag,
    ManglerIeuFraPesys,

    // Gammel
    BrukerManglerSupplement,
    ForventetInntektErStørreEnn0,
}
