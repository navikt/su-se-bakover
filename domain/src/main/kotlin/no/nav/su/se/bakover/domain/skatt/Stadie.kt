package no.nav.su.se.bakover.domain.skatt

/**
 * De 3 ulike stadiene for et spesifisert summert skattegrunnlag
 * Spesifitets rekkefølge er fastsatt > oppgjør > utkast
 */
enum class Stadie(val verdi: String) {
    UTKAST("utkast"),
    OPPGJØR("oppgjoer"),
    FASTSATT("fastsatt"),
}
