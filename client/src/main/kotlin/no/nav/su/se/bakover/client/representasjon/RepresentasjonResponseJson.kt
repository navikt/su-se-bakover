package no.nav.su.se.bakover.client.representasjon

data class RepresentasjonResponseJson(
    val representasjoner: List<Representasjon>,
) {
    fun harFullmakt(): Boolean {
        return representasjoner.any { it.opphoert == false || it.opphoert == null }
    }
}

data class Representasjon(
    val fullmaktId: Long,
    /** Eksempel: 2024-09-19T07:07:22.505Z */
    val registrert: String? = null,
    val registrertAv: String? = null,
    /** Eksempel: 2024-09-19T07:07:22.505Z */
    val endret: String? = null,
    val endretAv: String? = null,
    val opphoert: Boolean? = null,
    val fullmaktsgiver: String,
    val fullmektig: String,
    val omraade: List<Område> = emptyList(),
    val gyldigFraOgMed: String,
    val gyldigTilOgMed: String? = null,
    /** Eksempel: 3fa85f64-5717-4562-b3fc-2c963f66afa6 */
    val fullmaktUuid: String? = null,
    /** Eksempel: 3fa85f64-5717-4562-b3fc-2c963f66afa6 */
    val opplysningsId: String? = null,
    val endringsId: Long? = null,
    val status: String? = null,
    val kilde: String? = null,
    val fullmaktsgiverNavn: String? = null,
    val fullmektigsNavn: String? = null,
) {
    data class Område(
        val tema: String,
        /** En av: LES, KOMMUNISER, SKRIV */
        val handling: List<String>,
    )
}
