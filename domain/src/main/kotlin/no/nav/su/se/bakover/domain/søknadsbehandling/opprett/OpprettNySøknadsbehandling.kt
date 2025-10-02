package no.nav.su.se.bakover.domain.søknadsbehandling.opprett

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import behandling.søknadsbehandling.domain.KunneIkkeStarteSøknadsbehandling
import behandling.søknadsbehandling.domain.VilkårsvurderingerSøknadsbehandling
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.nySøknadsbehandling
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingsHandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshendelse
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandlingshistorikk
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import vilkår.vurderinger.domain.Grunnlagsdata
import vilkår.vurderinger.domain.StøtterHentingAvEksternGrunnlag
import java.time.Clock
import java.util.UUID

fun Sak.opprettNySøknadsbehandling(
    søknadsbehandlingId: SøknadsbehandlingId? = null,
    søknadId: UUID,
    clock: Clock,
    saksbehandler: NavIdentBruker.Saksbehandler?,
): Either<KunneIkkeStarteSøknadsbehandling, Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart>> {
    val søknad = hentSøknad(søknadId).fold(
        ifLeft = { throw IllegalArgumentException("Fant ikke søknad $søknadId") },
        ifRight = {
            if (it is Søknad.Journalført.MedOppgave.Lukket) {
                return KunneIkkeStarteSøknadsbehandling.ErLukket.left()
            }
            if (it !is Søknad.Journalført.MedOppgave) {
                return KunneIkkeStarteSøknadsbehandling.ManglerOppgave.left()
            }
            it as Søknad.Journalført.MedOppgave.IkkeLukket
        },
    ).also { require(type == it.type) { "Støtter ikke å ha forskjellige typer (uføre, alder) på en og samme sak." } }
    return opprettNySøknadsbehandling(søknadsbehandlingId, søknad, clock, saksbehandler)
}

/**
 * Begrensninger for opprettelse av ny søknadsbehandling:
 * - Kun én søknadsbehandling per søknad. På sikt kan denne begrensningen løses litt opp. Eksempelvis ved omgjøring etter klage eller eget tiltak.
 * - Søknaden må være journalført, oppgave må ha vært opprettet og søknaden kan ikke være lukket.
 *
 * Siden stønadsperioden velges etter man har opprettet søknadsbehandlingen, vil ikke stønadsperiodebegresningene gjelde for dette steget.
 *
 * @param søknadsbehandlingId - Id'en til søknadsbehandlingen. Genereres automatisk dersom dette ikke sendes med.
 */
fun Sak.opprettNySøknadsbehandling(
    søknadsbehandlingId: SøknadsbehandlingId? = null,
    søknad: Søknad.Journalført.MedOppgave.IkkeLukket,
    clock: Clock,
    saksbehandler: NavIdentBruker.Saksbehandler?,
): Either<KunneIkkeStarteSøknadsbehandling, Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart>> {
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
                saksbehandler = saksbehandler ?: NavIdentBruker.Saksbehandler.systembruker(),
                handling = SøknadsbehandlingsHandling.StartetBehandling,
            ),
        ),
        sakstype = type,
        saksbehandler = saksbehandler,
        omgjøringsårsak = null,
        omgjøringsgrunn = null,
    ).let { søknadsbehandling ->
        Pair(
            this.nySøknadsbehandling(søknadsbehandling),
            søknadsbehandling,
        ).right()
    }
}
