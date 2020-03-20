package no.nav.su.se.bakover.db

import no.nav.su.se.bakover.Fødselsnummer

// forstår hvordan man kan lagre og hente saker fra et persistenslag
internal interface Repository {
    fun nySak(fnr: Fødselsnummer): Long
    fun sakIdForFnr(fnr: Fødselsnummer): Long?
    fun lagreSøknad(json: String): Long
    fun fnrForSakId(sakId: Long): Fødselsnummer?
    fun søknadForStønadsperiode(stønadsperiodeId: Long): Pair<Long, String>?
    fun alleSaker(): List<Pair<Long, Fødselsnummer>>
    fun søknadForId(id: Long): Pair<Long, String>?
    fun lagreStønadsperiode(sakId: Long, søknadId: Long): Long
    fun stønadsperioderForSak(sakId: Long): List<Long>
}