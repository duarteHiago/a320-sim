package br.a320sim.host.cartridge;

import br.a320sim.core.module.AvionicsModule;

/** Cria uma caixa a partir do nome de classe listado no manifesto. */
public interface ModuleFactory {

    AvionicsModule create(String className);
}
