package no.nav.su.se.bakover.domain.behandling.avslag

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.BrevInnhold.AvslagsVedtak
import no.nav.su.se.bakover.domain.brev.BrevInnhold.Personalia
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.getBrevinnholdberegning

data class AvslagBrevRequest(
    private val person: Person,
    private val avslag: Avslag,
) : LagBrevRequest {
    override fun getPerson(): Person = person
    override fun lagBrevInnhold(personalia: Personalia): AvslagsVedtak = AvslagsVedtak(
        personalia = personalia,
        avslagsgrunner = avslag.avslagsgrunner,
        harEktefelle = avslag.harEktefelle,
        halvGrunnbeløp = avslag.halvGrunnbeløp.toInt(),
        beregning = avslag.beregning?.let { getBrevinnholdberegning(it) }
    )
}
