package br.a320sim.host;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.core.cartridge.Cartridge;
import br.a320sim.core.cartridge.CartridgeGroup;
import br.a320sim.core.cartridge.CartridgeManifest;
import br.a320sim.core.module.AvionicsModule;
import br.a320sim.core.module.SelfTestResult;
import br.a320sim.host.cartridge.CartridgeLibrary;
import br.a320sim.host.cartridge.CartridgeLoader;
import br.a320sim.host.cartridge.ClasspathModuleFactory;
import br.a320sim.host.cartridge.DevFolderReader;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

/** Console do instrutor (Fase 0). Na Fase 5 vira a tela gráfica do IOS. */
public class HostMain {

    private final Host host = new Host();
    private final CartridgeLibrary library = new CartridgeLibrary(
            new DevFolderReader(Path.of("sd-dev")),
            new CartridgeLoader(new ClasspathModuleFactory()));

    public static void main(String[] args) {
        new HostMain().run();
    }

    private void run() {
        banner();
        help();
        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.print("\nIOS> ");
            if (!in.hasNextLine()) {
                return;
            }
            String line = in.nextLine().replace("﻿", "").trim();
            String[] parts = line.split("\\s+", 2);
            String arg = parts.length > 1 ? parts[1].trim() : "";
            switch (parts[0].toLowerCase(Locale.ROOT)) {
                case "gaveta" -> drawer();
                case "rack" -> rack();
                case "inserir" -> insert(arg);
                case "remover" -> remove(arg);
                case "ligar" -> powerOn();
                case "desligar" -> powerOff();
                case "manete" -> thrust(arg);
                case "rodar" -> advance(arg);
                case "status" -> status();
                case "ajuda" -> help();
                case "sair" -> {
                    System.out.println("Até o próximo voo!");
                    return;
                }
                case "" -> {
                }
                default -> System.out.println("Comando desconhecido. Digite 'ajuda'.");
            }
        }
    }

    private void banner() {
        System.out.println("""

                 ===========================================
                   A 3 2 0   S I M   -   H O S T   v0.1
                 ===========================================
                         __|__
                  --------(_)--------      INSIRA OS
                    O  O       O  O        CARTUCHOS
                """);
    }

    private void help() {
        System.out.println("""
                Comandos:
                  gaveta             lista os cartuchos disponíveis
                  rack               mostra os slots
                  inserir <PN>       encaixa um cartucho (ex.: inserir PLT-A320-CEO)
                  remover <GRUPO>    tira um cartucho (PLATAFORMA, PROPULSAO, COCKPIT)
                  ligar / desligar   liga ou desliga o avião
                  manete <0-100>     posição da manete de potência
                  rodar <segundos>   avança a simulação
                  status             mostra o barramento
                  sair""");
    }

    private void drawer() {
        System.out.println("Gaveta de cartuchos:");
        for (CartridgeManifest m : library.available()) {
            boolean inRack = host.rack().slot(m.grupo())
                    .map(c -> c.manifest().partNumber().equals(m.partNumber()))
                    .orElse(false);
            System.out.printf("  %-14s %-11s %-4s %s%s%n", m.partNumber(), m.grupo(), m.versao(), m.nome(),
                    inRack ? "   [no rack]" : "");
        }
    }

    private void rack() {
        System.out.println("+------------+------------------------------------------+");
        for (CartridgeGroup group : CartridgeGroup.values()) {
            String content = host.rack().slot(group).map(Cartridge::toString).orElse("--- vazio ---");
            System.out.printf("| %-10s | %-40s |%n", group, content);
        }
        System.out.println("+------------+------------------------------------------+");
        var report = host.rack().validate();
        if (report.ok()) {
            System.out.println("Configuração válida. Pronto para ligar.");
        } else {
            report.problems().forEach(p -> System.out.println("  X " + p));
        }
    }

    private void insert(String partNumber) {
        if (host.isPowered()) {
            System.out.println("Desligue o avião antes de trocar cartuchos no solo.");
            return;
        }
        Optional<CartridgeManifest> manifest = library.find(partNumber);
        if (manifest.isEmpty()) {
            System.out.println("Cartucho '" + partNumber + "' não está na gaveta.");
            return;
        }
        Cartridge cartridge = library.load(manifest.get());
        Optional<Cartridge> conflict = host.rack().insert(cartridge);
        if (conflict.isPresent()) {
            System.out.println("CONFLITO: o slot " + cartridge.group() + " já tem " + conflict.get()
                    + ". Remova antes.");
            return;
        }
        loadingBar(cartridge.manifest().partNumber());
    }

    private void remove(String groupName) {
        CartridgeGroup group;
        try {
            group = CartridgeGroup.valueOf(groupName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            System.out.println("Grupo inválido. Use PLATAFORMA, PROPULSAO ou COCKPIT.");
            return;
        }
        Optional<Cartridge> removed = host.rack().remove(group);
        if (removed.isEmpty()) {
            System.out.println("Slot " + group + " já está vazio.");
            return;
        }
        if (host.isPowered()) {
            removed.get().modules().forEach(AvionicsModule::powerDown);
            System.out.println("!! Cartucho arrancado em operação: "
                    + removed.get().modules().stream().map(m -> m.info().friendlyName()).toList()
                    + " PERDERAM O SOFTWARE");
        } else {
            System.out.println("Removido: " + removed.get());
        }
    }

    private void powerOn() {
        if (host.isPowered()) {
            System.out.println("O avião já está ligado.");
            return;
        }
        Host.PowerOnResult result = host.powerOn();
        if (!result.report().ok()) {
            System.out.println("RACK INCOMPLETO / INCOMPATÍVEL:");
            result.report().problems().forEach(p -> System.out.println("  X " + p));
            return;
        }
        System.out.println("\n*** POWER UP ***  self-test das caixas:");
        for (Map.Entry<AvionicsModule, SelfTestResult> e : result.selfTests().entrySet()) {
            pause(150);
            var info = e.getKey().info();
            System.out.printf("  %-18s %-24s ..... %s%n", info.friendlyName(), info.variant(),
                    e.getValue().passed() ? "OK" : "FALHA (" + e.getValue().message() + ")");
        }
        System.out.println("Todas as caixas em operação. Use 'rodar <segundos>'.");
    }

    private void powerOff() {
        host.powerOff();
        System.out.println("Avião desligado.");
    }

    private void thrust(String value) {
        try {
            double percent = Math.max(0, Math.min(100, Double.parseDouble(value)));
            host.bus().publish(Labels.SIM_THRUST_LEVER, percent / 100.0);
            System.out.printf("Manete em %.0f%%%n", percent);
        } catch (NumberFormatException e) {
            System.out.println("Use: manete <0-100>");
        }
    }

    private void advance(String value) {
        if (!host.isPowered()) {
            System.out.println("Ligue o avião primeiro.");
            return;
        }
        double seconds;
        try {
            seconds = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            System.out.println("Use: rodar <segundos>");
            return;
        }
        double step = Math.max(Host.TICK_SECONDS, seconds / 5);
        for (double done = 0; done < seconds - 1e-9; done += step) {
            host.advance(Math.min(step, seconds - done));
            statusLine();
            pause(200);
        }
    }

    private void statusLine() {
        DataBus bus = host.bus();
        System.out.printf("  t=%6.1fs  IR %-8s %s  LAW %-6s  N1 %5.1f%%  IAS %3.0f  ALT %5.0f  %s%n",
                host.simTime(),
                bus.getString(Labels.IR_ALIGN_STATUS, "---"),
                bar(bus.getDouble(Labels.IR_ALIGN_PROGRESS, 0), 10),
                bus.getString(Labels.FCTL_LAW, "---"),
                bus.getDouble(Labels.ENG1_N1, 0),
                bus.getDouble(Labels.ADR_IAS, 0),
                bus.getDouble(Labels.ADR_ALT, 0),
                alertText(bus));
    }

    private String alertText(DataBus bus) {
        List<String> warnings = bus.getList(Labels.FWC_WARNINGS);
        List<String> cautions = bus.getList(Labels.FWC_CAUTIONS);
        return (warnings.isEmpty() ? "" : "[WARNING] " + String.join(" | ", warnings) + " ")
                + (cautions.isEmpty() ? "" : "[CAUTION] " + String.join(" | ", cautions));
    }

    private void status() {
        DataBus bus = host.bus();
        List<String> labels = List.of(Labels.SIM_TIME, Labels.IR_ALIGN_STATUS, Labels.IR_ALIGN_PROGRESS,
                Labels.ADR_IAS, Labels.ADR_ALT, Labels.IR_HDG, Labels.FCTL_LAW, Labels.ENG_TYPE,
                Labels.ENG_PRIMARY_PARAM, Labels.ENG1_N1, Labels.ENG2_N1, Labels.AP_MODES, Labels.AP_ENGAGED,
                Labels.DMC_STYLE, Labels.FWC_WARNINGS, Labels.FWC_CAUTIONS, Labels.SIM_THRUST_LEVER);
        System.out.println("Barramento:");
        for (String label : labels) {
            System.out.printf("  %-20s %s%n", label, bus.has(label) ? bus.getString(label, "") : "--");
        }
    }

    private void loadingBar(String partNumber) {
        System.out.print("DATA LOADING  P/N " + partNumber + "  ");
        for (int i = 0; i <= 10; i++) {
            System.out.print("\rDATA LOADING  P/N " + partNumber + "  " + bar(i / 10.0, 10) + " " + i * 10 + "%");
            pause(60);
        }
        System.out.println("  OK");
    }

    private static String bar(double fraction, int width) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * width);
        return "[" + "#".repeat(filled) + ".".repeat(width - filled) + "]";
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
