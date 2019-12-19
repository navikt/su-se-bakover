package no.nav.su.se.bakover

data class Environment(val map: Map<String, String> = System.getenv()) {

    companion object {
        const val CORS_KEY = "ALLOW_CORS_ORIGIN"
        const val SU_PERSON_URL = "http://su-person"
        const val SU_INNTEKT_URL = "http://su-inntekt"
    }

    val theSecret: String = envVar("secret", "not set, not required")
    val allowCorsOrigin: String = envVar(CORS_KEY)

    private fun envVar(key: String, defaultValue: String? = null): String {
        return map[key] ?: defaultValue ?: throw RuntimeException("Missing required variable \"$key\"")
    }
}
