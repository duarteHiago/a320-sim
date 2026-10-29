package br.a320sim.host.cartridge;

import br.a320sim.core.module.AvionicsModule;

/**
 * Fase 0: as classes das caixas já estão no classpath do host.
 * Na Fase 3 entra uma JarModuleFactory que carrega o .jar copiado do SD.
 */
public class ClasspathModuleFactory implements ModuleFactory {

    @Override
    public AvionicsModule create(String className) {
        try {
            Class<?> type = Class.forName(className);
            if (!AvionicsModule.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException(className + " não é um AvionicsModule");
            }
            return (AvionicsModule) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Não consegui carregar " + className, e);
        }
    }
}
