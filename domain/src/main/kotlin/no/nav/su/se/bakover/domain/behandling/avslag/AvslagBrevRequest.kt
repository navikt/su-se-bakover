package no.nav.su.se.bakover.domain.behandling.avslag

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.BrevInnhold.AvslagsBrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevInnhold.Personalia
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning

data class AvslagBrevRequest(
    private val person: Person,
    private val avslag: Avslag,
    private val saksbehandlerNavn: String,
    private val attestantNavn: String
) : LagBrevRequest {
    override fun getPerson(): Person = person
    override fun lagBrevInnhold(personalia: Personalia): AvslagsBrevInnhold = AvslagsBrevInnhold(
        personalia = personalia,
        avslagsgrunner = avslag.avslagsgrunner,
        harEktefelle = avslag.harEktefelle,
        halvGrunnbeløp = avslag.halvGrunnbeløp.toInt(),
        beregning = avslag.beregning?.let { LagBrevinnholdForBeregning(it).get() },
        saksbehandlerNavn = saksbehandlerNavn,
        attestantNavn = attestantNavn
    )
}
