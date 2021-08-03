package no.nav.su.se.bakover.domain.brev.søknad.lukk

import arrow.core.Either
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.lagPersonalia
import no.nav.su.se.bakover.domain.dokument.Dokument
import java.time.LocalDate

data class TrukketSøknadBrevRequest(
    override val person: Person,
    private val søknad: Søknad,
    private val trukketDato: LocalDate,
    private val saksbehandlerNavn: String,
) : LagBrevRequest {
    override val brevInnhold = TrukketSøknadBrevInnhold(
        personalia = lagPersonalia(),
        datoSøknadOpprettet = søknad.opprettet.toLocalDate(zoneIdOslo),
        trukketDato = trukketDato,
        saksbehandlerNavn = saksbehandlerNavn,
    )

    override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<LagBrevRequest.KunneIkkeGenererePdf, ByteArray>): Either<LagBrevRequest.KunneIkkeGenererePdf, Dokument.UtenMetadata.Informasjon> {
        return genererDokument(genererPdf).map {
            Dokument.UtenMetadata.Informasjon(
                tittel = it.first,
                generertDokument = it.second,
                generertDokumentJson = it.third,
            )
        }
    }
}
