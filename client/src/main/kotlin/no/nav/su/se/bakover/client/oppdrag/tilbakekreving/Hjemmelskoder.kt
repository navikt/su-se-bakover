package no.nav.su.se.bakover.client.oppdrag.tilbakekreving

/**
 * Dette er hjemmelskodene som aksepteres av Oppdrag/Tilbakekrevingskomponenten.
 * De er ikke beskrevet i wsdl'en, og finnes derfor ikke i tjenestespesifikasjonssbiblioteket.
 */
enum class Hjemmelskoder(val hjemmelskode: String) {
    TODO("TODO");

    override fun toString() = hjemmelskode
}
