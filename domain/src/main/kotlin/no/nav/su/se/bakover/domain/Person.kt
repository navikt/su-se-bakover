package no.nav.su.se.bakover.domain

data class Person(
    val fnr: Fnr,
    val aktørId: AktørId,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)
