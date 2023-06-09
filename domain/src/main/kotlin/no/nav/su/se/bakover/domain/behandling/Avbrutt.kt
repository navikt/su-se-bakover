package no.nav.su.se.bakover.domain.behandling

/**
    En spesifisering av "avsluttet" - "Avbrutt" brukes når en behandling blir startet, men det senere viser seg at
    det ikke er noe behov for behandlingen.
    <p>
    Et eksempel er [no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering]
    */
interface Avbrutt : Avsluttet
