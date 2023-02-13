package no.nav.su.se.bakover.domain.regulering

enum class ÅrsakTilManuellRegulering {
    FradragMåHåndteresManuelt,
    YtelseErMidlertidigStanset,
    ForventetInntektErStørreEnn0,
    DelvisOpphør,
    VedtakstidslinjeErIkkeSammenhengende,
    PågåendeAvkortingEllerBehovForFremtidigAvkorting,
    AvventerKravgrunnlag,
    UtbetalingFeilet,
}
