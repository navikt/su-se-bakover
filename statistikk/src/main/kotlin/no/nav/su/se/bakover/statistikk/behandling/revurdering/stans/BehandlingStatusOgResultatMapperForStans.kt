// package no.nav.su.se.bakover.statistikk.behandling.revurdering.stans
//
// import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
// import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
// import no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat
// import no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus
// import no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat
// import org.slf4j.LoggerFactory
//
// private val log = LoggerFactory.getLogger("BehandlingStatusOgResultatMapperForStans")
//
// internal fun StansAvYtelseRevurdering.mapToBehandlingStatusOgResultat(): no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat {
//     return when (this) {
//         is StansAvYtelseRevurdering.SimulertStansAvYtelse -> no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat(
//             resultat = no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat.Resultat(
//                 resultat = no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat.Stans,
//                 begrunnelse = this.revurderingsårsak.årsak.hentStansBegrunnelseEllerNull(),
//             ),
//             status = no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat.Status(
//                 status = no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus.SIMULERT,
//             ),
//         )
//
//         is StansAvYtelseRevurdering.AvsluttetStansAvYtelse -> no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat(
//             resultat = no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat.Resultat(
//                 resultat = no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat.Avbrutt,
//                 begrunnelse = this.revurderingsårsak.årsak.hentStansBegrunnelseEllerNull(),
//             ),
//             status = no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat.Status(
//                 status = no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus.AVSLUTTET,
//             ),
//         )
//
//         is StansAvYtelseRevurdering.IverksattStansAvYtelse -> no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat(
//             resultat = no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat.Resultat(
//                 resultat = no.nav.su.se.bakover.statistikk.behandling.BehandlingResultat.Stans,
//                 begrunnelse = this.revurderingsårsak.årsak.hentStansBegrunnelseEllerNull(),
//             ),
//             status = no.nav.su.se.bakover.statistikk.behandling.BehandlingStatusOgResultat.Status(
//                 status = no.nav.su.se.bakover.statistikk.behandling.BehandlingStatus.IVERKSATT,
//             ),
//         )
//     }
// }
//
// private fun Revurderingsårsak.Årsak.hentStansBegrunnelseEllerNull(): String? {
//     return when (this) {
//         Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING -> "Manglende kontrollerklæring"
//         else -> null.also {
//             log.error("Feil ved behandlingstatistikkmapping av gyldig årsak for Stans. $this er ikke en gyldig årsak for stans av ytelse")
//         }
//     }
// }
