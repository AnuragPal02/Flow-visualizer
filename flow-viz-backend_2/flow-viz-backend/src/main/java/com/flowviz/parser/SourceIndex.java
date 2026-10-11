package com.flowviz.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every class/interface found under the target repo's source roots. If a type is not in here it is
 * not application code (JDK, Spring, Hibernate, ...), so it can never become a graph node.
 */
public class SourceIndex {

    public static final class ClassInfo {
        public final String qualifiedName;
        public final String packageName;
        public final ClassOrInterfaceDeclaration decl;
        public final CompilationUnit cu;
        public final String file;

        ClassInfo(String qualifiedName, String packageName, ClassOrInterfaceDeclaration decl, CompilationUnit cu, String file) {
            this.qualifiedName = qualifiedName;
            this.packageName = packageName;
            this.decl = decl;
            this.cu = cu;
            this.file = file;
        }

        public String simpleName() {
            return decl.getNameAsString();
        }
    }

    private final Map<String, ClassInfo> byQualified = new LinkedHashMap<>();
    private final Map<String, List<ClassInfo>> bySimple = new HashMap<>();
    private final Map<String, List<ClassInfo>> implCache = new HashMap<>();
    private final Map<String, Path> pathByFile = new HashMap<>();
    private final Map<String, List<String>> linesByFile = new HashMap<>();
    private final Set<String> warnings;

    public SourceIndex(Set<String> warnings) {
        this.warnings = warnings;
    }

    public void addUnit(CompilationUnit cu, String file) {
        String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        cu.getStorage().ifPresent(st -> pathByFile.putIfAbsent(file, st.getPath()));
        for (ClassOrInterfaceDeclaration decl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            String qn = qualifiedNameOf(decl, pkg);
            ClassInfo info = new ClassInfo(qn, pkg, decl, cu, file);
            if (byQualified.putIfAbsent(qn, info) == null) {
                bySimple.computeIfAbsent(decl.getNameAsString(), k -> new ArrayList<>()).add(info);
            }
        }
    }

    /** Full text of a source file (loaded on first use). */
    public String source(String file) {
        return String.join("\n", lines(file));
    }

    /** One line of a source file, trimmed and shortened for display. */
    public String lineText(String file, int line) {
        List<String> lines = lines(file);
        if (line <= 0 || line > lines.size()) return "";
        String text = lines.get(line - 1).trim().replaceAll("\\s+", " ");
        return text.length() > 160 ? text.substring(0, 157) + "..." : text;
    }

    private List<String> lines(String file) {
        return linesByFile.computeIfAbsent(file, f -> readLines(pathByFile.get(f)));
    }

    private static List<String> readLines(Path path) {
        if (path == null) return List.of();
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            try {
                return Files.readAllLines(path, StandardCharsets.ISO_8859_1);
            } catch (IOException ex) {
                return List.of();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    public Collection<ClassInfo> allClasses() {
        return byQualified.values();
    }

    public int size() {
        return byQualified.size();
    }

    public ClassInfo find(String qualifiedName) {
        return byQualified.get(qualifiedName);
    }

    /** The class this one is nested in, or null for top-level classes. */
    public ClassInfo enclosing(ClassInfo c) {
        int i = c.qualifiedName.lastIndexOf('.');
        return i < 0 ? null : byQualified.get(c.qualifiedName.substring(0, i));
    }

    public ClassInfo resolveType(Type type, ClassInfo ctx) {
        if (type == null || !type.isClassOrInterfaceType()) return null;
        return resolveTypeName(typeName(type.asClassOrInterfaceType()), ctx);
    }

    /** Maps a name as written in {@code ctx}'s source to an application class, or null if it is not one. */
    public ClassInfo resolveTypeName(String name, ClassInfo ctx) {
        if (name == null || name.isEmpty()) return null;

        if (name.contains(".")) {
            ClassInfo direct = byQualified.get(name);
            if (direct != null) return direct;
            List<ClassInfo> matches = new ArrayList<>();
            for (ClassInfo c : bySimple.getOrDefault(name.substring(name.lastIndexOf('.') + 1), List.of())) {
                if (c.qualifiedName.endsWith("." + name)) matches.add(c);
            }
            return matches.size() == 1 ? matches.get(0) : null;
        }

        List<ClassInfo> candidates = bySimple.getOrDefault(name, List.of());
        if (candidates.isEmpty()) return null; // not ours

        // 1. explicit import: if it points outside the repo, the name means a library class
        for (ImportDeclaration imp : ctx.cu.getImports()) {
            if (imp.isAsterisk() || imp.isStatic()) continue;
            String in = imp.getNameAsString();
            if (in.endsWith("." + name)) return byQualified.get(in);
        }
        // 2. member of this class or an enclosing class
        for (ClassInfo o = ctx; o != null; o = enclosing(o)) {
            ClassInfo member = byQualified.get(o.qualifiedName + "." + name);
            if (member != null) return member;
        }
        // 3. same package
        ClassInfo samePackage = byQualified.get(ctx.packageName.isEmpty() ? name : ctx.packageName + "." + name);
        if (samePackage != null) return samePackage;
        // 4. wildcard imports
        for (ImportDeclaration imp : ctx.cu.getImports()) {
            if (imp.isAsterisk() && !imp.isStatic()) {
                ClassInfo hit = byQualified.get(imp.getNameAsString() + "." + name);
                if (hit != null) return hit;
            }
        }
        // 5. only one class in the repo has this name
        if (candidates.size() == 1) return candidates.get(0);

        warnings.add("Could not resolve application class '" + name + "' referenced in " + ctx.simpleName()
                + " (" + candidates.size() + " classes share that name)");
        return null;
    }

    /** Direct supertypes that are application classes (library supertypes are ignored). */
    public List<ClassInfo> supertypes(ClassInfo c) {
        List<ClassInfo> out = new ArrayList<>();
        for (ClassOrInterfaceType t : c.decl.getExtendedTypes()) addIfPresent(out, resolveTypeName(typeName(t), c), c);
        for (ClassOrInterfaceType t : c.decl.getImplementedTypes()) addIfPresent(out, resolveTypeName(typeName(t), c), c);
        return out;
    }

    /** Concrete application classes that extend/implement {@code base}, sorted by name for determinism. */
    public List<ClassInfo> implementations(ClassInfo base) {
        return implCache.computeIfAbsent(base.qualifiedName, k -> {
            List<ClassInfo> out = new ArrayList<>();
            for (ClassInfo c : byQualified.values()) {
                if (c == base || c.decl.isInterface() || c.decl.isAbstract()) continue;
                if (isSubtype(c, base, new HashSet<>())) out.add(c);
            }
            out.sort(Comparator.comparing(c -> c.qualifiedName));
            return out;
        });
    }

    private boolean isSubtype(ClassInfo c, ClassInfo base, Set<String> seen) {
        if (!seen.add(c.qualifiedName)) return false;
        for (ClassInfo s : supertypes(c)) {
            if (s == base || isSubtype(s, base, seen)) return true;
        }
        return false;
    }

    private static void addIfPresent(List<ClassInfo> out, ClassInfo s, ClassInfo self) {
        if (s != null && s != self) out.add(s);
    }

    private static String typeName(ClassOrInterfaceType t) {
        String n = t.getNameAsString();
        return t.getScope().map(s -> typeName(s) + "." + n).orElse(n);
    }

    private static String qualifiedNameOf(TypeDeclaration<?> decl, String pkg) {
        Deque<String> names = new ArrayDeque<>();
        Node n = decl;
        while (n != null) {
            if (n instanceof TypeDeclaration) names.addFirst(((TypeDeclaration<?>) n).getNameAsString());
            n = n.getParentNode().orElse(null);
        }
        String joined = String.join(".", names);
        return pkg.isEmpty() ? joined : pkg + "." + joined;
    }
}
