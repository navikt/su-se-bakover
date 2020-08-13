package no.nav.su.se.bakover.domain

data class Fnr(
    val fnr: String?
) {
    private val fnrPattern = Regex("[0-9]{11}")

    init {
        validate(fnr)
    }

    override fun toString(): String = fnr!!

    private fun validate(fnr: String?) {
        if (fnr == null) throw UgyldigFnrException(fnr)
        if (!fnr.matches(fnrPattern)) throw UgyldigFnrException(fnr)
    }

    companion object {
        const val FNR = "fnr"
    }
}

class UgyldigFnrException(fnr: String?) : RuntimeException("Ugyldig fnr: $fnr")
