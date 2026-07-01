package no.nav.su.se.bakover.kontrollsamtale.domain

interface KontrollsamtaleNotatRepo {
    fun lagre(
        kontrollsamtaleNotat: KontrollsamtaleNotat,
    )
}
