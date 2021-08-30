package no.nav.su.se.bakover.domain.person

enum class SivilstandTyper(val readableName: String) {
    UOPPGITT("Uoppgitt"),
    UGIFT("Ugift"),
    GIFT("Gift"),
    ENKE_ELLER_ENKEMANN("Enke eller enkemann"),
    SKILT("Skilt"),
    SEPARERT("Separert"),
    REGISTRERT_PARTNER("Registrert partner"),
    SEPARERT_PARTNER("Separert partner"),
    SKILT_PARTNER("Skilt partner"),
    GJENLEVENDE_PARTNER("Gjenlevende partner"),
}
