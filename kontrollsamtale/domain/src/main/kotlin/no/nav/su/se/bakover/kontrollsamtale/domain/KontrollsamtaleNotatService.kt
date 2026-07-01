package no.nav.su.se.bakover.kontrollsamtale.domain

interface KontrollsamtaleNotatService {
    fun lagre(
        kontrollsamtaleNotat: KontrollsamtaleNotat,
    )
}
