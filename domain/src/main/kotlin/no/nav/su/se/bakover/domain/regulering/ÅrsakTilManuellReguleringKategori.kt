package no.nav.su.se.bakover.domain.regulering

enum class ÅrsakTilManuellReguleringKategori {
    OpprettetAvSaksbehandler,

    ManglerRegulertBeløpForFradrag,
    ManglerIeuFraPesys,
    YtelseErMidlertidigStanset,
    EtAutomatiskFradragHarFremtidigPeriode,
    UgyldigePerioderForAutomatiskRegulering,
    AapManglerGyldigPeriode,

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
    VedtakstidslinjeErIkkeSammenhengende,
    DelvisOpphør,
}
