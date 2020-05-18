package no.nav.su.se.bakover

// forstår hvordan man kan lagre og hente saker fra et persistenslag
interface Repository {
    fun lagreSøknad(json: String): Long
    fun søknadForStønadsperiode(stønadsperiodeId: Long): Pair<Long, String>?
    fun søknadForId(id: Long): Pair<Long, String>?
    fun lagreStønadsperiode(sakId: Long, søknadId: Long): Long
    fun stønadsperioderForSak(sakId: Long): List<Long>
}