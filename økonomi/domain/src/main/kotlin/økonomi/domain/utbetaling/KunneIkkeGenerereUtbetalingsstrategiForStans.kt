package økonomi.domain.utbetaling

sealed class KunneIkkeGenerereUtbetalingsstrategiForStans {
    data object IngenUtbetalingerEtterStansDato : KunneIkkeGenerereUtbetalingsstrategiForStans()
    data object StansDatoErIkkeFørsteDatoIInneværendeEllerNesteMåned : KunneIkkeGenerereUtbetalingsstrategiForStans()
    data object SisteUtbetalingErEnStans : KunneIkkeGenerereUtbetalingsstrategiForStans()
    data object SisteUtbetalingErOpphør : KunneIkkeGenerereUtbetalingsstrategiForStans()
    data object KanIkkeStanseOpphørtePerioder : KunneIkkeGenerereUtbetalingsstrategiForStans()
    data object FantIngenUtbetalinger : KunneIkkeGenerereUtbetalingsstrategiForStans()
}
