package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.domain.Saksnummer

abstract class SaksnummerFactory(
    private val nesteSaksnummer: () -> Saksnummer?,
) {
    protected open fun hentNesteSaksnummer(): Saksnummer? {
        return nesteSaksnummer()
    }

    abstract fun neste(): Saksnummer
}

class SaksnummerFactoryProd(
    nesteSaksnummer: () -> Saksnummer?,
) : SaksnummerFactory(nesteSaksnummer) {
    private val minimum = 2021L

    @Synchronized
    override fun neste(): Saksnummer {
        return super.hentNesteSaksnummer() ?: Saksnummer(minimum)
    }
}

/**
 * Eget factory for test slik at vi har mulighet til å sette startverdien til et minimum som ikke overlapper med
 * noe som allerede eksisterer i prod. Behover oppstår som følge av jevnlig datalast fra prod til test i OS.
 * Eksisterende oppdrag for våre syntetiske brukere fjernes i denne prosessen (siden de ikke eksisterer i prod).
 * Etter tømming av vår database, er vi avhengige av at de nye saksnummerne (brukes fagsystem-id mot OS) som opprettes
 * ikke er i konflikt med dataene som ble lastet inn i OS fra prod.
 */
class SaksnummerFactoryTest(
    nesteSaksnummer: () -> Saksnummer?,
) : SaksnummerFactory(nesteSaksnummer) {
    private val minimum = 10002021L

    @Synchronized
    override fun neste(): Saksnummer {
        return super.hentNesteSaksnummer() ?: Saksnummer(minimum)
    }
}
