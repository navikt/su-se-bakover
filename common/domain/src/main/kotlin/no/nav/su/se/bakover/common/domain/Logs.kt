package no.nav.su.se.bakover.common

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory

val sikkerLogg = SikkerLogg.Logg()

sealed class SikkerLogg {
    open fun info(msg: String, vararg args: Any?) {}
    open fun warn(msg: String, vararg args: Any?) {}
    open fun error(msg: String, vararg args: Any?) {}
    open fun debug(msg: String, vararg args: Any?) {}
    open fun trace(msg: String, vararg args: Any?) {}

    data object NOP : SikkerLogg()

    class Logg : SikkerLogg() {

        private val teamslog: Logger = LoggerFactory.getLogger("team-logs-logger")

        private val marker = MarkerFactory.getMarker("TEAM_LOGS")

        override fun info(msg: String, vararg args: Any?) =
            teamslog.info(marker, msg, *args)

        override fun warn(msg: String, vararg args: Any?) =
            teamslog.warn(marker, msg, *args)

        override fun error(msg: String, vararg args: Any?) =
            teamslog.error(marker, msg, *args)

        override fun debug(msg: String, vararg args: Any?) =
            teamslog.debug(marker, msg, *args)

        override fun trace(msg: String, vararg args: Any?) =
            teamslog.trace(marker, msg, *args)
    }
}
