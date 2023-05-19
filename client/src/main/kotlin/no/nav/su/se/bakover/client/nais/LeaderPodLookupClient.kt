package no.nav.su.se.bakover.client.nais

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.getOrElse
import no.nav.su.se.bakover.common.nais.LeaderPodLookup
import no.nav.su.se.bakover.common.nais.LeaderPodLookupFeil
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class LeaderPodLookupClient(
    private val leaderLookupPath: String,
) : LeaderPodLookup {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun amITheLeader(localHostName: String): Either<LeaderPodLookupFeil, Boolean> {
        val (_, _, result) = addProtocolIfMissing(leaderLookupPath).httpGet().responseString()
        val json = JSONObject(
            result.getOrElse { err ->
                log.error("Klarte ikke å kontakte leader-elector-containeren", err)
                return LeaderPodLookupFeil.KunneIkkeKontakteLeaderElectorContainer.left()
            },
        )
        val leaderName = json.optString("name", null)
        if (leaderName == null) {
            log.error("json-responsen fra leader-elector-containeren manglet keyen 'name'.")
            return LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer.left()
        }
        log.debug("Fant leder med navn '$leaderName'. Mitt hostname er '$localHostName'.")
        return (leaderName == localHostName).right()
    }

    private fun addProtocolIfMissing(endpoint: String): String {
        return if (endpoint.startsWith("http")) {
            endpoint
        } else {
            "http://$endpoint"
        }
    }
}
