package example;

import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.tree.*;
import java.lang.invoke.MethodHandles;
import java.util.*;
import sun.misc.Unsafe;

public class UsesJdkReferences {
    private Map.Entry<String, String> entry;
    private MethodHandles.Lookup lookup;
    private Tree tree;
    private Tree.Kind treeKind;
    private TreeScanner<Void, Void> treeScanner;
    private JCTree.JCCompilationUnit compilationUnit;
    private Unsafe unsafe;
}
