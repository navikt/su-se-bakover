package no.nav.su.se.bakover.common

import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerLogg: Logger = LoggerFactory.getLogger("sikkerLogg")

// Application er allerede reservert av Ktor
val log: Logger = LoggerFactory.getLogger("su-se-bakover")
