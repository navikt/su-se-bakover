package no.nav.su.se.bakover.client

class ClientResponse(
    val httpStatus: Int,
    val content: String
) {
    override fun equals(other: Any?): Boolean = other is ClientResponse && httpStatus == other.httpStatus && content == other.content
}
