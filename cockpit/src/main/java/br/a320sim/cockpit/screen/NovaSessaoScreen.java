package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.crud.PosicoesAdapter;
import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.cockpit.input.MenuInput;
import br.a320sim.cockpit.session.SessionPlan;
import br.a320sim.dados.Dados;
import br.a320sim.dados.DadosException;
import br.a320sim.dados.modelo.Perfil;
import br.a320sim.dados.modelo.Pessoa;
import br.a320sim.dados.modelo.Posicao;
import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entre o rack e o boot: quem voa (aluno), quem acompanha (instrutor, só no modo INSTRUTOR) e onde o
 * avião começa. Cima/baixo escolhe a linha, esquerda/direita troca a opção, ENTER liga o avião.
 */
public class NovaSessaoScreen extends RetroScreen {

    private static final Color YELLOW = Color.valueOf("ffff48");
    private static final Color GREY = Color.valueOf("aab0ba");
    private static final Color DIM = Color.valueOf("6c7380");
    private static final Color RED = Color.valueOf("ff3a30");
    private static final Color AMBER = Color.valueOf("ffb020");
    private static final Color GREEN = Color.valueOf("38ff6a");
    private static final Color PANEL = Color.valueOf("15181d");
    private static final Color EDGE = Color.valueOf("38e8ff");

    /** Uma linha de escolha: rótulo, opções e qual está escolhida. */
    private static final class Row<T> {
        final String label;
        final List<T> options;
        int index;

        Row(String label, List<T> options, T preferred) {
            this.label = label;
            this.options = options;
            index = preferred == null ? 0 : Math.max(0, options.indexOf(preferred));
        }

        T value() {
            return options.isEmpty() ? null : options.get(index);
        }

        void cycle(int direction) {
            if (!options.isEmpty()) {
                index = Math.floorMod(index + direction, options.size());
            }
        }
    }

    private final boolean hasDatabase;
    private final boolean instructorMode;
    private final Row<Pessoa> student;
    private final Row<Pessoa> instructor;
    private final Row<Posicao> position;
    private final List<Row<?>> rows = new ArrayList<>();
    private int selected;
    private float time;
    private boolean autostart = System.getProperty("sim.autostart") != null
            && !"NovaSessaoScreen".equals(System.getProperty("sim.shotScreen"));

    public NovaSessaoScreen(SimApp app) {
        super(app);
        instructorMode = app.mode() == SimApp.Mode.INSTRUCTOR;
        SessionPlan last = app.lastPlan();
        List<Pessoa> students = List.of();
        List<Pessoa> instructors = new ArrayList<>();
        List<Posicao> positions = List.of();
        Dados dados = app.banco().abrir().orElse(null);
        boolean ok = dados != null;
        if (ok) {
            try {
                students = dados.pessoas().ativos(Perfil.ALUNO);
                instructors.addAll(dados.pessoas().ativos(Perfil.INSTRUTOR));
                positions = dados.posicoes().listar();
            } catch (DadosException e) {
                System.err.println("[SESSAO] " + e.getMessage());
                ok = false;
            }
        }
        hasDatabase = ok;
        instructors.add(null); // "(nenhum)": o instrutor pode não querer se identificar
        student = new Row<>("ALUNO", students, byId(students, last.aluno()));
        instructor = new Row<>("INSTRUTOR", instructors, byId(instructors, last.instrutor()));
        position = new Row<>("POSICAO", positions, preferredPosition(positions, last.posicao()));
        rows.add(student);
        if (instructorMode) {
            rows.add(instructor);
        }
        rows.add(position);
    }

    /** A da sessão anterior; senão a pedida em -Dsim.posicao=NOME (testes); senão a primeira. */
    private static Posicao preferredPosition(List<Posicao> positions, Posicao last) {
        String wanted = System.getProperty("sim.posicao", "");
        for (Posicao p : positions) {
            if (last != null && Objects.equals(p.id(), last.id())) {
                return p;
            }
        }
        return positions.stream().filter(p -> p.nome().equalsIgnoreCase(wanted)).findFirst().orElse(null);
    }

    private static Pessoa byId(List<Pessoa> people, Pessoa wanted) {
        return wanted == null ? null
                : people.stream().filter(p -> p != null && p.id().equals(wanted.id())).findFirst().orElse(null);
    }

    @Override
    protected void update(float delta) {
        time += delta;
        if (autostart) {
            autostart = false;
            start();
            return;
        }
        if (MenuInput.back()) {
            app.sound().play(Sfx.BACK);
            app.showRack();
            return;
        }
        if (MenuInput.up()) {
            selected = (selected + rows.size() - 1) % rows.size();
            app.sound().play(Sfx.MOVE);
        }
        if (MenuInput.down()) {
            selected = (selected + 1) % rows.size();
            app.sound().play(Sfx.MOVE);
        }
        if (MenuInput.left() || MenuInput.right()) {
            rows.get(selected).cycle(MenuInput.left() ? -1 : 1);
            app.sound().play(Sfx.INSERT);
        }
        if (MenuInput.confirm()) {
            start();
        }
    }

    private void start() {
        app.sound().play(Sfx.SELECT);
        app.startBoot(new SessionPlan(student.value(), instructorMode ? instructor.value() : null, position.value()));
    }

    @Override
    protected void paint(Draw d) {
        d.text("NOVA SESSAO", WIDTH / 2f, 318, YELLOW, Align.CENTER, 2);
        d.text(instructorMode ? "modo INSTRUTOR: o aluno voa no outro PC" : "modo SOLO: o aluno voa neste PC",
                WIDTH / 2f, 296, GREY, Align.CENTER);

        float panelX = 100, panelW = 440, rowH = 48;
        float panelH = rows.size() * rowH + 24;
        float panelY = 250 - panelH;
        d.rect(panelX, panelY, panelW, panelH, PANEL);
        d.frame(panelX, panelY, panelW, panelH, EDGE);
        for (int i = 0; i < rows.size(); i++) {
            Row<?> row = rows.get(i);
            float y = panelY + panelH - 26 - i * rowH;
            boolean active = i == selected;
            if (active) {
                d.rect(panelX + 6, y - 22, panelW - 12, 40, new Color(1, 1, 1, 0.08f));
                if ((time % 0.8f) < 0.55f) {
                    d.text(">", panelX + 18, y + 2, YELLOW, Align.CENTER, 2);
                }
            }
            d.text(row.label, panelX + 34, y + 2, active ? Color.WHITE : GREY, Align.LEFT);
            String value = describe(row);
            boolean many = row.options.size() > 1;
            d.text((many ? "< " : "") + value + (many ? " >" : ""), panelX + 130, y + 2,
                    row.value() == null ? DIM : active ? GREEN : Color.WHITE, Align.LEFT);
            d.text(detail(row), panelX + 130, y - 13, DIM, Align.LEFT);
        }

        float note = panelY - 22;
        if (!hasDatabase) {
            d.text("SEM BANCO: a sessao nao sera gravada", WIDTH / 2f, note, RED, Align.CENTER);
            d.text("o aviao comeca na cabeceira", WIDTH / 2f, note - 14, GREY, Align.CENTER);
        } else if (student.value() == null) {
            d.text("NENHUM ALUNO ATIVO: a sessao nao sera gravada", WIDTH / 2f, note, AMBER, Align.CENTER);
            d.text("cadastre em 4 CADASTROS > PESSOAS", WIDTH / 2f, note - 14, GREY, Align.CENTER);
        } else {
            d.text("a sessao sera gravada no historico de " + student.value().nome(), WIDTH / 2f, note, DIM,
                    Align.CENTER);
        }
        d.text("SETAS: escolher   ENTER: ligar o aviao   ESC: voltar ao rack", WIDTH / 2f, 12, Color.WHITE,
                Align.CENTER);
    }

    private String describe(Row<?> row) {
        Object value = row.value();
        if (value instanceof Pessoa p) {
            return p.nome() + " (" + p.matricula() + ")";
        }
        if (value instanceof Posicao p) {
            return p.nome();
        }
        if (row == position) {
            return "PISTA (padrao)";
        }
        return row == student ? "(nenhum aluno)" : "(nenhum)";
    }

    private String detail(Row<?> row) {
        if (row.value() instanceof Posicao p) {
            return p.noChao() ? "na cabeceira da pista " + p.pista() + " de " + p.destino()
                    : p.altitudeFt() + " ft  " + p.velocidadeKt() + " kt  proa " + String.format("%03d", p.proaGraus())
                    + "  a " + PosicoesAdapter.formatar(p.distanciaNm()) + " NM de " + p.destino() + " " + p.pista();
        }
        if (row.options.size() > 1) {
            return (row.index + 1) + " de " + row.options.size();
        }
        return "";
    }

    @Override
    protected boolean scanlines() {
        return true;
    }
}
