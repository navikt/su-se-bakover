package no.nav.su.se.bakover.domain.vedtak

/**
 * Henger sammen med sub-typene til [Vedtak], men er en forenklet modell, sett fra et større nav-perspektiv.
 * Brukes blant annet for å avgjøre om en bruker har stønad en gitt måned.
 * Utelukker stans og gjenoppta, da disse ikke er ekte vedtak som avgjør rett til stønad, men er mer et midlertidig tiltak.
 * Vi utelukker også reguleringer, da disse ikke skal endre om en bruker har rett på stønad.
 */
enum class Vedtakstype {
    SØKNADSBEHANDLING_INNVILGELSE,
    REVURDERING_INNVILGELSE,
    REVURDERING_OPPHØR,
}
