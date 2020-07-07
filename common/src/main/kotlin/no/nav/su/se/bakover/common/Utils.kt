package no.nav.su.se.bakover.common

// NAIS_CLUSTER_NAME blir satt av Nais.
fun Map<String, String>.isLocalOrRunningTests(): Boolean = this["NAIS_CLUSTER_NAME"] == null
