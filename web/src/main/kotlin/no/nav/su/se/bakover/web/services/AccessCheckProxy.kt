package no.nav.su.se.bakover.web.services

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.domain.Stønadsbehandling
import behandling.domain.fradrag.LeggTilFradragsgrunnlagRequest
import behandling.klage.domain.KlageId
import behandling.klage.domain.UprosessertKlageinstanshendelse
import behandling.revurdering.domain.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering
import behandling.revurdering.domain.bosituasjon.LeggTilBosituasjonerForRevurderingCommand
import behandling.søknadsbehandling.domain.KunneIkkeOppretteSøknadsbehandling
import behandling.søknadsbehandling.domain.bosituasjon.LeggTilBosituasjonerCommand
import dokument.domain.Dokument
import dokument.domain.GenererDokumentCommand
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import dokument.domain.brev.Brevvalg
import dokument.domain.brev.FantIkkeDokument
import dokument.domain.brev.HentDokumenterForIdType
import dokument.domain.journalføring.Journalpost
import dokument.domain.journalføring.KunneIkkeHenteJournalposter
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.AlleredeGjeldendeSakForBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.domain.klage.AvsluttetKlage
import no.nav.su.se.bakover.domain.klage.AvvistKlage
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeAvslutteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.KunneIkkeIverksetteAvvistKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLeggeTilFritekstForAvvist
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeKlageTilAttestering
import no.nav.su.se.bakover.domain.klage.KunneIkkeTolkeKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenneKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVilkårsvurdereKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.domain.klage.TolketKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.VilkårsvurdertKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.domain.klage.brev.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.domain.regulering.AvsluttetRegulering
import no.nav.su.se.bakover.domain.regulering.IverksattRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeAvslutte
import no.nav.su.se.bakover.domain.regulering.KunneIkkeOppretteRegulering
import no.nav.su.se.bakover.domain.regulering.KunneIkkeRegulereManuelt
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringId
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import no.nav.su.se.bakover.domain.regulering.ReguleringSomKreverManuellBehandling
import no.nav.su.se.bakover.domain.regulering.StartAutomatiskReguleringForInnsynCommand
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLeggeTilVedtaksbrevvalg
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.attestering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.attestering.SendTilAttesteringRequest
import no.nav.su.se.bakover.domain.revurdering.beregning.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeForhåndsvarsle
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForAvsluttingAvRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.LeggTilBrevvalgRequest
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseService
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeSimulereGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.OppdaterRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.opphør.AnnullerKontrollsamtaleVedOpphørService
import no.nav.su.se.bakover.domain.revurdering.opprett.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingOgFeilmeldingerResponse
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.stans.IverksettStansAvYtelseITransaksjonResponse
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeIverksetteStansYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeStanseYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.StansAvYtelseITransaksjonResponse
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.revurdering.underkjenn.KunneIkkeUnderkjenneRevurdering
import no.nav.su.se.bakover.domain.revurdering.vilkår.formue.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.domain.revurdering.vilkår.fradag.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.domain.revurdering.vilkår.uføre.KunneIkkeLeggeTilUføreVilkår
import no.nav.su.se.bakover.domain.revurdering.vilkår.utenlandsopphold.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.domain.sak.FantIkkeSak
import no.nav.su.se.bakover.domain.sak.FeilVedHentingAvGjeldendeVedtaksdataForPeriode
import no.nav.su.se.bakover.domain.sak.JournalførOgSendOpplastetPdfSomBrevCommand
import no.nav.su.se.bakover.domain.sak.KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak
import no.nav.su.se.bakover.domain.sak.KunneIkkeHenteGjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.sak.KunneIkkeOppretteDokument
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.sak.OpprettDokumentRequest
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.fnr.KunneIkkeOppdatereFødselsnummer
import no.nav.su.se.bakover.domain.sak.fnr.OppdaterFødselsnummerPåSakCommand
import no.nav.su.se.bakover.domain.søknad.LukkSøknadCommand
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SimulertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.UnderkjentSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast.BrevutkastForSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast.KunneIkkeGenerereBrevutkastForSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.SøknadsbehandlingSkattCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksattSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.OpprettKontrollsamtaleVedNyStønadsperiodeService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.AvslåManglendeDokumentasjonCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.KunneIkkeAvslåSøknad
import no.nav.su.se.bakover.domain.søknadsbehandling.simuler.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering.KunneIkkeSendeSøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn.KunneIkkeUnderkjenneSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.InnvilgetForMåned
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtak
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.domain.vilkår.fastopphold.KunneIkkeLeggeFastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.fastopphold.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.domain.vilkår.flyktning.KunneIkkeLeggeTilFlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.flyktning.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.KunneIkkeLeggeTilInstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.domain.vilkår.oppmøte.KunneIkkeLeggeTilPersonligOppmøteVilkårForRevurdering
import no.nav.su.se.bakover.domain.vilkår.oppmøte.KunneIkkeLeggeTilPersonligOppmøteVilkårForSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.oppmøte.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.pensjon.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.pensjon.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler
import no.nav.su.se.bakover.kontrollsamtale.domain.KunneIkkeSetteNyDatoForKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.annuller.KunneIkkeAnnullereKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.hent.KunneIkkeHenteKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.innkallingsmåned.KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.innkallingsmåned.OppdaterInnkallingsmånedPåKontrollsamtaleCommand
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status.KunneIkkeOppdatereStatusPåKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.oppdater.status.OppdaterStatusPåKontrollsamtaleCommand
import no.nav.su.se.bakover.kontrollsamtale.domain.opprett.KanIkkeOppretteKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.opprett.OpprettKontrollsamtaleCommand
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.setup.KontrollsamtaleSetup
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.service.SendPåminnelserOmNyStønadsperiodeService
import no.nav.su.se.bakover.service.avstemming.AvstemmingFeilet
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.service.klage.KlageVurderingerRequest
import no.nav.su.se.bakover.service.klage.KlageinstanshendelseService
import no.nav.su.se.bakover.service.klage.NyKlageRequest
import no.nav.su.se.bakover.service.klage.UnderkjennKlageRequest
import no.nav.su.se.bakover.service.klage.VurderKlagevilkårRequest
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallService
import no.nav.su.se.bakover.service.personhendelser.DryrunResult
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService
import no.nav.su.se.bakover.service.statistikk.ResendStatistikkhendelserService
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonService
import no.nav.su.se.bakover.service.søknad.FantIkkeSøknad
import no.nav.su.se.bakover.service.søknad.KunneIkkeLageSøknadPdf
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteSøknad
import no.nav.su.se.bakover.service.søknad.OpprettManglendeJournalpostOgOppgaveResultat
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.vedtak.application.FerdigstillVedtakService
import no.nav.su.se.bakover.vedtak.application.VedtakService
import nøkkeltall.domain.Nøkkeltall
import person.domain.KunneIkkeHentePerson
import person.domain.Person
import person.domain.PersonRepo
import person.domain.PersonService
import vedtak.domain.KunneIkkeStarteNySøknadsbehandling
import vedtak.domain.Stønadsvedtak
import vedtak.domain.Vedtak
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.skatt.application.FrioppslagSkattRequest
import vilkår.skatt.application.KunneIkkeGenerereSkattePdfOgJournalføre
import vilkår.skatt.application.KunneIkkeHenteOgLagePdfAvSkattegrunnlag
import vilkår.skatt.application.SkatteService
import vilkår.skatt.domain.Skattegrunnlag
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.vurderinger.domain.GrunnlagsdataOgVilkårsvurderinger
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.Fagområde
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.simulering.ForskjellerMellomUtbetalingOgSimulering
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.KunneIkkeKlaregjøreUtbetaling
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingKlargjortForOversendelse
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

open class AccessCheckProxy(
    private val personRepo: PersonRepo,
    private val services: Services,
) {
    fun proxy(): Services {
        return Services(
            avstemming = object : AvstemmingService {
                override fun grensesnittsavstemming(fagområde: Fagområde): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
                    return services.avstemming.grensesnittsavstemming(fagområde)
                }

                override fun grensesnittsavstemming(
                    fraOgMed: Tidspunkt,
                    tilOgMed: Tidspunkt,
                    fagområde: Fagområde,
                ): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
                    return services.avstemming.grensesnittsavstemming(fraOgMed, tilOgMed, fagområde)
                }

                override fun konsistensavstemming(
                    løpendeFraOgMed: LocalDate,
                    fagområde: Fagområde,
                ): Either<AvstemmingFeilet, Avstemming.Konsistensavstemming.Ny> {
                    return services.avstemming.konsistensavstemming(løpendeFraOgMed, fagområde)
                }

                override fun konsistensavstemmingUtførtForOgPåDato(dato: LocalDate, fagområde: Fagområde): Boolean {
                    return services.avstemming.konsistensavstemmingUtførtForOgPåDato(dato, fagområde)
                }
            },
            utbetaling = object : UtbetalingService {

                override fun hentUtbetalingerForSakId(sakId: UUID) = kastKanKunKallesFraAnnenService()

                override fun oppdaterMedKvittering(
                    utbetalingId: UUID30,
                    kvittering: Kvittering,
                    sessionContext: SessionContext?,
                ) = kastKanKunKallesFraAnnenService()

                override fun simulerUtbetaling(
                    utbetalingForSimulering: Utbetaling.UtbetalingForSimulering,
                ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
                    kastKanKunKallesFraAnnenService()
                }

                override fun klargjørUtbetaling(
                    utbetaling: Utbetaling.SimulertUtbetaling,
                    transactionContext: TransactionContext,
                ): Either<KunneIkkeKlaregjøreUtbetaling, UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>> {
                    kastKanKunKallesFraAnnenService()
                }

                override fun hentGjeldendeUtbetaling(
                    sakId: UUID,
                    forDato: LocalDate,
                ) = kastKanKunKallesFraAnnenService()
            },
            sak = object : SakService {
                override fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak> {
                    assertHarTilgangTilSak(sakId)

                    return services.sak.hentSak(sakId)
                }

                override fun hentSak(sakId: UUID, sessionContext: SessionContext): Either<FantIkkeSak, Sak> {
                    return services.sak.hentSak(sakId, sessionContext)
                }

                override fun hentSaker(fnr: Fnr): Either<FantIkkeSak, List<Sak>> {
                    return services.sak.hentSaker(fnr).also {
                        it.map { saker -> saker.map { sak -> assertHarTilgangTilSak(sak.id) } }
                    }
                }

                override fun hentSak(fnr: Fnr, type: Sakstype): Either<FantIkkeSak, Sak> {
                    // Siden vi også vil kontrollere på EPS må vi hente ut saken først
                    // og sjekke på hele den (i stedet for å gjøre assertHarTilgangTilPerson(fnr))
                    return services.sak.hentSak(fnr, type).also {
                        it.map { sak -> assertHarTilgangTilSak(sak.id) }
                    }
                }

                override fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak> {
                    return services.sak.hentSak(saksnummer).also {
                        it.map { sak -> assertHarTilgangTilSak(sak.id) }
                    }
                }

                override fun hentSak(hendelseId: HendelseId): Either<FantIkkeSak, Sak> {
                    return services.sak.hentSak(hendelseId).also {
                        it.map { sak -> assertHarTilgangTilSak(sak.id) }
                    }
                }

                override fun hentSakForUtbetalingId(utbetalingId: UUID30) = kastKanKunKallesFraAnnenService()

                override fun hentGjeldendeVedtaksdata(
                    sakId: UUID,
                    periode: Periode,
                ): Either<KunneIkkeHenteGjeldendeVedtaksdata, GjeldendeVedtaksdata?> {
                    assertHarTilgangTilSak(sakId)
                    return services.sak.hentGjeldendeVedtaksdata(sakId, periode)
                }

                override fun historiskGrunnlagForVedtaketsPeriode(
                    sakId: UUID,
                    vedtakId: UUID,
                ): Either<KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak, GjeldendeVedtaksdata> {
                    assertHarTilgangTilSak(sakId)
                    return services.sak.historiskGrunnlagForVedtaketsPeriode(
                        sakId = sakId,
                        vedtakId = vedtakId,
                    )
                }

                override fun hentSakidOgSaksnummer(fnr: Fnr) = kastKanKunKallesFraAnnenService()
                override fun hentSakInfo(sakId: UUID): Either<FantIkkeSak, SakInfo> {
                    kastKanKunKallesFraAnnenService()
                }

                override fun hentSakForRevurdering(revurderingId: RevurderingId): Sak {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.sak.hentSakForRevurdering(revurderingId)
                }

                override fun hentSakForRevurdering(revurderingId: RevurderingId, sessionContext: SessionContext): Sak {
                    kastKanKunKallesFraAnnenService()
                }

                override fun hentSakForSøknadsbehandling(søknadsbehandlingId: SøknadsbehandlingId): Sak {
                    kastKanKunKallesFraAnnenService()
                }

                override fun hentSakForVedtak(vedtakId: UUID): Sak? {
                    kastKanKunKallesFraAnnenService()
                }

                override fun hentSakForSøknad(søknadId: UUID): Either<FantIkkeSak, Sak> {
                    assertHarTilgangTilSøknad(søknadId)
                    return services.sak.hentSakForSøknad(søknadId)
                }

                override fun genererFritekstbrevPåSak(request: OpprettDokumentRequest): Either<KunneIkkeOppretteDokument, Dokument.UtenMetadata> {
                    assertHarTilgangTilSak(request.sakId)
                    return services.sak.genererFritekstbrevPåSak(request)
                }

                override fun genererLagreOgSendFritekstbrevPåSak(request: OpprettDokumentRequest): Either<KunneIkkeOppretteDokument, Dokument.MedMetadata> {
                    assertHarTilgangTilSak(request.sakId)
                    return services.sak.genererLagreOgSendFritekstbrevPåSak(request)
                }

                override fun lagreOgSendOpplastetPdfPåSak(request: JournalførOgSendOpplastetPdfSomBrevCommand): Dokument.MedMetadata {
                    assertHarTilgangTilSak(request.sakId)
                    return services.sak.lagreOgSendOpplastetPdfPåSak(request)
                }

                override fun hentAlleJournalposter(sakId: UUID): Either<KunneIkkeHenteJournalposter, List<Journalpost>> {
                    assertHarTilgangTilSak(sakId)
                    return services.sak.hentAlleJournalposter(sakId)
                }

                override fun oppdaterFødselsnummer(command: OppdaterFødselsnummerPåSakCommand): Either<KunneIkkeOppdatereFødselsnummer, Sak> {
                    assertHarTilgangTilSak(command.sakId)
                    return services.sak.oppdaterFødselsnummer(command)
                }

                override fun hentSakIdSaksnummerOgFnrForAlleSaker() = kastKanKunKallesFraAnnenService()
                override fun hentEpsSaksIderForBrukersSak(sakId: UUID): List<UUID> {
                    assertHarTilgangTilSak(sakId)
                    return services.sak.hentEpsSaksIderForBrukersSak(sakId)
                }

                override fun opprettSak(sak: NySak) {
                    assertHarTilgangTilPerson(sak.fnr)

                    return services.sak.opprettSak(sak)
                }

                override fun hentÅpneBehandlingerForAlleSaker(): List<Behandlingssammendrag> {
                    // vi gjør ikke noe assert fordi vi ikke sender noe sensitiv info.
                    // Samtidig som at dem går gjennom hentSak() når de skal saksbehandle
                    return services.sak.hentÅpneBehandlingerForAlleSaker()
                }

                override fun hentFerdigeBehandlingerForAlleSaker(): List<Behandlingssammendrag> {
                    return services.sak.hentFerdigeBehandlingerForAlleSaker()
                }

                override fun hentAlleredeGjeldendeSakForBruker(fnr: Fnr): AlleredeGjeldendeSakForBruker {
                    assertHarTilgangTilPerson(fnr)
                    return services.sak.hentAlleredeGjeldendeSakForBruker(fnr)
                }
            },
            søknad = object : SøknadService {
                override fun nySøknad(
                    søknadInnhold: SøknadInnhold,
                    identBruker: NavIdentBruker,
                ): Either<KunneIkkeOppretteSøknad, Pair<Saksnummer, Søknad>> {
                    assertHarTilgangTilPerson(søknadInnhold.personopplysninger.fnr)

                    return services.søknad.nySøknad(søknadInnhold, identBruker)
                }

                override fun persisterSøknad(
                    søknad: Søknad.Journalført.MedOppgave.Lukket,
                    sessionContext: SessionContext,
                ) = kastKanKunKallesFraAnnenService()

                override fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad> {
                    assertHarTilgangTilSøknad(søknadId)

                    return services.søknad.hentSøknad(søknadId)
                }

                override fun hentSøknadPdf(søknadId: UUID): Either<KunneIkkeLageSøknadPdf, PdfA> {
                    assertHarTilgangTilSøknad(søknadId)

                    return services.søknad.hentSøknadPdf(søknadId)
                }

                override fun opprettManglendeJournalpostOgOppgave(): OpprettManglendeJournalpostOgOppgaveResultat {
                    // Dette er et driftsendepunkt og vi vil ikke returnere kode 6/7/person-sensitive data.

                    return services.søknad.opprettManglendeJournalpostOgOppgave()
                }
            },
            brev = object : BrevService {

                override fun lagDokument(
                    command: GenererDokumentCommand,
                    id: UUID,
                ): Either<KunneIkkeLageDokument, Dokument.UtenMetadata> {
                    kastKanKunKallesFraAnnenService()
                }

                override fun hentDokument(id: UUID): Either<FantIkkeDokument, Dokument.MedMetadata> {
                    return assertTilgangTilSakOgHentDokument(id)
                }

                override fun lagreDokument(
                    dokument: Dokument.MedMetadata,
                    transactionContext: TransactionContext?,
                ) = kastKanKunKallesFraAnnenService()

                override fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument> {
                    when (hentDokumenterForIdType) {
                        is HentDokumenterForIdType.HentDokumenterForRevurdering -> assertHarTilgangTilRevurdering(
                            RevurderingId(hentDokumenterForIdType.id),
                        )

                        is HentDokumenterForIdType.HentDokumenterForSak -> assertHarTilgangTilSak(
                            hentDokumenterForIdType.id,
                        )

                        is HentDokumenterForIdType.HentDokumenterForSøknad -> assertHarTilgangTilSøknad(
                            hentDokumenterForIdType.id,
                        )

                        is HentDokumenterForIdType.HentDokumenterForVedtak -> assertHarTilgangTilVedtak(
                            hentDokumenterForIdType.id,
                        )

                        is HentDokumenterForIdType.HentDokumenterForKlage -> assertHarTilgangTilKlage(
                            KlageId(hentDokumenterForIdType.id),
                        )
                    }.let {
                        return services.brev.hentDokumenterFor(hentDokumenterForIdType)
                    }
                }
            },
            lukkSøknad = object : LukkSøknadService {
                override fun lukkSøknad(command: LukkSøknadCommand): Triple<Søknad.Journalført.MedOppgave.Lukket, LukketSøknadsbehandling?, Fnr> {
                    assertHarTilgangTilSøknad(command.søknadId)

                    return services.lukkSøknad.lukkSøknad(command)
                }

                override fun lagBrevutkast(
                    command: LukkSøknadCommand,
                ): Pair<Fnr, PdfA> {
                    assertHarTilgangTilSøknad(command.søknadId)

                    return services.lukkSøknad.lagBrevutkast(command)
                }
            },
            oppgave = object : OppgaveService {
                override fun opprettOppgave(config: OppgaveConfig) = kastKanKunKallesFraAnnenService()
                override fun opprettOppgaveMedSystembruker(config: OppgaveConfig) = kastKanKunKallesFraAnnenService()
                override fun lukkOppgave(oppgaveId: OppgaveId, tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs) =
                    kastKanKunKallesFraAnnenService()

                override fun lukkOppgaveMedSystembruker(
                    oppgaveId: OppgaveId,
                    tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs,
                ) = kastKanKunKallesFraAnnenService()

                override fun oppdaterOppgave(
                    oppgaveId: OppgaveId,
                    oppdaterOppgaveInfo: OppdaterOppgaveInfo,
                ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse> = kastKanKunKallesFraAnnenService()

                override fun oppdaterOppgaveMedSystembruker(
                    oppgaveId: OppgaveId,
                    oppdaterOppgaveInfo: OppdaterOppgaveInfo,
                ): Either<KunneIkkeOppdatereOppgave, OppgaveHttpKallResponse> = kastKanKunKallesFraAnnenService()

                override fun hentOppgave(oppgaveId: OppgaveId) = kastKanKunKallesFraAnnenService()
            },
            person = object : PersonService {
                override fun hentPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Person> {
                    assertHarTilgangTilPerson(fnr)
                    return services.person.hentPerson(fnr)
                }

                override fun hentPersonMedSystembruker(fnr: Fnr) = kastKanKunKallesFraAnnenService()

                override fun hentAktørIdMedSystembruker(fnr: Fnr) = kastKanKunKallesFraAnnenService()

                override fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit> {
                    return services.person.sjekkTilgangTilPerson(fnr)
                }

                override fun hentFnrForSak(sakId: UUID) = kastKanKunKallesFraAnnenService()
            },
            søknadsbehandling = SøknadsbehandlingServices(
                iverksettSøknadsbehandlingService = object : IverksettSøknadsbehandlingService {

                    override fun iverksett(command: IverksettSøknadsbehandlingCommand): Either<KunneIkkeIverksetteSøknadsbehandling, Triple<Sak, IverksattSøknadsbehandling, Stønadsvedtak>> {
                        assertHarTilgangTilSøknadsbehandling(command.behandlingId)
                        return services.søknadsbehandling.iverksettSøknadsbehandlingService.iverksett(command)
                    }

                    override fun iverksett(iverksattSøknadsbehandlingResponse: IverksattSøknadsbehandlingResponse<*>) {
                        kastKanKunKallesFraAnnenService()
                    }
                },
                søknadsbehandlingService = object : SøknadsbehandlingService {
                    val service = services.søknadsbehandling.søknadsbehandlingService
                    override fun opprett(
                        request: SøknadsbehandlingService.OpprettRequest,
                        hentSak: (() -> Sak)?,
                    ): Either<KunneIkkeOppretteSøknadsbehandling, Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart>> {
                        assertHarTilgangTilSøknad(request.søknadId)
                        return service.opprett(request, hentSak)
                    }

                    override fun beregn(request: SøknadsbehandlingService.BeregnRequest): Either<SøknadsbehandlingService.KunneIkkeBeregne, BeregnetSøknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId)
                        return service.beregn(request)
                    }

                    override fun simuler(request: SøknadsbehandlingService.SimulerRequest): Either<KunneIkkeSimulereBehandling, SimulertSøknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId)
                        return service.simuler(request)
                    }

                    override fun sendTilAttestering(request: SøknadsbehandlingService.SendTilAttesteringRequest): Either<KunneIkkeSendeSøknadsbehandlingTilAttestering, SøknadsbehandlingTilAttestering> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId)
                        return service.sendTilAttestering(request)
                    }

                    override fun underkjenn(request: SøknadsbehandlingService.UnderkjennRequest): Either<KunneIkkeUnderkjenneSøknadsbehandling, UnderkjentSøknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId)
                        return service.underkjenn(request)
                    }

                    override fun genererBrevutkast(
                        command: BrevutkastForSøknadsbehandlingCommand,
                    ): Either<KunneIkkeGenerereBrevutkastForSøknadsbehandling, Pair<PdfA, Fnr>> {
                        assertHarTilgangTilSøknadsbehandling(command.søknadsbehandlingId)
                        return service.genererBrevutkast(command)
                    }

                    override fun hent(request: SøknadsbehandlingService.HentRequest): Either<SøknadsbehandlingService.FantIkkeBehandling, Søknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId)
                        return service.hent(request)
                    }

                    override fun oppdaterStønadsperiode(request: SøknadsbehandlingService.OppdaterStønadsperiodeRequest): Either<Sak.KunneIkkeOppdatereStønadsperiode, VilkårsvurdertSøknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId)
                        return service.oppdaterStønadsperiode(request)
                    }

                    override fun leggTilUførevilkår(
                        request: LeggTilUførevurderingerRequest,
                        saksbehandler: NavIdentBruker.Saksbehandler,
                    ): Either<SøknadsbehandlingService.KunneIkkeLeggeTilUføreVilkår, Søknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId as SøknadsbehandlingId)
                        return service.leggTilUførevilkår(request, saksbehandler)
                    }

                    override fun leggTilLovligOpphold(
                        request: LeggTilLovligOppholdRequest,
                        saksbehandler: NavIdentBruker.Saksbehandler,
                    ): Either<KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling, Søknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId as SøknadsbehandlingId)
                        return service.leggTilLovligOpphold(request, saksbehandler)
                    }

                    override fun leggTilFamiliegjenforeningvilkår(
                        request: LeggTilFamiliegjenforeningRequest,
                        saksbehandler: NavIdentBruker.Saksbehandler,
                    ): Either<SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService, Søknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId as SøknadsbehandlingId)
                        return service.leggTilFamiliegjenforeningvilkår(request, saksbehandler)
                    }

                    override fun leggTilFradragsgrunnlag(
                        request: LeggTilFradragsgrunnlagRequest,
                        saksbehandler: NavIdentBruker.Saksbehandler,
                    ): Either<SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag, Søknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId as SøknadsbehandlingId)
                        return service.leggTilFradragsgrunnlag(request, saksbehandler)
                    }

                    override fun leggTilFormuevilkår(
                        request: LeggTilFormuevilkårRequest,
                    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår, Søknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId as SøknadsbehandlingId)
                        return service.leggTilFormuevilkår(request)
                    }

                    override fun hentForSøknad(søknadId: UUID) = kastKanKunKallesFraAnnenService()

                    override fun persisterSøknadsbehandling(
                        lukketSøknadbehandling: LukketSøknadsbehandling,
                        tx: TransactionContext,
                    ) = kastKanKunKallesFraAnnenService()

                    override fun leggTilUtenlandsopphold(
                        request: LeggTilFlereUtenlandsoppholdRequest,
                        saksbehandler: NavIdentBruker.Saksbehandler,
                    ): Either<SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold, VilkårsvurdertSøknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId as SøknadsbehandlingId)
                        return service.leggTilUtenlandsopphold(request, saksbehandler)
                    }

                    override fun leggTilOpplysningspliktVilkår(request: LeggTilOpplysningspliktRequest.Søknadsbehandling): Either<KunneIkkeLeggeTilOpplysningsplikt, VilkårsvurdertSøknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId)
                        return service.leggTilOpplysningspliktVilkår(request)
                    }

                    override fun leggTilPensjonsVilkår(
                        request: LeggTilPensjonsVilkårRequest,
                        saksbehandler: NavIdentBruker.Saksbehandler,
                    ): Either<KunneIkkeLeggeTilPensjonsVilkår, VilkårsvurdertSøknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId as SøknadsbehandlingId)
                        return service.leggTilPensjonsVilkår(request, saksbehandler)
                    }

                    override fun leggTilFlyktningVilkår(
                        request: LeggTilFlyktningVilkårRequest,
                        saksbehandler: NavIdentBruker.Saksbehandler,
                    ): Either<KunneIkkeLeggeTilFlyktningVilkår, VilkårsvurdertSøknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId as SøknadsbehandlingId)
                        return service.leggTilFlyktningVilkår(request, saksbehandler)
                    }

                    override fun leggTilFastOppholdINorgeVilkår(
                        request: LeggTilFastOppholdINorgeRequest,
                        saksbehandler: NavIdentBruker.Saksbehandler,
                    ): Either<KunneIkkeLeggeFastOppholdINorgeVilkår, VilkårsvurdertSøknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId as SøknadsbehandlingId)
                        return service.leggTilFastOppholdINorgeVilkår(request, saksbehandler)
                    }

                    override fun leggTilPersonligOppmøteVilkår(
                        request: LeggTilPersonligOppmøteVilkårRequest,
                        saksbehandler: NavIdentBruker.Saksbehandler,
                    ): Either<KunneIkkeLeggeTilPersonligOppmøteVilkårForSøknadsbehandling, VilkårsvurdertSøknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId as SøknadsbehandlingId)
                        return service.leggTilPersonligOppmøteVilkår(request, saksbehandler)
                    }

                    override fun leggTilInstitusjonsoppholdVilkår(
                        request: LeggTilInstitusjonsoppholdVilkårRequest,
                        saksbehandler: NavIdentBruker.Saksbehandler,
                    ): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, VilkårsvurdertSøknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId as SøknadsbehandlingId)
                        return service.leggTilInstitusjonsoppholdVilkår(request, saksbehandler)
                    }

                    override fun leggTilBosituasjongrunnlag(
                        request: LeggTilBosituasjonerCommand,
                        saksbehandler: NavIdentBruker.Saksbehandler,
                    ): Either<behandling.søknadsbehandling.domain.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlag, VilkårsvurdertSøknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(request.behandlingId as SøknadsbehandlingId)
                        return service.leggTilBosituasjongrunnlag(request, saksbehandler)
                    }

                    override fun oppdaterSkattegrunnlag(
                        command: SøknadsbehandlingSkattCommand,
                    ): Either<KunneIkkeLeggeTilSkattegrunnlag, Søknadsbehandling> {
                        assertHarTilgangTilSøknadsbehandling(command.behandlingId)
                        return service.oppdaterSkattegrunnlag(command)
                    }

                    override fun lagre(søknadsbehandling: Søknadsbehandling) = kastKanKunKallesFraAnnenService()
                    override fun hentSisteInnvilgetSøknadsbehandlingGrunnlagForSakFiltrerVekkSøknadsbehandling(
                        sakId: UUID,
                        søknadsbehandlingId: SøknadsbehandlingId,
                    ): Either<FeilVedHentingAvGjeldendeVedtaksdataForPeriode, Pair<Periode, GrunnlagsdataOgVilkårsvurderinger>> {
                        assertHarTilgangTilSak(sakId)
                        return service.hentSisteInnvilgetSøknadsbehandlingGrunnlagForSakFiltrerVekkSøknadsbehandling(
                            sakId,
                            søknadsbehandlingId,
                        )
                    }
                },
            ),
            ferdigstillVedtak = object : FerdigstillVedtakService {
                override fun ferdigstillVedtakEtterUtbetaling(
                    utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering,
                    transactionContext: TransactionContext?,
                ) = kastKanKunKallesFraAnnenService()

                override fun ferdigstillVedtak(vedtakId: UUID): Either<KunneIkkeFerdigstilleVedtak, VedtakSomKanRevurderes> {
                    assertHarTilgangTilVedtak(vedtakId)
                    return services.ferdigstillVedtak.ferdigstillVedtak(vedtakId)
                }

                override fun lukkOppgaveMedBruker(
                    behandling: Stønadsbehandling,
                    tilordnetRessurs: OppdaterOppgaveInfo.TilordnetRessurs,
                ) = kastKanKunKallesFraAnnenService()
            },
            revurdering = object : RevurderingService {
                override fun hentRevurdering(revurderingId: RevurderingId): AbstraktRevurdering? {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.hentRevurdering(revurderingId)
                }

                override fun opprettRevurdering(
                    command: OpprettRevurderingCommand,
                ): Either<KunneIkkeOppretteRevurdering, OpprettetRevurdering> {
                    assertHarTilgangTilSak(command.sakId)
                    return services.revurdering.opprettRevurdering(command)
                }

                override fun oppdaterRevurdering(
                    command: OppdaterRevurderingCommand,
                ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
                    assertHarTilgangTilRevurdering(command.revurderingId)
                    return services.revurdering.oppdaterRevurdering(command)
                }

                override fun beregnOgSimuler(
                    revurderingId: RevurderingId,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeBeregneOgSimulereRevurdering, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.beregnOgSimuler(
                        revurderingId = revurderingId,
                        saksbehandler = saksbehandler,
                    )
                }

                override fun lagreOgSendForhåndsvarsel(
                    revurderingId: RevurderingId,
                    utførtAv: NavIdentBruker.Saksbehandler,
                    fritekst: String,
                ): Either<KunneIkkeForhåndsvarsle, Revurdering> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.lagreOgSendForhåndsvarsel(
                        revurderingId,
                        utførtAv,
                        fritekst,
                    )
                }

                override fun lagBrevutkastForForhåndsvarsling(
                    revurderingId: RevurderingId,
                    utførtAv: NavIdentBruker.Saksbehandler,
                    fritekst: String,
                ): Either<KunneIkkeLageBrevutkastForRevurdering, PdfA> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.lagBrevutkastForForhåndsvarsling(revurderingId, utførtAv, fritekst)
                }

                override fun sendTilAttestering(
                    request: SendTilAttesteringRequest,
                ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering> {
                    assertHarTilgangTilRevurdering(request.revurderingId)
                    return services.revurdering.sendTilAttestering(request)
                }

                override fun leggTilBrevvalg(request: LeggTilBrevvalgRequest): Either<KunneIkkeLeggeTilVedtaksbrevvalg, Revurdering> {
                    assertHarTilgangTilRevurdering(request.revurderingId)
                    return services.revurdering.leggTilBrevvalg(request)
                }

                override fun lagBrevutkastForRevurdering(
                    revurderingId: RevurderingId,
                ): Either<KunneIkkeLageBrevutkastForRevurdering, PdfA> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.lagBrevutkastForRevurdering(revurderingId)
                }

                override fun iverksett(
                    revurderingId: RevurderingId,
                    attestant: NavIdentBruker.Attestant,
                ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.iverksett(revurderingId, attestant)
                }

                override fun underkjenn(
                    revurderingId: RevurderingId,
                    attestering: Attestering.Underkjent,
                ): Either<KunneIkkeUnderkjenneRevurdering, UnderkjentRevurdering> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.underkjenn(revurderingId, attestering)
                }

                override fun leggTilUførevilkår(
                    request: LeggTilUførevurderingerRequest,
                ): Either<KunneIkkeLeggeTilUføreVilkår, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId as RevurderingId)
                    return services.revurdering.leggTilUførevilkår(request)
                }

                override fun leggTilUtenlandsopphold(
                    request: LeggTilFlereUtenlandsoppholdRequest,
                ): Either<KunneIkkeLeggeTilUtenlandsopphold, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId as RevurderingId)
                    return services.revurdering.leggTilUtenlandsopphold(request)
                }

                override fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradragsgrunnlag, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId as RevurderingId)
                    return services.revurdering.leggTilFradragsgrunnlag(request)
                }

                override fun leggTilBosituasjongrunnlag(request: LeggTilBosituasjonerForRevurderingCommand): Either<KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId as RevurderingId)
                    return services.revurdering.leggTilBosituasjongrunnlag(request)
                }

                override fun leggTilFormuegrunnlag(request: LeggTilFormuevilkårRequest): Either<KunneIkkeLeggeTilFormuegrunnlag, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId as RevurderingId)
                    return services.revurdering.leggTilFormuegrunnlag(request)
                }

                override fun avsluttRevurdering(
                    revurderingId: RevurderingId,
                    begrunnelse: String,
                    brevvalg: Brevvalg.SaksbehandlersValg?,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeAvslutteRevurdering, AbstraktRevurdering> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.avsluttRevurdering(
                        revurderingId,
                        begrunnelse,
                        brevvalg,
                        saksbehandler,
                    )
                }

                override fun leggTilOpplysningspliktVilkår(request: LeggTilOpplysningspliktRequest.Revurdering): Either<KunneIkkeLeggeTilOpplysningsplikt, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId)
                    return services.revurdering.leggTilOpplysningspliktVilkår(request)
                }

                override fun leggTilPensjonsVilkår(request: LeggTilPensjonsVilkårRequest): Either<KunneIkkeLeggeTilPensjonsVilkår, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId as RevurderingId)
                    return services.revurdering.leggTilPensjonsVilkår(request)
                }

                override fun leggTilLovligOppholdVilkår(
                    request: LeggTilLovligOppholdRequest,
                ) = assertHarTilgangTilRevurdering(request.behandlingId as RevurderingId).let {
                    services.revurdering.leggTilLovligOppholdVilkår(request)
                }

                override fun leggTilFlyktningVilkår(request: LeggTilFlyktningVilkårRequest): Either<KunneIkkeLeggeTilFlyktningVilkår, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId as RevurderingId)
                    return services.revurdering.leggTilFlyktningVilkår(request)
                }

                override fun leggTilFastOppholdINorgeVilkår(request: LeggTilFastOppholdINorgeRequest): Either<KunneIkkeLeggeFastOppholdINorgeVilkår, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId as RevurderingId)
                    return services.revurdering.leggTilFastOppholdINorgeVilkår(request)
                }

                override fun leggTilPersonligOppmøteVilkår(request: LeggTilPersonligOppmøteVilkårRequest): Either<KunneIkkeLeggeTilPersonligOppmøteVilkårForRevurdering, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId as RevurderingId)
                    return services.revurdering.leggTilPersonligOppmøteVilkår(request)
                }

                override fun leggTilInstitusjonsoppholdVilkår(request: LeggTilInstitusjonsoppholdVilkårRequest): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId as RevurderingId)
                    return services.revurdering.leggTilInstitusjonsoppholdVilkår(request)
                }

                override fun defaultTransactionContext(): TransactionContext {
                    return services.revurdering.defaultTransactionContext()
                }

                override fun lagBrevutkastForAvslutting(
                    revurderingId: RevurderingId,
                    fritekst: String,
                    avsluttetAv: NavIdentBruker,
                ): Either<KunneIkkeLageBrevutkastForAvsluttingAvRevurdering, Pair<Fnr, PdfA>> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.lagBrevutkastForAvslutting(revurderingId, fritekst, avsluttetAv)
                }
            },
            stansYtelse = object : StansYtelseService {

                override fun stansAvYtelse(request: StansYtelseRequest): Either<KunneIkkeStanseYtelse, StansAvYtelseRevurdering.SimulertStansAvYtelse> {
                    assertHarTilgangTilSak(request.sakId)
                    return services.stansYtelse.stansAvYtelse(request)
                }

                override fun stansAvYtelseITransaksjon(
                    request: StansYtelseRequest,
                    transactionContext: TransactionContext,
                ): StansAvYtelseITransaksjonResponse {
                    kastKanKunKallesFraAnnenService()
                }

                override fun iverksettStansAvYtelse(
                    revurderingId: RevurderingId,
                    attestant: NavIdentBruker.Attestant,
                ): Either<KunneIkkeIverksetteStansYtelse, StansAvYtelseRevurdering.IverksattStansAvYtelse> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.stansYtelse.iverksettStansAvYtelse(
                        revurderingId = revurderingId,
                        attestant = attestant,
                    )
                }

                override fun iverksettStansAvYtelseITransaksjon(
                    revurderingId: RevurderingId,
                    attestant: NavIdentBruker.Attestant,
                    transactionContext: TransactionContext,
                ): IverksettStansAvYtelseITransaksjonResponse {
                    kastKanKunKallesFraAnnenService()
                }
            },
            gjenopptaYtelse = object : GjenopptaYtelseService {
                override fun gjenopptaYtelse(
                    request: GjenopptaYtelseRequest,
                ): Either<KunneIkkeSimulereGjenopptakAvYtelse, Pair<GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse, ForskjellerMellomUtbetalingOgSimulering?>> {
                    assertHarTilgangTilSak(request.sakId)
                    return services.gjenopptaYtelse.gjenopptaYtelse(request)
                }

                override fun iverksettGjenopptakAvYtelse(
                    revurderingId: RevurderingId,
                    attestant: NavIdentBruker.Attestant,
                ): Either<KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.gjenopptaYtelse.iverksettGjenopptakAvYtelse(revurderingId, attestant)
                }
            },
            vedtakService = object : VedtakService {
                override fun lagre(vedtak: Vedtak) = kastKanKunKallesFraAnnenService()

                override fun lagreITransaksjon(
                    vedtak: Vedtak,
                    tx: TransactionContext,
                ) = kastKanKunKallesFraAnnenService()

                override fun hentForVedtakId(vedtakId: UUID) = kastKanKunKallesFraAnnenService()

                override fun hentForRevurderingId(revurderingId: RevurderingId) = kastKanKunKallesFraAnnenService()

                override fun hentJournalpostId(vedtakId: UUID) = kastKanKunKallesFraAnnenService()

                override fun hentInnvilgetFnrForMåned(måned: Måned): InnvilgetForMåned {
                    return services.vedtakService.hentInnvilgetFnrForMåned(måned)
                }

                override fun hentInnvilgetFnrFraOgMedMåned(måned: Måned, inkluderEps: Boolean): List<Fnr> {
                    return services.vedtakService.hentInnvilgetFnrFraOgMedMåned(måned, inkluderEps)
                }

                override fun hentForUtbetaling(utbetalingId: UUID30, sessionContext: SessionContext?) =
                    kastKanKunKallesFraAnnenService()

                override fun hentForBrukerFødselsnumreOgFraOgMedMåned(
                    fødselsnumre: List<Fnr>,
                    fraOgMed: Måned,
                ) = kastKanKunKallesFraAnnenService()

                override fun hentForEpsFødselsnumreOgFraOgMedMåned(
                    fnr: List<Fnr>,
                    fraOgMedEllerSenere: Måned,
                ) = kastKanKunKallesFraAnnenService()

                override fun hentSøknadsbehandlingsvedtakFraOgMed(fraOgMed: LocalDate): List<UUID> =
                    kastKanKunKallesFraAnnenService()

                override fun startNySøknadsbehandlingForAvslag(
                    sakId: UUID,
                    vedtakId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeStarteNySøknadsbehandling, Søknadsbehandling> {
                    assertHarTilgangTilVedtak(vedtakId)
                    return services.vedtakService.startNySøknadsbehandlingForAvslag(sakId, vedtakId, saksbehandler)
                }
            },
            nøkkeltallService = object : NøkkeltallService {
                override fun hentNøkkeltall(): Nøkkeltall {
                    return services.nøkkeltallService.hentNøkkeltall()
                }
            },
            avslåSøknadManglendeDokumentasjonService = object : AvslåSøknadManglendeDokumentasjonService {
                override fun avslå(command: AvslåManglendeDokumentasjonCommand): Either<KunneIkkeAvslåSøknad, Sak> {
                    assertHarTilgangTilSøknad(command.søknadId)
                    return services.avslåSøknadManglendeDokumentasjonService.avslå(command)
                }

                override fun genererBrevForhåndsvisning(command: AvslåManglendeDokumentasjonCommand): Either<KunneIkkeLageDokument, Pair<Fnr, PdfA>> {
                    assertHarTilgangTilSøknad(command.søknadId)
                    return services.avslåSøknadManglendeDokumentasjonService.genererBrevForhåndsvisning(command)
                }
            },
            klageService = object : KlageService {
                override fun opprett(request: NyKlageRequest): Either<KunneIkkeOppretteKlage, OpprettetKlage> {
                    assertHarTilgangTilSak(request.sakId)
                    return services.klageService.opprett(request)
                }

                override fun vilkårsvurder(request: VurderKlagevilkårRequest): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
                    assertHarTilgangTilKlage(request.klageId)
                    return services.klageService.vilkårsvurder(request)
                }

                override fun bekreftVilkårsvurderinger(
                    klageId: KlageId,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeBekrefteKlagesteg, VilkårsvurdertKlage.Bekreftet> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.bekreftVilkårsvurderinger(klageId, saksbehandler)
                }

                override fun vurder(request: KlageVurderingerRequest): Either<KunneIkkeVurdereKlage, VurdertKlage> {
                    assertHarTilgangTilKlage(request.klageId)
                    return services.klageService.vurder(request)
                }

                override fun bekreftVurderinger(
                    klageId: KlageId,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeBekrefteKlagesteg, VurdertKlage.Bekreftet> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.bekreftVurderinger(klageId, saksbehandler)
                }

                override fun leggTilAvvistFritekstTilBrev(
                    klageId: KlageId,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                    fritekst: String,
                ): Either<KunneIkkeLeggeTilFritekstForAvvist, AvvistKlage> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.leggTilAvvistFritekstTilBrev(klageId, saksbehandler, fritekst)
                }

                override fun sendTilAttestering(
                    klageId: KlageId,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeSendeKlageTilAttestering, KlageTilAttestering> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.sendTilAttestering(klageId, saksbehandler)
                }

                override fun underkjenn(request: UnderkjennKlageRequest): Either<KunneIkkeUnderkjenneKlage, Klage> {
                    assertHarTilgangTilKlage(request.klageId)
                    return services.klageService.underkjenn(request)
                }

                override fun oversend(
                    klageId: KlageId,
                    attestant: NavIdentBruker.Attestant,
                ): Either<KunneIkkeOversendeKlage, OversendtKlage> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.oversend(klageId, attestant)
                }

                override fun iverksettAvvistKlage(
                    klageId: KlageId,
                    attestant: NavIdentBruker.Attestant,
                ): Either<KunneIkkeIverksetteAvvistKlage, IverksattAvvistKlage> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.iverksettAvvistKlage(klageId, attestant)
                }

                override fun brevutkast(
                    klageId: KlageId,
                    ident: NavIdentBruker,
                ): Either<KunneIkkeLageBrevutkast, PdfA> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.brevutkast(klageId, ident)
                }

                override fun avslutt(
                    klageId: KlageId,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                    begrunnelse: String,
                ): Either<KunneIkkeAvslutteKlage, AvsluttetKlage> {
                    assertHarTilgangTilKlage(klageId)
                    return services.klageService.avslutt(
                        klageId = klageId,
                        saksbehandler = saksbehandler,
                        begrunnelse = begrunnelse,
                    )
                }
            },
            klageinstanshendelseService = object : KlageinstanshendelseService {
                override fun lagre(hendelse: UprosessertKlageinstanshendelse) = kastKanKunKallesFraAnnenService()
                override fun håndterUtfallFraKlageinstans(
                    deserializeAndMap: (id: UUID, opprettet: Tidspunkt, json: String) -> Either<KunneIkkeTolkeKlageinstanshendelse, TolketKlageinstanshendelse>,
                ) = kastKanKunKallesFraAnnenService()
            },
            reguleringService = object : ReguleringService {
                override fun startAutomatiskRegulering(
                    fraOgMedMåned: Måned,
                    supplement: Reguleringssupplement,
                ): List<Either<KunneIkkeOppretteRegulering, Regulering>> {
                    return services.reguleringService.startAutomatiskRegulering(fraOgMedMåned, supplement)
                }

                override fun startAutomatiskReguleringForInnsyn(
                    command: StartAutomatiskReguleringForInnsynCommand,
                ) {
                    return services.reguleringService.startAutomatiskReguleringForInnsyn(command)
                }

                override fun avslutt(
                    reguleringId: ReguleringId,
                    avsluttetAv: NavIdentBruker,
                ): Either<KunneIkkeAvslutte, AvsluttetRegulering> {
                    return services.reguleringService.avslutt(reguleringId, avsluttetAv)
                }

                override fun hentStatusForÅpneManuelleReguleringer(): List<ReguleringSomKreverManuellBehandling> {
                    return services.reguleringService.hentStatusForÅpneManuelleReguleringer()
                }

                override fun hentSakerMedÅpenBehandlingEllerStans(): List<Saksnummer> {
                    return services.reguleringService.hentSakerMedÅpenBehandlingEllerStans()
                }

                override fun regulerManuelt(
                    reguleringId: ReguleringId,
                    uføregrunnlag: List<Uføregrunnlag>,
                    fradrag: List<Fradragsgrunnlag>,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeRegulereManuelt, IverksattRegulering> {
                    return services.reguleringService.regulerManuelt(
                        reguleringId,
                        uføregrunnlag,
                        fradrag,
                        saksbehandler,
                    )
                }

                override fun oppdaterReguleringerMedSupplement(
                    supplement: Reguleringssupplement,
                ) {
                    return services.reguleringService.oppdaterReguleringerMedSupplement(supplement)
                }
            },
            sendPåminnelserOmNyStønadsperiodeService = object : SendPåminnelserOmNyStønadsperiodeService {
                override fun sendPåminnelser(): SendPåminnelseNyStønadsperiodeContext {
                    kastKanKunKallesFraAnnenService()
                }
            },
            skatteService = object : SkatteService {
                override fun hentSamletSkattegrunnlag(
                    fnr: Fnr,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Skattegrunnlag {
                    assertHarTilgangTilPerson(fnr)
                    return services.skatteService.hentSamletSkattegrunnlag(fnr, saksbehandler)
                }

                override fun hentSamletSkattegrunnlagForÅr(
                    fnr: Fnr,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                    yearRange: YearRange,
                ): Skattegrunnlag {
                    assertHarTilgangTilPerson(fnr)
                    return services.skatteService.hentSamletSkattegrunnlagForÅr(fnr, saksbehandler, yearRange)
                }

                override fun hentOgLagSkattePdf(request: FrioppslagSkattRequest): Either<KunneIkkeHenteOgLagePdfAvSkattegrunnlag, PdfA> {
                    request.fnr?.let { assertHarTilgangTilPerson(it) }
                    request.epsFnr?.let { assertHarTilgangTilPerson(it) }
                    return services.skatteService.hentOgLagSkattePdf(request)
                }

                override fun hentLagOgJournalførSkattePdf(request: FrioppslagSkattRequest): Either<KunneIkkeGenerereSkattePdfOgJournalføre, PdfA> {
                    request.fnr?.let { assertHarTilgangTilPerson(it) }
                    request.epsFnr?.let { assertHarTilgangTilPerson(it) }
                    return services.skatteService.hentLagOgJournalførSkattePdf(request)
                }
            },
            kontrollsamtaleSetup = object : KontrollsamtaleSetup {
                override val kontrollsamtaleService: KontrollsamtaleService = object : KontrollsamtaleService {
                    val service = services.kontrollsamtaleSetup.kontrollsamtaleService
                    override fun nyDato(
                        sakId: UUID,
                        dato: LocalDate,
                    ): Either<KunneIkkeSetteNyDatoForKontrollsamtale, Unit> {
                        assertHarTilgangTilSak(sakId)
                        return service.nyDato(sakId, dato)
                    }

                    override fun hentNestePlanlagteKontrollsamtale(
                        sakId: UUID,
                        sessionContext: SessionContext?,
                    ): Either<KunneIkkeHenteKontrollsamtale, Kontrollsamtale> {
                        assertHarTilgangTilSak(sakId)
                        return service.hentNestePlanlagteKontrollsamtale(sakId, sessionContext)
                    }

                    override fun hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato: LocalDate) =
                        kastKanKunKallesFraAnnenService()

                    override fun lagre(kontrollsamtale: Kontrollsamtale, sessionContext: SessionContext?) =
                        kastKanKunKallesFraAnnenService()

                    override fun hentKontrollsamtaler(sakId: UUID): Kontrollsamtaler {
                        assertHarTilgangTilSak(sakId)
                        return service.hentKontrollsamtaler(sakId)
                    }

                    override fun annullerKontrollsamtale(
                        sakId: UUID,
                        kontrollsamtaleId: UUID,
                        sessionContext: SessionContext?,
                    ): Either<KunneIkkeAnnullereKontrollsamtale, Kontrollsamtale> {
                        assertHarTilgangTilSak(sakId)
                        return service.annullerKontrollsamtale(sakId, kontrollsamtaleId, sessionContext)
                    }

                    override fun opprettKontrollsamtale(
                        command: OpprettKontrollsamtaleCommand,
                        sessionContext: SessionContext?,
                    ): Either<KanIkkeOppretteKontrollsamtale, Kontrollsamtale> {
                        return service.opprettKontrollsamtale(command, sessionContext)
                    }

                    override fun oppdaterInnkallingsmånedPåKontrollsamtale(
                        command: OppdaterInnkallingsmånedPåKontrollsamtaleCommand,
                        sessionContext: SessionContext?,
                    ): Either<KunneIkkeOppdatereInnkallingsmånedPåKontrollsamtale, Kontrollsamtale> {
                        return service.oppdaterInnkallingsmånedPåKontrollsamtale(command, sessionContext)
                    }

                    override fun oppdaterStatusPåKontrollsamtale(
                        command: OppdaterStatusPåKontrollsamtaleCommand,
                        sessionContext: SessionContext?,
                    ): Either<KunneIkkeOppdatereStatusPåKontrollsamtale, Kontrollsamtale> {
                        return service.oppdaterStatusPåKontrollsamtale(command, sessionContext)
                    }

                    override fun kallInn(
                        kontrollsamtale: Kontrollsamtale,
                    ) = kastKanKunKallesFraAnnenService()

                    override fun hentPlanlagteKontrollsamtaler(
                        sessionContext: SessionContext?,
                    ) = kastKanKunKallesFraAnnenService()

                    override fun hentFristUtløptFørEllerPåDato(fristFørEllerPåDato: LocalDate) =
                        kastKanKunKallesFraAnnenService()
                }

                override val annullerKontrollsamtaleService: AnnullerKontrollsamtaleVedOpphørService
                    get() = kastKanKunKallesFraAnnenService()

                override val opprettPlanlagtKontrollsamtaleService: OpprettKontrollsamtaleVedNyStønadsperiodeService
                    get() = kastKanKunKallesFraAnnenService()

                override val utløptFristForKontrollsamtaleService: UtløptFristForKontrollsamtaleService
                    get() = kastKanKunKallesFraAnnenService()
            },
            resendStatistikkhendelserService =
            object : ResendStatistikkhendelserService {
                override fun resendIverksattSøknadsbehandling(fraOgMedDato: LocalDate) {
                    // Driftsendepunkt, ingen direkteoppslag på person og ingen returdata
                    services.resendStatistikkhendelserService.resendIverksattSøknadsbehandling(fraOgMedDato)
                }

                override fun resendStatistikkForVedtak(vedtakId: UUID, requiredType: KClass<*>?): Either<Unit, Unit> {
                    // Driftsendepunkt - returnerer ikke data, bare status
                    return services.resendStatistikkhendelserService.resendStatistikkForVedtak(vedtakId, requiredType)
                }
            },
            personhendelseService = object : PersonhendelseService {
                override fun prosesserNyHendelse(fraOgMed: Måned, personhendelse: Personhendelse.IkkeTilknyttetSak) {
                    // Driftsendepunkt ingen returdata
                    services.personhendelseService.prosesserNyHendelse(fraOgMed, personhendelse)
                }

                override fun opprettOppgaverForPersonhendelser() {
                    // Driftsendepunkt ingen returdata
                    services.personhendelseService.opprettOppgaverForPersonhendelser()
                }

                override fun dryRunPersonhendelser(
                    fraOgMed: Måned,
                    personhendelser: List<Personhendelse.IkkeTilknyttetSak>,
                ): DryrunResult {
                    // Driftsendepunkt - minimal returdata
                    return services.personhendelseService.dryRunPersonhendelser(fraOgMed, personhendelser)
                }
            },
        )
    }

    /**
     * Denne skal kun brukes fra en annen service.
     * Når en service bruker en annen service, vil den ha den ikke-proxyede versjonen.
     * Vi kaster derfor her for å unngå at noen bruker metoden fra feil plass (som ville ha omgått tilgangssjekk).
     */
    private fun kastKanKunKallesFraAnnenService(): Nothing =
        throw IllegalStateException("This should only be called from another service")

    private fun assertHarTilgangTilPerson(fnr: Fnr) {
        services.person.sjekkTilgangTilPerson(fnr).getOrElse {
            throw Tilgangssjekkfeil(it, fnr)
        }
    }

    private fun assertHarTilgangTilSak(sakId: UUID) {
        personRepo.hentFnrForSak(sakId).forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilSøknad(søknadId: UUID) {
        personRepo.hentFnrForSøknad(søknadId).forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilSøknadsbehandling(behandlingId: SøknadsbehandlingId) {
        personRepo.hentFnrForBehandling(behandlingId.value).forEach { assertHarTilgangTilPerson(it) }
    }

    @Suppress("unused")
    private fun assertHarTilgangTilUtbetaling(utbetalingId: UUID30) {
        personRepo.hentFnrForUtbetaling(utbetalingId).forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilRevurdering(revurderingId: RevurderingId) {
        personRepo.hentFnrForRevurdering(revurderingId.value).forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilVedtak(vedtakId: UUID) {
        personRepo.hentFnrForVedtak(vedtakId).forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilKlage(klageId: KlageId) {
        personRepo.hentFnrForKlage(klageId.value).forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertTilgangTilSakOgHentDokument(id: UUID): Either<FantIkkeDokument, Dokument.MedMetadata> {
        return services.brev.hentDokument(id).fold(
            ifLeft = {
                it.left()
            },
            ifRight = {
                assertHarTilgangTilSak(it.metadata.sakId)
                it.right()
            },
        )
    }
}

class Tilgangssjekkfeil(val feil: KunneIkkeHentePerson, val fnr: Fnr) : RuntimeException("Underliggende feil: $feil")
