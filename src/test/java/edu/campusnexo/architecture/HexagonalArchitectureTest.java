package edu.campusnexo.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Golden rule of the hexagonal architecture, enforced on every build. */
@AnalyzeClasses(packages = "edu.campusnexo", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

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
}
