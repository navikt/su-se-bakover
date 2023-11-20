package tilbakekreving.presentation.api.common

enum class TilbakekrevingsbehandlingStatus {
    OPPRETTET,

    /**
     * At statusen er forhåndsvarslet betyr ikke nødvendigvis at saksbehandler har sendt forhåndsvarsel,
     * med at dem har tatt stilling til dette steget ved å enten sende ut, eller ikke
     */
    FORHÅNDSVARSLET,
    VURDERT,
    VEDTAKSBREV,
    TIL_ATTESTERING,
    IVERKSATT,
    AVBRUTT,
    UNDERKJENT,
}
