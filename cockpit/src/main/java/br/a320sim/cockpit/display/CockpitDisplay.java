package br.a320sim.cockpit.display;

import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Theme;
import br.a320sim.core.bus.DataBus;

/** Uma das telas quadradas do painel (PFD, ND, E/WD). Desenha só o que lê do barramento. */
public interface CockpitDisplay {

    default void update(DataBus bus, double dt) {
    }

    void draw(Draw d, DataBus bus, Theme t, float x, float y, float size);
}
