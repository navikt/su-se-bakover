package no.nav.su.se.bakover.web

/**
 * Markerer en testklasse som låser JSON-formen frontend sender eller mottar.
 *
 * Fasiten er fast JSON skrevet for hånd, ikke laget av produksjonskoden. Da feiler testen hvis feltnavn, typer,
 * enumverdier eller struktur endres. Avklar endringen med frontend før du oppdaterer fasiten.
 *
 * Klassen ligger i `kontrakt`-undermappen til området den tester, atskilt fra rutetestene.
 * Bruk annotasjonen bare i web-modulen.
 */
@Target(AnnotationTarget.CLASS)
annotation class Frontendkontrakt
