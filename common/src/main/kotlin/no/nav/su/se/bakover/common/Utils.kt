package no.nav.su.se.bakover.common

import java.util.Properties

// NAIS_CLUSTER_NAME blir satt av Nais.
fun Map<String, String>.isLocalOrRunningTests(): Boolean = this["NAIS_CLUSTER_NAME"] == null
fun Properties.isLocalOrRunningTests(): Boolean = this["NAIS_CLUSTER_NAME"] == null
