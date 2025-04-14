package no.nav.su.se.bakover.domain.revurdering.steg

enum class Revurderingsteg(val vilkår: String) {
    // BorOgOppholderSegINorge("BorOgOppholderSegINorge"),
    Flyktning("Flyktning"),
    Formue("Formue"),

    Oppholdstillatelse("Oppholdstillatelse"),

    PersonligOppmøte("PersonligOppmøte"),
    Uførhet("Uførhet"),
    Bosituasjon("Bosituasjon"),

    Institusjonsopphold("Institusjonsopphold"),
    Utenlandsopphold("Utenlandsopphold"),
    Inntekt("Inntekt"),
    Opplysningsplikt("Opplysningsplikt"),
    Pensjon("Pensjon"),
    FastOppholdINorge("FastOppholdINorge"),
    Familiegjenforening("Familiegjenforening"),
}
