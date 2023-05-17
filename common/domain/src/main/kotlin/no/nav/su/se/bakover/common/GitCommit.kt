package no.nav.su.se.bakover.common

@JvmInline
value class GitCommit(val value: String) {

    init {
        require(isSha1(value)) {
            "$value wasn't a SHA1 string"
        }
    }

    companion object {

        private val sha1Extractor = Regex("[a-fA-F0-9]{40}$")

        /** Extracts a 40 character SHA1 from the end of the string  */
        fun fromString(value: String): GitCommit? {
            return sha1Extractor.find(value.trimWhitespace())?.value?.let {
                if (!isSha1(it)) return null
                GitCommit(it)
            }
        }

        private val validSha1 = Regex("^[a-fA-F0-9]{40}$")

        private fun isSha1(value: String): Boolean {
            return value.matches(validSha1)
        }
    }
}
