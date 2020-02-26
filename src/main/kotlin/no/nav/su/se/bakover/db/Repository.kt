package no.nav.su.se.bakover.db

// forstår hvordan man kan lagre og hente saker fra et persistenslag
internal interface Repository {
    fun nySak(fnr: String): Long
    fun sakIdForFnr(fnr: String): Long?
    fun nySøknad(sakId: Long, json: String): Long
    fun fnrForSakId(sakId: Long): String?
    fun søknaderForSak(sakId: Long): List<Pair<Long, String>>
    fun alleSaker(): List<Pair<Long, String>>
}