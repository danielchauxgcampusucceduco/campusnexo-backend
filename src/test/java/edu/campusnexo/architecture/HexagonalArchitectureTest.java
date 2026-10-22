package edu.campusnexo.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.stream.Stream;

/**
 * Golden rule of the hexagonal architecture, enforced on every build. The first two rules are allow-lists:
 * the core may only use the JDK and its own inner rings, so a new library cannot slip in unnoticed (a
 * deny-list of known frameworks would let it). The rest block specific directions between adapters.
 */
@AnalyzeClasses(packages = "edu.campusnexo", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domainOnlyUsesTheJdk = classes().that().resideInAPackage("edu.campusnexo.domain..")
            .should().onlyDependOnClassesThat(jdkOr("edu.campusnexo.domain"))
            .because("el dominio es Java 21 puro: solo la JDK y el propio dominio");

    @ArchTest
    static final ArchRule applicationOnlyUsesTheJdkAndTheDomain = classes()
            .that().resideInAPackage("edu.campusnexo.application..")
            .should().onlyDependOnClassesThat(jdkOr("edu.campusnexo.domain", "edu.campusnexo.application"))
            .because("los casos de uso solo conocen la JDK, el dominio y sus puertos");

    /** Primitives, arrays of allowed types, {@code java.*} and the given packages. */
    private static DescribedPredicate<JavaClass> jdkOr(String... packages) {
        return DescribedPredicate.describe("la JDK o " + String.join(", ", packages), javaClass -> {
            JavaClass base = javaClass.isArray() ? javaClass.getBaseComponentType() : javaClass;
            if (base.isPrimitive()) {
                return true;
            }
            String name = base.getPackageName();
            return name.equals("java") || name.startsWith("java.")
                    || Stream.of(packages).anyMatch(allowed -> name.equals(allowed) || name.startsWith(allowed + "."));
        });
    }

    @ArchTest
    static final ArchRule domainIsPureJava = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..",
                    "com.fasterxml..", "tools.jackson..", "org.eclipse.paho..", "org.hibernate..")
            .because("el dominio debe ser Java 21 puro");

    @ArchTest
    static final ArchRule domainDoesNotKnowOuterRings = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
            .because("las dependencias apuntan hacia el dominio");

    @ArchTest
    static final ArchRule applicationIsFrameworkFree = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..",
                    "com.fasterxml..", "tools.jackson..", "org.eclipse.paho..", "..infrastructure..")
            .because("los casos de uso solo conocen puertos");

    @ArchTest
    static final ArchRule inboundAdaptersDoNotUseOutboundAdapters = noClasses()
            .that().resideInAPackage("..infrastructure.adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.adapter.out..")
            .because("los adaptadores de entrada hablan con el núcleo a través de los puertos de entrada");

    @ArchTest
    static final ArchRule outboundAdaptersDoNotUseInboundAdapters = noClasses()
            .that().resideInAPackage("..infrastructure.adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.adapter.in..")
            .because("los contratos compartidos viven en adapter.contract");

    @ArchTest
    static final ArchRule domainModulesAreFreeOfCycles = slices().matching("edu.campusnexo.domain.(*)..")
            .should().beFreeOfCycles()
            .because("cada módulo del dominio se entiende y se prueba sin arrastrar a los demás en círculo");

    @ArchTest
    static final ArchRule applicationModulesAreFreeOfCycles = slices().matching("edu.campusnexo.application.(*)..")
            .should().beFreeOfCycles()
            .because("los casos de uso se comunican por puertos, no en círculo");

    @ArchTest
    static final ArchRule inboundAdaptersOnlyUseInputPorts = noClasses()
            .that().resideInAPackage("..infrastructure.adapter.in..")
            .should().dependOnClassesThat(resideInAPackage("edu.campusnexo.application..")
                    .and(not(resideInAnyPackage("edu.campusnexo.application.port..",
                            "edu.campusnexo.application.exception.."))))
            .because("un adaptador de entrada conoce el contrato del caso de uso, nunca su implementación");

    @ArchTest
    static final ArchRule onlyTheTwinUseCasesWriteTheTwin = classes().that().haveSimpleName("TwinWriter")
            .should().onlyBeAccessed().byAnyPackage("edu.campusnexo.application.twin..",
                    "edu.campusnexo.infrastructure.config..")
            .because("el gemelo se escribe solo con lectura-decisión-comparación y en una transacción");

    @ArchTest
    static final ArchRule simulationOnlyUsesInputPorts = noClasses()
            .that().resideInAPackage("edu.campusnexo.application.simulation..")
            .should().dependOnClassesThat().resideInAnyPackage("edu.campusnexo.application.twin..",
                    "edu.campusnexo.domain.port..")
            .because("la demostración recorre el mismo camino que los dispositivos: solo los puertos de entrada");
}
