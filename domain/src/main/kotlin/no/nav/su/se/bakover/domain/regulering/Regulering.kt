package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.domain.Stønadsbehandling
import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import beregning.domain.Beregning
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import vilkår.common.domain.Vurdering
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.StøtterIkkeHentingAvEksternGrunnlag
import vilkår.vurderinger.domain.erGyldigTilstand
import økonomi.domain.simulering.Simulering
import java.time.Clock
import java.util.UUID

fun Regulering.inneholderAvslag(): Boolean = this.vilkårsvurderinger.resultat() is Vurdering.Avslag

/**
 * Det knyttes et slikt objekt til hver regulering, både manuelle og automatiske, eller null dersom vi ikke har slike data.
 * Den vil være basert på eksterne data (både fil og tjenester). Merk at det er viktig og lagre originaldata, f.eks. i hendelser.
 *
 * @property bruker reguleringsdata/fradrag fra eksterne kilder for bruker. Kan være null dersom bruker ikke har fradrag fra eksterne kilder.
 * @property eps reguleringsdata/fradrag fra eksterne kilder for ingen, en eller flere EPS, eller vi har hentet regulerte fradrag på EPS.
 */
data class EksternSupplementRegulering(
    val bruker: ReguleringssupplementFor?,
    val eps: List<ReguleringssupplementFor>,
)

sealed interface Regulering : Stønadsbehandling {
    override val id: ReguleringId
    override val beregning: Beregning?
    override val simulering: Simulering?
    override val eksterneGrunnlag: EksterneGrunnlag get() = StøtterIkkeHentingAvEksternGrunnlag
    override val grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderingerRevurdering
    override val vilkårsvurderinger: VilkårsvurderingerRevurdering get() = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger
    val saksbehandler: NavIdentBruker.Saksbehandler
    val reguleringstype: Reguleringstype

    /**
     * Supplementet inneholder informasjon som skal brukes for å oppdatere grunnlagene
     * Supplementet hentes fra eksterne kilder
     */
    val eksternSupplementRegulering: EksternSupplementRegulering

    fun erÅpen(): Boolean

    /** true dersom dette er en iverksatt regulering, false ellers. */
    val erFerdigstilt: Boolean

    companion object {

        private val log: Logger = LoggerFactory.getLogger(this::class.java)

        /**
         * @param clock Brukes kun dersom [opprettet] ikke sendes inn.
         */
        fun opprettRegulering(
            id: ReguleringId = ReguleringId.generer(),
            sakId: UUID,
            saksnummer: Saksnummer,
            fnr: Fnr,
            gjeldendeVedtaksdata: GjeldendeVedtaksdata,
            clock: Clock,
            opprettet: Tidspunkt = Tidspunkt.now(clock),
            sakstype: Sakstype,
            eksternSupplementRegulering: EksternSupplementRegulering,
        ): Either<LagerIkkeReguleringDaDenneUansettMåRevurderes, OpprettetRegulering> {
            val reguleringstype =
                gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger.sjekkOmGrunnlagOgVilkårErKonsistent().fold(
                    { konsistensproblemer ->
                        val message =
                            "Kunne ikke opprette regulering for saksnummer $saksnummer." +
                                " Grunnlag er ikke konsistente. Vi kan derfor ikke beregne denne. Vi klarer derfor ikke å bestemme om denne allerede er regulert. Problemer: [$konsistensproblemer]"
                        if (konsistensproblemer.erGyldigTilstand()) {
                            log.info(message)
                        } else {
                            log.error(message)
                        }
                        return LagerIkkeReguleringDaDenneUansettMåRevurderes.left()
                    },
                    {
                        gjeldendeVedtaksdata.utledReguleringstype(eksternSupplementRegulering)
                    },
                )

            // TODO må oppdatere grunnlagene med verdiene i supplementet
            return OpprettetRegulering(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                saksbehandler = NavIdentBruker.Saksbehandler.systembruker(),
                fnr = fnr,
                grunnlagsdataOgVilkårsvurderinger = gjeldendeVedtaksdata.grunnlagsdataOgVilkårsvurderinger,
                beregning = null,
                simulering = null,
                reguleringstype = reguleringstype,
                sakstype = sakstype,
                eksternSupplementRegulering = eksternSupplementRegulering,
            ).right()
        }
    }

    data object LagerIkkeReguleringDaDenneUansettMåRevurderes
}
