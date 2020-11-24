package no.nav.su.se.bakover.client.nais

import arrow.core.Either
import arrow.core.flatMap
import com.github.kittinunf.fuel.httpGet
import no.nav.su.se.bakover.client.fromResult
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.domain.nais.LeaderPodLookupFeil
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object LeaderPodLookupClient : LeaderPodLookup {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun amITheLeader(electorPath: String, localHostName: String): Either<LeaderPodLookupFeil, Boolean> {
        val (_, _, result) =
            addProtocolIfMissing(electorPath)
                .httpGet()
                .responseString()
        return Either.fromResult(result)
            .mapLeft { err ->
                log.error("Klarte ikke å kontakte leader-elector-containeren", err)
                LeaderPodLookupFeil.KunneIkkeKontakteLeaderElectorContainer
            }
            .flatMap {
                Either.fromNullable(
                    JSONObject(it).let { json ->
                        if (json.has("name")) {
                            json.getString("name")
                        } else {
                            null
                        }
                    }
                )
                    .mapLeft { LeaderPodLookupFeil.UkjentSvarFraLeaderElectorContainer }
            }
            .map { leaderName ->
                log.info("Fant leder med navn '$leaderName'. Mitt hostname er '$localHostName'.")
                leaderName == localHostName
            }
    }

    private fun addProtocolIfMissing(endpoint: String) =
        if (endpoint.startsWith("http")) {
            endpoint
        } else {
            "http://$endpoint"
        }
}
