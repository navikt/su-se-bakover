package no.nav.su.se.bakover.web

/**
 * Markerer en test som låser JSON-kontrakten mellom backend og frontend.
 *
 * Testen sammenligner serialisert JSON med en fast JSON-tekst. Fasiten er bevisst skrevet for hånd og ikke laget av
 * produksjonskoden, slik at endrede feltnavn, typer, enumverdier eller struktur får testen til å feile.
 * Avklar endringen med frontend før fasiten oppdateres.
 *
 * Rene serialiseringstester ligger i en `kontrakt`-undermappe under området de tester, atskilt fra rutetestene.
 * Annotasjonen finnes bare i web-modulen.
 */
@Target(AnnotationTarget.CLASS)
annotation class Frontendkontrakt
