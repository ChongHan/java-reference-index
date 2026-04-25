package io.github.hanc.javareferenceindex;

import static io.github.hanc.javareferenceindex.ReferenceIndexingFixtures.fixtureSourceRoot;
import static io.github.hanc.javareferenceindex.ReferenceIndexingFixtures.request;
import static io.github.hanc.javareferenceindex.ReferenceIndexingFixtures.singleFile;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.hanc.javareferenceindex.api.JavaReferenceIndexer;
import io.github.hanc.javareferenceindex.api.JavaReferenceIndexers;
import io.github.hanc.javareferenceindex.model.SourceReference;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceReferenceShapeTest {
    private final JavaReferenceIndexer indexer = JavaReferenceIndexers.jdt();

    @Test
    void index_withConstructorReference_resolvesConstructedType() {
        assertReferencesSourceFile("source-reference-shape-constructor-reference", "UsesConstructor.java", "ConstructorTarget.java", "example.ConstructorTarget");
    }

    @Test
    void index_withMethodInvocation_resolvesMethodOwnerType() {
        assertReferencesSourceFile("source-reference-shape-method-invocation-reference", "UsesMethodInvocation.java", "MethodTarget.java", "example.MethodTarget");
    }

    @Test
    void index_withStaticMethodInvocation_resolvesMethodOwnerType() {
        assertReferencesSourceFile("source-reference-shape-static-method-reference", "UsesStaticMethod.java", "StaticMethodTarget.java", "example.StaticMethodTarget");
    }

    @Test
    void index_withStaticImport_resolvesImportedMethodOwnerType() {
        assertReferencesSourceFile("source-reference-shape-static-import-reference", "UsesStaticImport.java", "StaticImportTarget.java", "example.StaticImportTarget");
    }

    @Test
    void index_withStaticFieldReference_resolvesFieldOwnerType() {
        assertReferencesSourceFile("source-reference-shape-field-reference", "UsesStaticField.java", "StaticFieldTarget.java", "example.StaticFieldTarget");
    }

    @Test
    void index_withAnnotationReference_resolvesAnnotationType() {
        assertReferencesSourceFile("source-reference-shape-annotation-reference", "UsesAnnotation.java", "AnnotationTarget.java", "example.AnnotationTarget");
    }

    @Test
    void index_withInheritanceReference_resolvesParentTypes() {
        Path sourceRoot = fixtureSourceRoot("source-reference-shape-inheritance-reference");
        Path sourceFile = sourceRoot.resolve("example/UsesInheritance.java");

        var references = singleFile(indexer.index(request(sourceRoot, List.of(sourceFile), List.of())));

        assertThat(references.sourceReferences())
            .containsExactlyInAnyOrder(
                sourceReference(sourceRoot, "BaseTarget.java", "example.BaseTarget"),
                sourceReference(sourceRoot, "InterfaceTarget.java", "example.InterfaceTarget")
            );
    }

    @Test
    void index_withExceptionReference_resolvesThrowsAndCatchTypes() {
        assertReferencesSourceFile("source-reference-shape-exception-reference", "UsesException.java", "ExceptionTarget.java", "example.ExceptionTarget");
    }

    @Test
    void index_withOrdinaryImport_resolvesImportedType() {
        assertReferencesSourceFile("source-reference-shape-ordinary-import", "UsesOrdinaryImport.java", "OrdinaryImportTarget.java", "example.OrdinaryImportTarget");
    }

    @Test
    void index_withFullyQualifiedTypeUsage_resolvesType() {
        assertReferencesSourceFile("source-reference-shape-fully-qualified-type", "UsesFullyQualifiedType.java", "FullyQualifiedTarget.java", "example.FullyQualifiedTarget");
    }

    @Test
    void index_withGenericTypeArgument_resolvesTypeArgument() {
        assertReferencesSourceFile("source-reference-shape-generic-type", "UsesGenericType.java", "GenericTarget.java", "example.GenericTarget");
    }

    @Test
    void index_withArrayType_resolvesComponentType() {
        assertReferencesSourceFile("source-reference-shape-array-type", "UsesArrayType.java", "ArrayTarget.java", "example.ArrayTarget");
    }

    @Test
    void index_withMethodSignature_resolvesReturnAndParameterTypes() {
        assertReferencesSourceFile("source-reference-shape-method-signature", "UsesMethodSignature.java", "SignatureTarget.java", "example.SignatureTarget");
    }

    @Test
    void index_withLocalVariableType_resolvesLocalType() {
        assertReferencesSourceFile("source-reference-shape-local-variable", "UsesLocalVariable.java", "LocalVariableTarget.java", "example.LocalVariableTarget");
    }

    @Test
    void index_withRecordComponent_resolvesComponentType() {
        assertReferencesSourceFile("source-reference-shape-record-component", "UsesRecordComponent.java", "RecordComponentTarget.java", "example.RecordComponentTarget");
    }

    @Test
    void index_withMethodReference_resolvesMethodOwnerType() {
        assertReferencesSourceFile("source-reference-shape-method-reference", "UsesMethodReference.java", "MethodReferenceTarget.java", "example.MethodReferenceTarget");
    }

    @Test
    void index_withConstructorReference_resolvesConstructorOwnerType() {
        assertReferencesSourceFile("source-reference-shape-constructor-method-reference", "UsesConstructorMethodReference.java", "ConstructorMethodReferenceTarget.java", "example.ConstructorMethodReferenceTarget");
    }

    @Test
    void index_withStaticFieldImport_resolvesImportedFieldOwnerType() {
        assertReferencesSourceFile("source-reference-shape-static-field-import", "UsesStaticFieldImport.java", "StaticFieldImportTarget.java", "example.StaticFieldImportTarget");
    }

    @Test
    void index_withWildcardImport_resolvesUsedType() {
        assertReferencesSourceFile("source-reference-shape-wildcard-import", "UsesWildcardImport.java", "WildcardImportTarget.java", "example.WildcardImportTarget");
    }

    @Test
    void index_withStaticWildcardImport_resolvesUsedMemberOwnerType() {
        assertReferencesSourceFile("source-reference-shape-static-wildcard-import", "UsesStaticWildcardImport.java", "StaticWildcardImportTarget.java", "example.StaticWildcardImportTarget");
    }

    @Test
    void index_withNestedClassReference_resolvesEnclosingSourceFile() {
        assertReferencesSourceFile("source-reference-shape-nested-class", "UsesNestedClass.java", "OuterTarget.java", "example.OuterTarget.InnerTarget");
    }

    @Test
    void index_withAnnotationMemberClassLiteral_resolvesAnnotationAndMemberValueTypes() {
        Path sourceRoot = fixtureSourceRoot("source-reference-shape-annotation-member-value");
        Path sourceFile = sourceRoot.resolve("example/UsesAnnotationMemberValue.java");

        var references = singleFile(indexer.index(request(sourceRoot, List.of(sourceFile), List.of())));

        assertThat(references.sourceReferences())
            .containsExactlyInAnyOrder(
                sourceReference(sourceRoot, "AnnotationMemberTarget.java", "example.AnnotationMemberTarget"),
                sourceReference(sourceRoot, "AnnotationMemberValueTarget.java", "example.AnnotationMemberValueTarget")
            );
    }

    @Test
    void index_withClassLiteral_resolvesLiteralType() {
        assertReferencesSourceFile("source-reference-shape-class-literal", "UsesClassLiteral.java", "ClassLiteralTarget.java", "example.ClassLiteralTarget");
    }

    @Test
    void index_withCastAndInstanceof_resolvesCheckedType() {
        assertReferencesSourceFile("source-reference-shape-cast-instanceof", "UsesCastAndInstanceof.java", "CastTarget.java", "example.CastTarget");
    }

    @Test
    void index_withMultiCatch_resolvesEachExceptionType() {
        Path sourceRoot = fixtureSourceRoot("source-reference-shape-multi-catch");
        Path sourceFile = sourceRoot.resolve("example/UsesMultiCatch.java");

        var references = singleFile(indexer.index(request(sourceRoot, List.of(sourceFile), List.of())));

        assertThat(references.sourceReferences())
            .containsExactlyInAnyOrder(
                sourceReference(sourceRoot, "FirstExceptionTarget.java", "example.FirstExceptionTarget"),
                sourceReference(sourceRoot, "SecondExceptionTarget.java", "example.SecondExceptionTarget")
            );
    }

    @Test
    void index_withUnresolvedType_reportsUnresolvedReference() {
        Path sourceRoot = fixtureSourceRoot("source-reference-shape-unresolved-type");
        Path sourceFile = sourceRoot.resolve("example/UsesUnresolvedType.java");

        var references = singleFile(indexer.index(request(sourceRoot, List.of(sourceFile), List.of())));

        assertThat(references.sourceReferences()).isEmpty();
        assertThat(references.binaryReferences()).isEmpty();
        assertThat(references.unresolvedReferences())
            .extracting("name")
            .contains("MissingType");
    }

    @Test
    void index_withAnonymousClass_resolvesConstructedType() {
        assertReferencesSourceFile("source-reference-shape-anonymous-class", "UsesAnonymousClass.java", "AnonymousTarget.java", "example.AnonymousTarget");
    }

    @Test
    void index_withLocalClass_resolvesLocalClassParentType() {
        assertReferencesSourceFile("source-reference-shape-local-class", "UsesLocalClass.java", "LocalClassTarget.java", "example.LocalClassTarget");
    }

    @Test
    void index_withEnumReferences_resolvesConstructorArgumentInterfaceAndConstantBodyTypes() {
        Path sourceRoot = fixtureSourceRoot("source-reference-shape-enum-references");
        Path sourceFile = sourceRoot.resolve("example/UsesEnumReferences.java");

        var references = singleFile(indexer.index(request(sourceRoot, List.of(sourceFile), List.of())));

        assertThat(references.sourceReferences())
            .containsExactlyInAnyOrder(
                sourceReference(sourceRoot, "EnumConstructorTarget.java", "example.EnumConstructorTarget"),
                sourceReference(sourceRoot, "EnumBodyTarget.java", "example.EnumBodyTarget"),
                sourceReference(sourceRoot, "EnumInterfaceTarget.java", "example.EnumInterfaceTarget")
            );
    }

    @Test
    void index_withSealedPermits_resolvesPermittedType() {
        assertReferencesSourceFile("source-reference-shape-sealed-permits", "UsesSealedPermits.java", "SealedPermitTarget.java", "example.SealedPermitTarget");
    }

    @Test
    void index_withTypeUseAnnotation_resolvesAnnotationType() {
        assertReferencesSourceFile("source-reference-shape-type-use-annotation", "UsesTypeUseAnnotation.java", "TypeUseAnnotationTarget.java", "example.TypeUseAnnotationTarget");
    }

    @Test
    void index_withAnnotationDefaultValue_resolvesDefaultClassLiteralType() {
        assertReferencesSourceFile("source-reference-shape-annotation-default-value", "UsesAnnotationDefaultValue.java", "AnnotationDefaultValueTarget.java", "example.AnnotationDefaultValueTarget");
    }

    private void assertReferencesSourceFile(
        String fixtureName,
        String sourceFileName,
        String targetFileName,
        String targetQualifiedName
    ) {
        Path sourceRoot = fixtureSourceRoot(fixtureName);
        Path sourceFile = sourceRoot.resolve("example/" + sourceFileName);

        var references = singleFile(indexer.index(request(sourceRoot, List.of(sourceFile), List.of())));

        assertThat(references.sourceReferences())
            .contains(sourceReference(sourceRoot, targetFileName, targetQualifiedName));
        assertThat(references.binaryReferences()).isEmpty();
        assertThat(references.unresolvedReferences()).isEmpty();
    }

    private static SourceReference sourceReference(Path sourceRoot, String sourceFileName, String qualifiedName) {
        return new SourceReference(qualifiedName, sourceRoot.resolve("example/" + sourceFileName).toAbsolutePath().normalize());
    }
}
