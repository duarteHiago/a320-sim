package br.a320sim.cockpit.mcdu;

import br.a320sim.cockpit.crud.Campo;
import br.a320sim.cockpit.crud.FormModel;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.core.bus.DataBus;
import br.a320sim.host.plan.FlightPlan;
import com.badlogic.gdx.graphics.Color;

/**
 * O MCDU (F1 na cabine): o aluno preenche origem, destino, cruzeiro, pesos e V1/VR/V2 antes de decolar.
 * Desenhado como a tela do MCDU: fundo preto, rótulos brancos, valores em ciano, campos vazios
 * obrigatórios como caixinhas âmbar e erros em vermelho. ENTER insere (publica o plano no barramento).
 */
public final class McduPanel {

    private static final Color BEZEL = Color.valueOf("2a2f38");
    private static final Color SCREEN = Color.valueOf("05070a");
    private static final Color WHITE = Color.valueOf("e8ecf2");
    private static final Color CYAN = Color.valueOf("38e8ff");
    private static final Color GREEN = Color.valueOf("38ff6a");
    private static final Color AMBER = Color.valueOf("ffb020");
    private static final Color RED = Color.valueOf("ff3a30");
    private static final Color GREY = Color.valueOf("8a909a");

    private static final float X = 150;
    private static final float Y = 52;
    private static final float W = 340;
    private static final float H = 290;
    private static final float ROW = 21;

    private FormModel form;
    private String status;
    private boolean statusOk;
    private float time;

    public boolean isOpen() {
        return form != null;
    }

    /** Abre com o plano já inserido ou os valores iniciais do barramento. */
    public void open(DataBus bus) {
        form = McduForm.form(bus);
        status = FlightPlan.fromBus(bus).isPresent() ? "PLANO INSERIDO" : null;
        statusOk = true;
    }

    public void close() {
        form = null;
    }

    public FormModel form() {
        return form;
    }

    /**
     * ENTER: valida e, se estiver tudo certo, publica o plano.
     *
     * @return true se inseriu
     */
    public boolean insert(DataBus bus) {
        FlightPlan[] inserted = new FlightPlan[1];
        if (form.salvar(values -> inserted[0] = McduForm.toPlan(values))) {
            inserted[0].publish(bus);
            status = "PLANO INSERIDO";
            statusOk = true;
            return true;
        }
        status = "CONFIRA OS CAMPOS EM VERMELHO";
        statusOk = false;
        return false;
    }

    public void draw(Draw d, float delta) {
        time += delta;
        d.rect(X - 6, Y - 6, W + 12, H + 12, BEZEL);
        d.rect(X, Y, W, H, SCREEN);
        d.frame(X, Y, W, H, GREY);
        d.text(form.titulo(), X + W / 2, Y + H - 12, WHITE, Align.CENTER);
        d.text("PLANO DE VOO", X + W / 2, Y + H - 24, GREY, Align.CENTER);

        float y = Y + H - 44;
        for (Campo campo : form.campos()) {
            boolean active = campo == form.atual();
            boolean editable = form.editavel(campo);
            String value = form.valor(campo.chave());
            d.text(campo.rotulo(), X + 10, y, active ? WHITE : GREY, Align.LEFT);
            float vx = X + 168;
            if (active) {
                d.rect(vx - 4, y - 7, W - 172, 14, new Color(0.1f, 0.25f, 0.3f, 1));
            }
            String shown;
            Color color;
            if (!editable) {
                shown = value;
                color = GREEN;
            } else if (campo.tipo() == Campo.Tipo.OPCAO) {
                shown = "< " + value + " >";
                color = CYAN;
            } else if (value.isEmpty() && !active) {
                shown = "[" + " ".repeat(Math.max(1, campo.max() - 1)) + "]"; // obrigatório: caixinhas âmbar
                color = AMBER;
            } else {
                shown = value + (active && (time % 0.8f) < 0.5f ? "_" : "");
                color = CYAN;
            }
            d.text(shown, vx, y, color, Align.LEFT);
            String error = form.erro(campo.chave());
            if (error != null) {
                d.text(error, X + W - 8, y - 9, RED, Align.RIGHT);
            }
            y -= ROW;
        }
        if (status != null) {
            d.text(status, X + W / 2, Y + 24, statusOk ? GREEN : RED, Align.CENTER);
        }
        d.text("SETAS: campo  ESQ/DIR: opcao  ENTER: inserir  F1: fechar", X + W / 2, Y + 9, GREY, Align.CENTER);
    }
}
