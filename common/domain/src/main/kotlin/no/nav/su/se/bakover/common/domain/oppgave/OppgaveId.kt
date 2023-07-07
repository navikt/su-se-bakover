package no.nav.su.se.bakover.common.domain.oppgave

import com.fasterxml.jackson.annotation.JsonValue

data class OppgaveId(private val value: String) {
    @JsonValue
    override fun toString() = value
}
