package no.nav.su.se.bakover.domain.søknadsbehandling
import behandling.søknadsbehandling.domain.avslag.ErAvslag

sealed interface ReturnerSøknadsbehandling : Søknadsbehandling

sealed interface Avslag :
    ReturnerSøknadsbehandling,
    ErAvslag,
    KanGenerereAvslagsbrev {

    override fun erÅpen() = true
    override fun erAvsluttet() = false
    override fun erAvbrutt() = false
}
