package no.nav.su.se.bakover.common.domain.config

data class ServiceUserConfig(
    val username: String,
    val password: String,
) {
    override fun toString(): String {
        return "ServiceUser(username='$username', password='****')"
    }

    // Tillater extension functions.
    companion object
}
