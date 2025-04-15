package no.nav.su.se.bakover.domain.revurdering.steg

enum class Revurderingsteg(val vilkår: String) {
    // BorOgOppholderSegINorge("BorOgOppholderSegINorge"),
    Formue("Formue"),
    Oppholdstillatelse("Oppholdstillatelse"),
    PersonligOppmøte("PersonligOppmøte"),
    Bosituasjon("Bosituasjon"),
    Institusjonsopphold("Institusjonsopphold"),
    Utenlandsopphold("Utenlandsopphold"),
    Inntekt("Inntekt"),
    Opplysningsplikt("Opplysningsplikt"),
    FastOppholdINorge("FastOppholdINorge"),

    // kun for uføre
    Flyktning("Flyktning"),
    Uførhet("Uførhet"),

    // Kun for alder
    Pensjon("Pensjon"),
    Familiegjenforening("Familiegjenforening"),
}
