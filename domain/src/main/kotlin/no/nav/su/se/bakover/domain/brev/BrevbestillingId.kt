package no.nav.su.se.bakover.domain.brev

import com.fasterxml.jackson.annotation.JsonValue

data class BrevbestillingId(val value: String) {
    @JsonValue
    override fun toString() = value
}
