package no.nav.su.se.bakover.domain.brev.søknad.lukk

import arrow.core.Either
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.lagPersonalia
import no.nav.su.se.bakover.domain.dokument.Dokument
import java.time.LocalDate
import java.util.UUID

data class TrukketSøknadBrevRequest(
    override val person: Person,
    private val søknad: Søknad,
    private val trukketDato: LocalDate,
    private val saksbehandlerNavn: String,
    override val dagensDato: LocalDate,
    override val saksnummer: Saksnummer,
) : LagBrevRequest {
    override val brevInnhold = TrukketSøknadBrevInnhold(
        personalia = lagPersonalia(),
        datoSøknadOpprettet = søknad.opprettet.toLocalDate(zoneIdOslo),
        trukketDato = trukketDato,
        saksbehandlerNavn = saksbehandlerNavn,
    )

    override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<LagBrevRequest.KunneIkkeGenererePdf, ByteArray>): Either<LagBrevRequest.KunneIkkeGenererePdf, Dokument.UtenMetadata.Informasjon> {
        return genererDokument(genererPdf).map {
            Dokument.UtenMetadata.Informasjon.Annet(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(), // TODO jah: Ta inn clock
                tittel = it.first,
                generertDokument = it.second,
                generertDokumentJson = it.third,
            )
        }
    }
}
