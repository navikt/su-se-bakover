package no.nav.su.se.bakover.domain.søknadsbehandling.opprett

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import behandling.søknadsbehandling.domain.KunneIkkeOppretteSøknadsbehandling
import behandling.søknadsbehandling.domain.VilkårsvurderingerSøknadsbehandling
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.nySøknadsbehandling
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshistorikk
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import vilkår.vurderinger.domain.Grunnlagsdata
import vilkår.vurderinger.domain.StøtterHentingAvEksternGrunnlag
import java.time.Clock
import java.util.UUID

/**
 * Begrensninger for opprettelse av ny søknadsbehandling:
 * - Kun én søknadsbehandling per søknad. På sikt kan denne begrensningen løses litt opp. Eksempelvis ved omgjøring etter klage eller eget tiltak.
 * - Søknaden må være journalført, oppgave må ha vært opprettet og søknaden kan ikke være lukket.
 * - Kun én åpen søknadsbehandling om gangen.
 *
 * Siden stønadsperioden velges etter man har opprettet søknadsbehandlingen, vil ikke stønadsperiodebegresningene gjelde for dette steget.
 *
 * @param søknadsbehandlingId - Id'en til søknadsbehandlingen. Genereres automatisk dersom dette ikke sendes med.
 * @param oppdaterOppgave - Ved opprettelse av behandlingen, vil man i noen tilfeller gjøre noe med oppgaven
 */
fun Sak.opprettNySøknadsbehandling(
    søknadsbehandlingId: SøknadsbehandlingId? = null,
    søknadId: UUID,
    clock: Clock,
    saksbehandler: NavIdentBruker.Saksbehandler,
    oppdaterOppgave: ((oppgaveId: OppgaveId, saksbehandler: NavIdentBruker.Saksbehandler) -> Either<Unit, OppgaveHttpKallResponse>)?,
): Either<KunneIkkeOppretteSøknadsbehandling, Triple<Sak, VilkårsvurdertSøknadsbehandling.Uavklart, StatistikkEvent.Behandling.Søknad.Opprettet>> {
    if (harÅpenSøknadsbehandling()) {
        return KunneIkkeOppretteSøknadsbehandling.HarÅpenSøknadsbehandling.left()
    }
    val søknad = hentSøknad(søknadId).fold(
        ifLeft = { throw IllegalArgumentException("Fant ikke søknad $søknadId") },
        ifRight = {
            if (it is Søknad.Journalført.MedOppgave.Lukket) {
                return KunneIkkeOppretteSøknadsbehandling.ErLukket.left()
            }
            if (it !is Søknad.Journalført.MedOppgave) {
                // TODO Prøv å opprette oppgaven hvis den mangler? (systembruker blir kanskje mest riktig?)
                return KunneIkkeOppretteSøknadsbehandling.ManglerOppgave.left()
            }
            it
        },
    ).also { require(type == it.type) { "Støtter ikke å ha forskjellige typer (uføre, alder) på en og samme sak." } }

    // gjør en best effort for å oppdatere oppgaven. logging av left gjøres i oppdaterTilordnetRessurs
    oppdaterOppgave?.invoke(søknad.oppgaveId, saksbehandler)

    return VilkårsvurdertSøknadsbehandling.Uavklart(
        id = søknadsbehandlingId ?: SøknadsbehandlingId.generer(),
        opprettet = Tidspunkt.now(clock),
        sakId = this.id,
        saksnummer = saksnummer,
        søknad = søknad,
        oppgaveId = søknad.oppgaveId,
        fnr = fnr,
        fritekstTilBrev = "",
        aldersvurdering = null,
        grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling(
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = when (type) {
                Sakstype.ALDER -> VilkårsvurderingerSøknadsbehandling.Alder.ikkeVurdert()
                Sakstype.UFØRE -> VilkårsvurderingerSøknadsbehandling.Uføre.ikkeVurdert()
            },
            eksterneGrunnlag = StøtterHentingAvEksternGrunnlag.ikkeHentet(),
        ),
        attesteringer = Attesteringshistorikk.empty(),
        søknadsbehandlingsHistorikk = Søknadsbehandlingshistorikk.nyHistorikk(
            Søknadsbehandlingshendelse(
                tidspunkt = opprettet,
                saksbehandler = saksbehandler,
                handling = SøknadsbehandlingsHandling.StartetBehandling,
            ),
        ),
        sakstype = type,
        saksbehandler = saksbehandler,
        omgjøringsårsak = null,
        omgjøringsgrunn = null,
    ).let { søknadsbehandling ->
        Triple(
            this.nySøknadsbehandling(søknadsbehandling),
            søknadsbehandling,
            StatistikkEvent.Behandling.Søknad.Opprettet(søknadsbehandling, saksbehandler),
        ).right()
    }
}
