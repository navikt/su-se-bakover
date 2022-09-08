package no.nav.su.se.bakover.client.krr

data class Kontaktinformasjon(
    val epostadresse: String?,
    val mobiltelefonnummer: String?,
    val reservert: Boolean,
    val kanVarsles: Boolean,
    val språk: String?, // "nb"
)
