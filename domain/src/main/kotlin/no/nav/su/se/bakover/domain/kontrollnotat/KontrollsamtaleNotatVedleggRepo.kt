import java.util.UUID

interface KontrollsamtaleNotatVedleggRepo {
    fun leggTil(vedlegg: KontrollsamtaleNotatVedlegg)
    fun slett(vedleggId: UUID)
    fun hent(vedleggId: UUID): KontrollsamtaleNotatVedlegg?
    fun hentForKontrollsamtaleNotat(kontrollsamtaleNotatId: UUID): List<KontrollsamtaleNotatVedlegg>
    fun hentAntallVedlegg(kontrollsamtaleNotatId: UUID): Int
}
