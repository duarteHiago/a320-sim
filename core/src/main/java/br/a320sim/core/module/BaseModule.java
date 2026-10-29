package br.a320sim.core.module;

import br.a320sim.core.bus.DataBus;

import java.util.List;

/**
 * Ciclo de vida comum a todas as caixas (Template Method):
 * powerUp → selfTest → tick... → powerDown.
 * As subclasses só preenchem os ganchos on*().
 */
public abstract class BaseModule implements AvionicsModule {

    private final ModuleInfo info;
    private ModuleState state = ModuleState.OFF;
    protected DataBus bus;

    protected BaseModule(ModuleInfo info) {
        this.info = info;
    }

    @Override
    public final ModuleInfo info() {
        return info;
    }

    @Override
    public final ModuleState state() {
        return state;
    }

    @Override
    public final void powerUp(DataBus bus) {
        this.bus = bus;
        state = ModuleState.SELF_TEST;
        onPowerUp();
    }

    @Override
    public final SelfTestResult selfTest() {
        SelfTestResult result = runSelfTest();
        state = result.passed() ? ModuleState.OPERATIONAL : ModuleState.FAILED;
        return result;
    }

    @Override
    public final void tick(double dt) {
        if (state == ModuleState.OPERATIONAL) {
            onTick(dt);
        }
    }

    @Override
    public final void powerDown() {
        onPowerDown();
        if (bus != null) {
            labelPrefixes().forEach(bus::clear);
        }
        state = ModuleState.OFF;
    }

    /** Prefixos dos labels que esta caixa publica; somem do barramento quando ela desliga. */
    protected List<String> labelPrefixes() {
        return List.of(info.code() + ".");
    }

    protected void onPowerUp() {
    }

    protected SelfTestResult runSelfTest() {
        return SelfTestResult.ok();
    }

    protected abstract void onTick(double dt);

    protected void onPowerDown() {
    }
}
