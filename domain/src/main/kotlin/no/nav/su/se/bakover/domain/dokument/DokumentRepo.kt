package no.nav.su.se.bakover.domain.dokument

import java.util.UUID

interface DokumentRepo {
    fun lagre(dokument: Dokument)
    fun hentDokument(id: UUID): Dokument?
    fun hentForSak(id: UUID): List<Dokument>
    fun hentForSøknad(id: UUID): List<Dokument>
    fun hentForVedtak(id: UUID): List<Dokument>

    fun hentDokumentdistribusjon(id: UUID): Dokumentdistribusjon?
    fun hentDokumenterForDistribusjon(): List<Dokumentdistribusjon>
    fun oppdaterDokumentdistribusjon(dokumentdistribusjon: Dokumentdistribusjon)
}
