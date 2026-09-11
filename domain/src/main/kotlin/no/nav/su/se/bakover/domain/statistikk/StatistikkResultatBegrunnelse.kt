enum class Søknadsavslagsgrunn(val verdi: String) {
    FOR_TIDLIG_SØKNAD("Avslag på grunn av for tidlig søknad"),
}

enum class Klageavvisningsgrunn {
    IKKE_INNENFOR_FRISTEN,
    KLAGES_IKKE_PÅ_KONKRETE_ELEMENTER_I_VEDTAKET,
    IKKE_UNDERSKREVET,
}
