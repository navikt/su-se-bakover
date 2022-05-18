package no.nav.su.se.bakover.domain.oppdrag

/**
 * Fra dokumentasjon fra Oppdrag: Gjør det mulig å velge om delytelsen skal beregnes/utbetales i henhold til kjøreplanen eller om dette skal skje idag.
 * Verdien 'N' medfører at beregningen kjøres idag. Beregningen vil bare gjelde beregningsperioder som allerede er forfalt.
 *
 * Tidligere var denne alltid satt til [NEI], men etter vi la til automatisk/manuell regulering ønsket vi å etterbetale sammen med neste kjøring.
 */
enum class Utbetalingskjøreplan {
    JA,
    NEI;
}
