package no.nav.su.se.bakover.domain.klage

interface KlagevedtakRepo {
    fun lagre(klagevedtak: UprosessertFattetKlagevedtak)
    fun hentUbehandlaKlagevedtak(): List<UprosessertFattetKlagevedtak>
}
