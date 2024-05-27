package no.nav.su.se.bakover.domain.regulering

enum class ÅrsakTilManuellReguleringKategori {
    FradragMåHåndteresManuelt,
    YtelseErMidlertidigStanset,
    ForventetInntektErStørreEnn0,
    UtbetalingFeilet,
    BrukerManglerSupplement,
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
}
