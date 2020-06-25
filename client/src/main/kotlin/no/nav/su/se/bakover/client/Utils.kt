package no.nav.su.se.bakover.client

// NAIS_CLUSTER_NAME blir satt av Nais.
internal fun Map<String, String>.isLocalOrRunningTests(): Boolean = this["NAIS_CLUSTER_NAME"] == null
