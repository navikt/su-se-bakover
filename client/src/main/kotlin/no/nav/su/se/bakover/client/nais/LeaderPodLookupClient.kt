package no.nav.su.se.bakover.client.nais

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.getOrElse
import kotlinx.atomicfu.atomic
import no.nav.su.se.bakover.common.nais.LeaderPodLookup
import no.nav.su.se.bakover.common.nais.LeaderPodLookupFeil
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Gjør et kall til leader-elector sidecar for å sjekke om poden er leder.
 * Dersom en pod har blitt valgt som leder, vil den være det til podden er slettet fra kubernetes clusteret.
 *
 * @param leaderLookupPath Full URL til leader-elector sidecar. Dokumentasjonen sier den skal ligge i environment variable $ELECTOR_PATH
 * Docs: https://doc.nais.io/services/leader-election/
 */
internal class LeaderPodLookupClient(
    private val leaderLookupPath: String,
) : LeaderPodLookup {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val amITheLeader = atomic(false)

    override fun amITheLeader(localHostName: String): Either<LeaderPodLookupFeil, Boolean> {
        // I dette tilfellet vil vi allerede være leder til podden slettes fra kubernetes clusteret og vi kan trygt returnere true.
        if (amITheLeader.value) return true.right()

        val url = addProtocolIfMissing(leaderLookupPath)
        val (_, response, responseString) = url.httpGet().responseString().let {
            Triple(
                it.first,
                it.second,
                it.third.getOrElse { err ->
                    log.error(
                        "Klarte ikke å kontakte leader-elector-containeren. Url: $url, status: ${it.second.statusCode}",
                        err,
                    )
                    return LeaderPodLookupFeil.KunneIkkeKontakteLeaderElectorContainer.left()
                },
            )
        }
        val json = JSONObject(responseString)
        val leaderName = json.optString("name", null)
        if (leaderName == null) {
            log.error("json-responsen fra leader-elector-containeren manglet keyen 'name'. Url: $url, body: $responseString, status: ${response.statusCode}")
            return LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer.left()
        }
        log.debug("Fant leder med navn '$leaderName'. Mitt hostname er '$localHostName'. Url: $url, body: $responseString, status: ${response.statusCode}")
        return (leaderName == localHostName).also {
            if (it) amITheLeader.value = true
        }.right()
    }

    private fun addProtocolIfMissing(endpoint: String): String {
        return if (endpoint.startsWith("http")) {
            endpoint
        } else {
            "http://$endpoint"
        }
    }
}
