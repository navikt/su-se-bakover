package no.nav.su.se.bakover.web.routes.drift

import arrow.core.Either
import arrow.core.getOrElse
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.person.pdl.leesah.common.adresse.UkjentBosted
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService



internal fun Route.innlesingPersonhendelserFraFilRoute(
    personhendelseService: PersonhendelseService,
) {
    post("$DRIFT_PATH/personhendelser") {
        authorize(Brukerrolle.Drift) {
            call.request.headers["content-type"]?.contains("multipart/form-data")
            // TODO: return error if content-type is not multipart/form-data
                ?: throw IllegalStateException("Missing content-type multipart/form-data")

            val parts = call.receiveMultipart()

            parts.forEachPart {
                when (it) {
                    is PartData.FileItem -> {
                        val fileBytes = it.streamProvider().readBytes()
                        val fileAsString = String(fileBytes)

                        Either.catch {
                            deserializeList<PersonhendelseJson>(fileAsString).mapNotNull {
                                it.toDomain().getOrNull()
                            }.forEach {
                                personhendelseService.prosesserNyHendelse(it)
                            }
                        }.getOrElse {
                            TODO()
                        }
                    }

                    else -> Feilresponser.ukjentMultipartType
                }
            }

        }
    }
}
