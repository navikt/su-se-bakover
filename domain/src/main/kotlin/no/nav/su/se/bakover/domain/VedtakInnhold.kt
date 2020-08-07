package no.nav.su.se.bakover.domain

data class VedtakInnhold(
    val dato: String,
    val fødselsnummer: String,
    val fornavn: String,
    val etternavn: String,
    val adresse: String?,
    val postnummer: String?,
    val poststed: String?
)
