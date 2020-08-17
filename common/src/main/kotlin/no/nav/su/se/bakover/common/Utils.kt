package no.nav.su.se.bakover.common

// NAIS_CLUSTER_NAME blir satt av Nais.
fun isLocalOrRunningTests(): Boolean = System.getenv()["NAIS_CLUSTER_NAME"] == null
