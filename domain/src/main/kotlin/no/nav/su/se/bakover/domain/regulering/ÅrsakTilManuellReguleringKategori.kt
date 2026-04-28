package no.nav.su.se.bakover.domain.regulering

enum class ÅrsakTilManuellReguleringKategori {
    ManglerRegulertBeløpForFradrag,
    ManglerIeuFraPesys,
    YtelseErMidlertidigStanset,
    EtAutomatiskFradragHarFremtidigPeriode,
    UgyldigePerioderForAutomatiskReguering,
    VedtakstidslinjeErIkkeSammenhengende,
    DelvisOpphør,

    // Historiske
    FradragErUtenlandsinntekt,
    FinnesFlerePerioderAvFradrag,
    UtbetalingFeilet,
    SupplementHarFlereVedtaksperioderForFradrag,
    FantIkkeVedtakForApril,
    MerEnn1Eps,
    SupplementInneholderIkkeFradraget,
    FradragMåHåndteresManuelt,
    DifferanseFørRegulering,
    DifferanseEtterRegulering,
    BrukerManglerSupplement,
    ForventetInntektErStørreEnn0,
    AutomatiskSendingTilUtbetalingFeilet,
}
