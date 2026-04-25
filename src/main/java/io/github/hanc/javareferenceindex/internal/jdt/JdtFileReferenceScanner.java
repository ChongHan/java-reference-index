package io.github.hanc.javareferenceindex.internal.jdt;

import io.github.hanc.javareferenceindex.internal.resolve.TypeReferenceResolver;
import io.github.hanc.javareferenceindex.model.BinaryReference;
import io.github.hanc.javareferenceindex.model.FileReferenceSet;
import io.github.hanc.javareferenceindex.model.ProjectIndexingRequest;
import io.github.hanc.javareferenceindex.model.SourceReference;
import io.github.hanc.javareferenceindex.model.UnresolvedReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
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
    public FileReferenceSet scan(Path sourceFile, ProjectIndexingRequest request) {
        CompilationUnit compilationUnit = parser.parse(sourceFile, request);
        ReferenceCollector collector = new ReferenceCollector(sourceFile, request, referenceResolver);
        compilationUnit.accept(collector);
        return collector.toReferenceSet();
    }

    private static final class ReferenceCollector extends ASTVisitor {
        private final Path sourceFile;
        private final ProjectIndexingRequest request;
        private final TypeReferenceResolver referenceResolver;
        private final Map<String, SourceReference> sourceReferences = new LinkedHashMap<>();
        private final Map<String, BinaryReference> binaryReferences = new LinkedHashMap<>();
        private final Map<String, UnresolvedReference> unresolvedReferences = new LinkedHashMap<>();

        private ReferenceCollector(
            Path sourceFile,
            ProjectIndexingRequest request,
            TypeReferenceResolver referenceResolver
        ) {
            this.sourceFile = sourceFile.toAbsolutePath().normalize();
            this.request = request;
            this.referenceResolver = referenceResolver;
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
            record(node.resolveBinding());
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

        private boolean recordSourceReference(String qualifiedName) {
            return referenceResolver.resolveSource(qualifiedName, sourceFile, request)
                .map(reference -> sourceReferences.putIfAbsent(qualifiedName, reference))
                .isPresent();
        }

        private void recordBinaryReference(String qualifiedName) {
            referenceResolver.resolveBinary(qualifiedName, request)
                .ifPresent(reference -> binaryReferences.putIfAbsent(qualifiedName, reference));
        }

        private void recordUnresolved(String name) {
            unresolvedReferences.putIfAbsent(name, new UnresolvedReference(name));
        }

        private static boolean shouldIgnore(String qualifiedName) {
            return qualifiedName == null || qualifiedName.isBlank() || qualifiedName.startsWith("java.");
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
