package br.a320sim.core.module;

import br.a320sim.core.bus.DataBus;

/** Contrato de toda "caixa preta" do avião. Cada cartucho traz uma ou mais implementações. */
public interface AvionicsModule {

    ModuleInfo info();

    ModuleState state();

    void powerUp(DataBus bus);

    SelfTestResult selfTest();

    void tick(double dt);

    void powerDown();
}
