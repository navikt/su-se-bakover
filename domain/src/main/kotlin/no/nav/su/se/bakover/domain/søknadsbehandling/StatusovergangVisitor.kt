package no.nav.su.se.bakover.domain.søknadsbehandling

interface StatusovergangVisitor : SøknadsbehandlingVisitor {
    override fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Uavklart) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Innvilget) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: VilkårsvurdertSøknadsbehandling.Avslag) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: BeregnetSøknadsbehandling.Innvilget) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: BeregnetSøknadsbehandling.Avslag) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: SimulertSøknadsbehandling) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Innvilget) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Avslag.MedBeregning) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: UnderkjentSøknadsbehandling.Avslag.UtenBeregning) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Avslag.UtenBeregning) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Avslag.MedBeregning) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: SøknadsbehandlingTilAttestering.Innvilget) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: IverksattSøknadsbehandling.Avslag.UtenBeregning) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: IverksattSøknadsbehandling.Avslag.MedBeregning) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: IverksattSøknadsbehandling.Innvilget) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    override fun visit(søknadsbehandling: LukketSøknadsbehandling) {
        throw UgyldigStatusovergangException(søknadsbehandling, this)
    }

    data class UgyldigStatusovergangException(
        private val søknadsbehandling: Any,
        private val statusovergang: Any,
        val msg: String = "Ugyldig statusovergang: ${statusovergang::class.qualifiedName} for type: ${søknadsbehandling::class.qualifiedName}",
    ) : RuntimeException(msg)
}
