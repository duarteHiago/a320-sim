package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.crud.Campo;
import br.a320sim.cockpit.crud.CrudAdapter;
import br.a320sim.cockpit.crud.FormModel;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.cockpit.input.MenuInput;
import br.a320sim.dados.DadosException;
import br.a320sim.dados.servico.RegraException;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tela genérica de cadastro: lista à esquerda, detalhes (ou o formulário) à direita.
 * Serve para os 4 cadastros: o que muda de um para o outro fica no {@link CrudAdapter}.
 * <p>
 * Lista: N novo, ENTER editar, DEL excluir (pede confirmação), ESC voltar.
 * Formulário: setas cima/baixo trocam de campo, esquerda/direita mudam a opção, ENTER salva, ESC cancela.
 * As letras digitadas chegam pelo InputProcessor (W/S são letras, por isso não trocam de campo).
 */
public class CrudScreen<T> extends RetroScreen {

    private static final Color PANEL = Color.valueOf("15181d");
    private static final Color LINE = Color.valueOf("3a404a");
    private static final Color GREY = Color.valueOf("8a909a");
    private static final Color DIM = Color.valueOf("5a606a");
    private static final Color WHITE = Color.valueOf("e8ecf2");
    private static final Color GREEN = Color.valueOf("38ff6a");
    private static final Color RED = Color.valueOf("ff3a30");
    private static final Color YELLOW = Color.valueOf("ffff48");
    private static final Color CYAN = Color.valueOf("38e8ff");
    private static final Color SELECTED = new Color(1, 1, 1, 0.10f);

    private static final float TOP = 330;
    private static final float BOTTOM = 40;
    private static final float LIST_X = 6;
    private static final float LIST_W = 226;
    private static final float DET_X = 238;
    private static final float DET_W = 396;
    private static final float ROW = 13;
    private static final float FIELD_ROW = 28;
    private static final float VALUE_X = DET_X + 112;
    private static final float VALUE_W = DET_X + DET_W - 8 - VALUE_X;
    private static final int VALUE_CHARS = (int) ((VALUE_W - 8) / 6);
    private static final int LIST_ROWS = (int) ((TOP - 12 - BOTTOM) / ROW);
    private static final int LIST_CHARS = (int) ((LIST_W - 12) / 6);
    private static final int DETAIL_CHARS = (int) ((DET_W - 16) / 6);

    private final CrudAdapter<T> adapter;
    private final InputAdapter typing = new InputAdapter() {
        @Override
        public boolean keyTyped(char character) {
            if (form != null) {
                form.digitar(character);
            }
            return true;
        }
    };
    private List<T> items = List.of();
    private int selected;
    private int firstRow;
    private int detailScroll;
    private List<String> details = List.of();
    private FormModel form;
    /** Item em edição (null = novo). */
    private T editing;
    private T confirmingDelete;
    private String message;
    private Color messageColor = GREEN;
    private float time;

    public CrudScreen(SimApp app, CrudAdapter<T> adapter) {
        super(app);
        this.adapter = adapter;
        reload();
        applyStartupShortcut();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(typing);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        super.hide();
    }

    private void reload() {
        try {
            items = adapter.listar();
        } catch (DadosException e) {
            items = List.of();
            error(e.getMessage());
        }
        selected = Math.max(0, Math.min(selected, items.size() - 1));
        refreshDetails();
    }

    private void refreshDetails() {
        detailScroll = 0;
        T item = current();
        try {
            details = item == null ? List.of() : adapter.detalhes(item);
        } catch (DadosException e) {
            details = List.of();
            error(e.getMessage());
        }
    }

    private T current() {
        return items.isEmpty() ? null : items.get(selected);
    }

    @Override
    protected void update(float delta) {
        time += delta;
        if (form != null) {
            updateForm();
        } else if (confirmingDelete != null) {
            updateConfirm();
        } else {
            updateList();
        }
    }

    private void updateList() {
        if (MenuInput.back()) {
            app.sound().play(Sfx.BACK);
            app.showCadastros();
            return;
        }
        if (!items.isEmpty() && (MenuInput.up() || MenuInput.down())) {
            int next = Math.floorMod(selected + (MenuInput.up() ? -1 : 1), items.size());
            if (next != selected) {
                selected = next;
                app.sound().play(Sfx.MOVE);
                refreshDetails();
            }
        }
        if (Gdx.input.isKeyJustPressed(Keys.PAGE_DOWN)) {
            detailScroll = Math.min(detailScroll + 10, Math.max(0, details.size() - 10));
        }
        if (Gdx.input.isKeyJustPressed(Keys.PAGE_UP)) {
            detailScroll = Math.max(0, detailScroll - 10);
        }
        if (Gdx.input.isKeyJustPressed(Keys.N)) {
            if (adapter.podeCriar()) {
                openForm(null);
            } else {
                error("AS SESSOES SAO GRAVADAS PELO SIMULADOR");
            }
        } else if (MenuInput.confirm() && current() != null) {
            openForm(current());
        } else if ((Gdx.input.isKeyJustPressed(Keys.FORWARD_DEL) || Gdx.input.isKeyJustPressed(Keys.DEL))
                && current() != null) {
            confirmingDelete = current();
            app.sound().play(Sfx.MOVE);
        }
    }

    private void updateConfirm() {
        if (MenuInput.back()) {
            confirmingDelete = null;
            app.sound().play(Sfx.BACK);
        } else if (MenuInput.confirm() || Gdx.input.isKeyJustPressed(Keys.FORWARD_DEL)
                || Gdx.input.isKeyJustPressed(Keys.DEL)) {
            T item = confirmingDelete;
            confirmingDelete = null;
            try {
                ok(adapter.excluir(item));
                reload();
            } catch (RegraException | DadosException e) {
                error(e.getMessage().toUpperCase());
            }
        }
    }

    private void openForm(T item) {
        editing = item;
        form = new FormModel(item == null ? "NOVO" : "EDITAR", adapter.campos(),
                item == null ? adapter.valoresNovos() : adapter.valores(item));
        message = null;
        app.sound().play(Sfx.SELECT);
    }

    private void updateForm() {
        if (MenuInput.back()) {
            form = null;
            app.sound().play(Sfx.BACK);
            return;
        }
        if (Gdx.input.isKeyJustPressed(Keys.DOWN) || Gdx.input.isKeyJustPressed(Keys.TAB)) {
            form.proximo();
        }
        if (Gdx.input.isKeyJustPressed(Keys.UP)) {
            form.anterior();
        }
        if (Gdx.input.isKeyJustPressed(Keys.LEFT)) {
            form.opcaoAnterior();
        }
        if (Gdx.input.isKeyJustPressed(Keys.RIGHT)) {
            form.opcaoSeguinte();
        }
        if (Gdx.input.isKeyJustPressed(Keys.ENTER) || Gdx.input.isKeyJustPressed(Keys.NUMPAD_ENTER)) {
            save();
        }
    }

    private void save() {
        List<T> saved = new ArrayList<>(1);
        if (form.salvar(valores -> saved.add(adapter.salvar(editing, valores)))) {
            form = null;
            ok("SALVO");
            reload();
            int index = items.indexOf(saved.get(0));
            if (index >= 0) {
                selected = index; // a lista é ordenada: o item pode ter mudado de lugar
                refreshDetails();
            }
        } else {
            app.sound().play(Sfx.ERROR);
        }
    }

    private void ok(String text) {
        message = text;
        messageColor = GREEN;
        app.sound().play(Sfx.CHIME);
    }

    private void error(String text) {
        message = text;
        messageColor = RED;
        app.sound().play(Sfx.ERROR);
    }

    // ------------------------------------------------------------------ desenho

    @Override
    protected void paint(Draw d) {
        d.text("CADASTROS > " + adapter.titulo(), 6, 348, YELLOW, Align.LEFT);
        d.text(items.size() + (items.size() == 1 ? " ITEM" : " ITENS"), 634, 348, GREY, Align.RIGHT);
        paintList(d);
        d.rect(DET_X, BOTTOM, DET_W, TOP - BOTTOM, PANEL);
        d.frame(DET_X, BOTTOM, DET_W, TOP - BOTTOM, form != null ? GREEN : LINE);
        if (form != null) {
            paintForm(d);
        } else {
            paintDetails(d);
        }
        paintFooter(d);
    }

    private void paintList(Draw d) {
        d.rect(LIST_X, BOTTOM, LIST_W, TOP - BOTTOM, PANEL);
        d.frame(LIST_X, BOTTOM, LIST_W, TOP - BOTTOM, form == null ? CYAN : LINE);
        if (items.isEmpty()) {
            d.text("(vazio)", LIST_X + 8, TOP - 14, DIM, Align.LEFT);
            if (adapter.podeCriar()) {
                d.text("N para cadastrar", LIST_X + 8, TOP - 28, DIM, Align.LEFT);
            }
            return;
        }
        if (selected < firstRow) {
            firstRow = selected;
        } else if (selected >= firstRow + LIST_ROWS) {
            firstRow = selected - LIST_ROWS + 1;
        }
        float y = TOP - 12;
        for (int i = firstRow; i < Math.min(items.size(), firstRow + LIST_ROWS); i++) {
            T item = items.get(i);
            boolean active = i == selected;
            if (active) {
                d.rect(LIST_X + 2, y - 6, LIST_W - 4, ROW, SELECTED);
                d.text(">", LIST_X + 6, y, YELLOW, Align.LEFT);
            }
            Color color = adapter.apagado(item) ? DIM : active ? WHITE : GREY;
            d.text(cut(adapter.linha(item), LIST_CHARS - 2), LIST_X + 16, y, color, Align.LEFT);
            y -= ROW;
        }
        if (items.size() > LIST_ROWS) {
            d.text((selected + 1) + "/" + items.size(), LIST_X + LIST_W - 6, BOTTOM + 8, DIM, Align.RIGHT);
        }
    }

    private void paintDetails(Draw d) {
        if (details.isEmpty()) {
            return;
        }
        float y = TOP - 14;
        int rows = (int) ((TOP - BOTTOM - 20) / ROW);
        for (int i = detailScroll; i < Math.min(details.size(), detailScroll + rows); i++) {
            String line = details.get(i);
            int bar = line.indexOf('|');
            if (bar >= 0) {
                d.text(line.substring(0, bar), DET_X + 8, y, GREY, Align.LEFT);
                d.text(cut(line.substring(bar + 1), DETAIL_CHARS - 16), DET_X + 104, y, WHITE, Align.LEFT);
            } else {
                d.text(cut(line, DETAIL_CHARS), DET_X + 8, y, CYAN, Align.LEFT);
            }
            y -= ROW;
        }
        if (details.size() > detailScroll + rows) {
            d.text("PGDN: mais", DET_X + DET_W - 8, BOTTOM + 8, DIM, Align.RIGHT);
        } else if (detailScroll > 0) {
            d.text("PGUP: voltar", DET_X + DET_W - 8, BOTTOM + 8, DIM, Align.RIGHT);
        }
    }

    private void paintForm(Draw d) {
        d.text(form.titulo(), DET_X + 8, TOP - 12, GREEN, Align.LEFT);
        float y = TOP - 34;
        for (Campo campo : form.campos()) {
            boolean active = campo == form.atual();
            boolean editable = form.editavel(campo);
            String value = form.valor(campo.chave());
            d.text(campo.rotulo(), DET_X + 8, y, active ? WHITE : editable ? GREY : DIM, Align.LEFT);
            if (campo.tipo() == Campo.Tipo.LEITURA) {
                d.text(cut(value, VALUE_CHARS), VALUE_X + 4, y, DIM, Align.LEFT);
            } else if (!editable) {
                d.frame(VALUE_X, y - 7, VALUE_W, 14, LINE);
                d.text(value + "  (bloqueado: no chao)", VALUE_X + 4, y, DIM, Align.LEFT);
            } else {
                d.rect(VALUE_X, y - 7, VALUE_W, 14, active ? new Color(0.1f, 0.3f, 0.15f, 1) : Color.BLACK);
                d.frame(VALUE_X, y - 7, VALUE_W, 14, active ? GREEN : LINE);
                String shown = campo.tipo() == Campo.Tipo.OPCAO ? "< " + value + " >"
                        : tail(value, VALUE_CHARS - 1) + (active && (time % 0.8f) < 0.5f ? "_" : "");
                d.text(shown, VALUE_X + 4, y, active ? GREEN : WHITE, Align.LEFT);
            }
            String error = form.erro(campo.chave());
            if (error != null) {
                d.text(cut(error, VALUE_CHARS + 2), VALUE_X, y - 13, RED, Align.LEFT);
            }
            y -= FIELD_ROW;
        }
        if (form.erroGeral() != null) {
            d.text(cut(form.erroGeral().toUpperCase(), DETAIL_CHARS), DET_X + 8, BOTTOM + 10, RED, Align.LEFT);
        }
    }

    private void paintFooter(Draw d) {
        if (confirmingDelete != null) {
            d.rect(0, 20, WIDTH, 14, new Color(0.4f, 0.05f, 0.05f, 1));
            d.text(adapter.perguntaExclusao(confirmingDelete) + "   ENTER/DEL: sim   ESC: nao", WIDTH / 2f, 27,
                    YELLOW, Align.CENTER);
        } else if (message != null) {
            d.text(message, WIDTH / 2f, 27, messageColor, Align.CENTER);
        }
        if (confirmingDelete != null) {
            return;
        }
        String help = form != null
                ? "SETAS: campo   ESQ/DIR: opcao   ENTER: salvar   ESC: cancelar"
                : (adapter.podeCriar() ? "N: novo   " : "") + "ENTER: editar   DEL: excluir   ESC: voltar";
        d.text(help, WIDTH / 2f, 10, WHITE, Align.CENTER);
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 2)) + "..";
    }

    /** Campo de texto maior que a caixa: mostra o fim, onde se está digitando. */
    private static String tail(String text, int max) {
        return text.length() <= max ? text : text.substring(text.length() - max);
    }

    @Override
    protected boolean scanlines() {
        return true;
    }

    // ------------------------------------------------------------------ atalho de teste

    /**
     * Para screenshots sem teclado: {@code -Dsim.crud=posicoes,1,editar,velocidade=90,salvar}
     * escolhe o item 1, abre o formulário, troca um valor e tenta salvar (mostrando os erros).
     * Também aceita {@code novo} e {@code excluir} (abre a confirmação).
     */
    private void applyStartupShortcut() {
        String[] steps = System.getProperty("sim.crud", "").split(",");
        if (steps.length < 2) {
            return;
        }
        Map<String, String> changes = new HashMap<>();
        List<String> actions = new ArrayList<>();
        for (int i = 1; i < steps.length; i++) {
            String step = steps[i].strip();
            if (step.matches("\\d+")) {
                selected = Math.min(Integer.parseInt(step), Math.max(0, items.size() - 1));
                refreshDetails();
            } else if (step.contains("=")) {
                changes.put(step.substring(0, step.indexOf('=')), step.substring(step.indexOf('=') + 1));
            } else {
                actions.add(step);
            }
        }
        for (String action : actions) {
            switch (action) {
                case "editar" -> openForm(current());
                case "novo" -> openForm(null);
                case "excluir" -> confirmingDelete = current();
                case "salvar" -> {
                    changes.forEach(this::typeInto);
                    save();
                }
                default -> System.err.println("[CRUD] passo desconhecido: " + action);
            }
        }
        if (form != null && !actions.contains("salvar")) {
            changes.forEach(this::typeInto);
        }
    }

    /** Digita um valor num campo como se fosse pelo teclado. */
    private void typeInto(String key, String value) {
        if (form == null || !form.irPara(key)) {
            return;
        }
        if (form.atual().tipo() == Campo.Tipo.OPCAO) {
            for (int i = 0; i < form.atual().opcoes(form.valores()).size() && !form.valor(key).equals(value); i++) {
                form.opcaoSeguinte();
            }
        } else {
            for (int i = form.valor(key).length(); i > 0; i--) {
                form.digitar('\b');
            }
            value.chars().forEach(c -> form.digitar((char) c));
        }
    }
}
