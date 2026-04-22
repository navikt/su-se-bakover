package no.nav.su.se.bakover.common.logging

import com.fasterxml.jackson.core.JsonStreamContext
import net.logstash.logback.mask.ValueMasker
import no.nav.su.se.bakover.common.person.Fnr

private val fnrRegex = Regex("\\b\\d{11}\\b")

class FnrMasker : ValueMasker {
    override fun mask(
        context: JsonStreamContext,
        value: Any,
    ): Any {
        return if (value is CharSequence) {
            redact(value.toString())
        } else {
            value
        }
    }

    companion object {
        fun redact(string: String): String {
            return string.replace(fnrRegex) {
                if (Fnr.tryCreate(it.value) != null) {
                    "***********"
                } else {
                    it.value
                }
            }
        }
    }
}
