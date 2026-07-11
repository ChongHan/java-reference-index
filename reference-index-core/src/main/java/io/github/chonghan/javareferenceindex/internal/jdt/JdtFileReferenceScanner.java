package io.github.chonghan.javareferenceindex.internal.jdt;

import io.github.chonghan.javareferenceindex.internal.resolve.TypeReferenceResolver;
import io.github.chonghan.javareferenceindex.model.BinaryReference;
import io.github.chonghan.javareferenceindex.model.FileReferenceSet;
import io.github.chonghan.javareferenceindex.model.ProjectIndexingRequest;
import io.github.chonghan.javareferenceindex.model.SourceReference;
import io.github.chonghan.javareferenceindex.model.UnresolvedReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypeMethodReference;

final class JdtFileReferenceScanner implements FileReferenceScanner {
    private final CompilationUnitParser parser;
    private final TypeReferenceResolver referenceResolver;

    JdtFileReferenceScanner(CompilationUnitParser parser, TypeReferenceResolver referenceResolver) {
        this.parser = parser;
        this.referenceResolver = referenceResolver;
    }

    @Override
    public List<FileReferenceSet> scan(ProjectIndexingRequest request) {
        Map<Path, CompilationUnit> compilationUnits = parser.parse(request);
        return request.sourceFiles().stream()
            .map(sourceFile -> scan(sourceFile, compilationUnits.get(normalize(sourceFile)), request))
            .toList();
    }

    private FileReferenceSet scan(Path sourceFile, CompilationUnit compilationUnit, ProjectIndexingRequest request) {
        if (compilationUnit == null) {
            throw new IllegalStateException("JDT did not produce an AST for source file " + sourceFile);
        }
        ReferenceCollector collector = new ReferenceCollector(sourceFile, request, referenceResolver, packageName(compilationUnit));
        compilationUnit.accept(collector);
        return collector.toReferenceSet();
    }

    private static Path normalize(Path sourceFile) {
        return sourceFile.toAbsolutePath().normalize();
    }

    private static String packageName(CompilationUnit compilationUnit) {
        if (compilationUnit.getPackage() == null) {
            return "";
        }
        return compilationUnit.getPackage().getName().getFullyQualifiedName();
    }

    private static final class ReferenceCollector extends ASTVisitor {
        private final Path sourceFile;
        private final ProjectIndexingRequest request;
        private final TypeReferenceResolver referenceResolver;
        private final String packageName;
        private final Map<String, SourceReference> sourceReferences = new LinkedHashMap<>();
        private final Map<String, BinaryReference> binaryReferences = new LinkedHashMap<>();
        private final Map<String, UnresolvedReference> unresolvedReferences = new LinkedHashMap<>();
        private final Set<String> ignoredImportedTopLevelNames = new HashSet<>();
        private final Set<String> ignoredOnDemandImportNames = new HashSet<>();

        private ReferenceCollector(
            Path sourceFile,
            ProjectIndexingRequest request,
            TypeReferenceResolver referenceResolver,
            String packageName
        ) {
            this.sourceFile = sourceFile.toAbsolutePath().normalize();
            this.request = request;
            this.referenceResolver = referenceResolver;
            this.packageName = packageName;
        }

        @Override
        public boolean visit(SimpleType node) {
            recordType(node.resolveBinding(), node.getName().getFullyQualifiedName());
            return false;
        }

        @Override
        public boolean visit(QualifiedType node) {
            recordType(node.resolveBinding(), node.toString());
            return false;
        }

        @Override
        public boolean visit(NameQualifiedType node) {
            recordType(node.resolveBinding(), node.toString());
            return false;
        }

        @Override
        public boolean visit(ImportDeclaration node) {
            recordIgnoredImport(node);
            recordImport(node);
            return false;
        }

        @Override
        public boolean visit(ClassInstanceCreation node) {
            record(node.resolveConstructorBinding());
            return true;
        }

        @Override
        public boolean visit(CreationReference node) {
            record(node.resolveMethodBinding());
            return true;
        }

        @Override
        public boolean visit(MethodInvocation node) {
            record(node.resolveMethodBinding());
            return true;
        }

        @Override
        public boolean visit(SuperMethodInvocation node) {
            record(node.resolveMethodBinding());
            return true;
        }

        @Override
        public boolean visit(ExpressionMethodReference node) {
            record(node.resolveMethodBinding());
            return true;
        }

        @Override
        public boolean visit(SuperMethodReference node) {
            record(node.resolveMethodBinding());
            return true;
        }

        @Override
        public boolean visit(TypeMethodReference node) {
            record(node.resolveMethodBinding());
            return true;
        }

        @Override
        public boolean visit(FieldAccess node) {
            record(node.resolveFieldBinding());
            return true;
        }

        @Override
        public boolean visit(SuperFieldAccess node) {
            record(node.resolveFieldBinding());
            return true;
        }

        @Override
        public boolean visit(QualifiedName node) {
            record(node.resolveBinding());
            return true;
        }

        @Override
        public boolean visit(TypeLiteral node) {
            record(node.getType().resolveBinding());
            return true;
        }

        @Override
        public boolean visit(CastExpression node) {
            record(node.getType().resolveBinding());
            return true;
        }

        @Override
        public boolean visit(InstanceofExpression node) {
            record(node.getRightOperand().resolveBinding());
            return true;
        }

        @Override
        public boolean visit(MarkerAnnotation node) {
            record(node.resolveTypeBinding());
            return true;
        }

        @Override
        public boolean visit(NormalAnnotation node) {
            record(node.resolveTypeBinding());
            return true;
        }

        @Override
        public boolean visit(SingleMemberAnnotation node) {
            record(node.resolveTypeBinding());
            return true;
        }

        private void record(IBinding binding) {
            if (binding instanceof ITypeBinding typeBinding) {
                record(typeBinding);
            } else if (binding instanceof IMethodBinding methodBinding) {
                record(methodBinding);
            } else if (binding instanceof IVariableBinding variableBinding) {
                record(variableBinding);
            }
        }

        private void recordImport(ImportDeclaration node) {
            String importedName = node.getName().getFullyQualifiedName();
            if (!node.isOnDemand() && recordExactTypeReference(importedName)) {
                return;
            }
            if (node.isOnDemand() && !node.isStatic()) {
                return;
            }

            String qualifiedName = node.isStatic() && !node.isOnDemand() ? withoutLastSegment(importedName) : importedName;
            if (shouldIgnore(qualifiedName)) {
                return;
            }
            if (recordSourceReference(qualifiedName)) {
                return;
            }
            recordBinaryReference(qualifiedName);
        }

        private void recordType(ITypeBinding binding, String unresolvedName) {
            if (binding == null || binding.isRecovered()) {
                recordUnresolved(unresolvedName);
                return;
            }
            record(binding);
        }

        private void record(IMethodBinding binding) {
            if (binding != null) {
                record(binding.getMethodDeclaration().getDeclaringClass());
            }
        }

        private void record(IVariableBinding binding) {
            if (binding != null) {
                record(binding.getVariableDeclaration().getDeclaringClass());
            }
        }

        private void record(ITypeBinding binding) {
            if (binding == null) {
                return;
            }
            ITypeBinding declaration = binding.getTypeDeclaration();
            String qualifiedName = declaration.getQualifiedName();
            if (shouldIgnore(qualifiedName)) {
                return;
            }

            if (recordSourceReference(qualifiedName)) {
                return;
            }
            recordBinaryReference(qualifiedName);
        }

        private boolean recordExactTypeReference(String qualifiedName) {
            var sourceReference = referenceResolver.resolveExactSource(qualifiedName, sourceFile, request);
            if (sourceReference.isPresent()) {
                SourceReference reference = sourceReference.orElseThrow();
                sourceReferences.putIfAbsent(qualifiedName, reference);
                binaryReferences.remove(qualifiedName);
                return true;
            }
            var binaryReference = referenceResolver.resolveBinary(qualifiedName, request);
            binaryReference.ifPresent(reference -> binaryReferences.putIfAbsent(qualifiedName, reference));
            return binaryReference.isPresent();
        }

        private boolean recordSourceReference(String qualifiedName) {
            return recordSourceReference(qualifiedName, qualifiedName);
        }

        private boolean recordSourceReference(String qualifiedName, String recordedQualifiedName) {
            var reference = referenceResolver.resolveSource(qualifiedName, sourceFile, request);
            reference.ifPresent(sourceReference -> {
                sourceReferences.putIfAbsent(recordedQualifiedName, new SourceReference(
                    recordedQualifiedName,
                    sourceReference.sourceFile(),
                    sourceReference.targetProject(),
                    sourceReference.targetSourceSet()
                ));
                binaryReferences.remove(qualifiedName);
                binaryReferences.remove(recordedQualifiedName);
            });
            return reference.isPresent();
        }

        private void recordBinaryReference(String qualifiedName) {
            if (sourceReferences.containsKey(qualifiedName)) {
                return;
            }
            referenceResolver.resolveBinary(qualifiedName, request)
                .ifPresent(reference -> binaryReferences.putIfAbsent(qualifiedName, reference));
        }

        private void recordUnresolved(String name) {
            if (shouldIgnoreUnresolved(name)) {
                return;
            }
            if (recordRecoveredSourceReference(name)) {
                return;
            }
            unresolvedReferences.putIfAbsent(name, new UnresolvedReference(name));
        }

        private void recordIgnoredImport(ImportDeclaration node) {
            String importedName = node.getName().getFullyQualifiedName();
            if (shouldIgnore(importedName)) {
                if (node.isOnDemand()) {
                    ignoredOnDemandImportNames.add(importedName);
                    return;
                }
                ignoredImportedTopLevelNames.add(simpleName(importedName));
            }
        }

        private boolean shouldIgnoreUnresolved(String name) {
            if (shouldIgnore(name)) {
                return true;
            }
            int firstSegmentEnd = name.indexOf('.');
            String firstSegment = firstSegmentEnd < 0 ? name : name.substring(0, firstSegmentEnd);
            return ignoredImportedTopLevelNames.contains(firstSegment)
                || ignoredOnDemandImportNames.stream().anyMatch(importName -> hasLoadableTopLevelType(importName, firstSegment));
        }

        private boolean recordRecoveredSourceReference(String name) {
            if (packageName.isBlank()) {
                return recordSourceReference(name);
            }
            if (name.contains(".")) {
                return recordPackageQualifiedTopLevelReference(name)
                    || recordSourceReference(name);
            }
            return recordSourceReference(packageName + "." + name) || recordSourceReference(name);
        }

        private boolean recordPackageQualifiedTopLevelReference(String name) {
            if (!startsWithTypeName(name)) {
                return false;
            }
            return recordSourceReference(packageName + "." + name, packageName + "." + firstSegment(name));
        }

        private static boolean startsWithTypeName(String name) {
            return !name.isBlank() && Character.isUpperCase(name.charAt(0));
        }

        private static String firstSegment(String name) {
            int firstSeparator = name.indexOf('.');
            if (firstSeparator < 0) {
                return name;
            }
            return name.substring(0, firstSeparator);
        }

        private static boolean shouldIgnore(String qualifiedName) {
            return qualifiedName == null
                || qualifiedName.isBlank()
                || qualifiedName.startsWith("java.")
                || qualifiedName.startsWith("javax.")
                || qualifiedName.startsWith("jdk.")
                || qualifiedName.startsWith("sun.")
                || qualifiedName.startsWith("com.sun.");
        }

        private static String simpleName(String qualifiedName) {
            int lastSeparator = qualifiedName.lastIndexOf('.');
            if (lastSeparator < 0) {
                return qualifiedName;
            }
            return qualifiedName.substring(lastSeparator + 1);
        }

        private static String withoutLastSegment(String qualifiedName) {
            int lastSeparator = qualifiedName.lastIndexOf('.');
            if (lastSeparator < 0) {
                return qualifiedName;
            }
            return qualifiedName.substring(0, lastSeparator);
        }

        private static boolean hasLoadableTopLevelType(String packageName, String simpleName) {
            try {
                Class.forName(packageName + "." + simpleName, false, ClassLoader.getSystemClassLoader());
                return true;
            } catch (LinkageError | ClassNotFoundException e) {
                return false;
            }
        }

        private FileReferenceSet toReferenceSet() {
            return new FileReferenceSet(
                sourceFile,
                new ArrayList<>(sourceReferences.values()),
                new ArrayList<>(binaryReferences.values()),
                new ArrayList<>(unresolvedReferences.values())
            );
        }
    }
}
