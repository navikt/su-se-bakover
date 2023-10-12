package dokument.domain.brev

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

data class BrevbestillingId
@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
constructor(val value: String) {
    @JsonValue
    override fun toString() = value
}
