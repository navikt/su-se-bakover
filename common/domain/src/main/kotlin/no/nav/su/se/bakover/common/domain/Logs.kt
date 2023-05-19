package no.nav.su.se.bakover.common

import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerLogg: Logger = LoggerFactory.getLogger("sikkerLogg")

/**
 * Application er allerede reservert av Ktor.
 * TODO jah: Denne skulle egentlig være private og bo i Application.kt, den brukes nå av alt for mange.
 */

val log: Logger = LoggerFactory.getLogger("su-se-bakover")
