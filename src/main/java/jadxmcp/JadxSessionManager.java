package jadxmcp;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.ResourceFile;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class JadxSessionManager {
    private final Map<String, JadxDecompiler> sessions = new ConcurrentHashMap<>();

    public String createSession(String apkPath) {
        File apkFile = new File(apkPath);
        if (!apkFile.exists()) {
            throw new IllegalArgumentException("File not found: " + apkPath);
        }

        JadxArgs args = new JadxArgs();
        args.setInputFile(apkFile);
        
        // TODO: detect and load project file (.jadx) if it exists

        JadxDecompiler jadx = new JadxDecompiler(args);
        jadx.load();

        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, jadx);
        return sessionId;
    }

    public JadxDecompiler getSession(String sessionId) {
        JadxDecompiler jadx = sessions.get(sessionId);
        if (jadx == null) {
            throw new IllegalArgumentException("Invalid session ID: " + sessionId);
        }
        return jadx;
    }

    public void closeSession(String sessionId) {
        JadxDecompiler jadx = sessions.remove(sessionId);
        if (jadx != null) {
            jadx.close();
        }
    }
    
    public String getClassSource(String sessionId, String className) {
        JadxDecompiler jadx = getSession(sessionId);
        JavaClass cls = jadx.searchJavaClassByOrigFullName(className);
        if (cls == null) {
            // Try alias name
            cls = jadx.searchJavaClassByAliasFullName(className);
        }
        if (cls == null) {
            throw new IllegalArgumentException("Class not found: " + className);
        }
        return cls.getCode();
    }
    
    public List<String> listClasses(String sessionId, String packagePrefix) {
        JadxDecompiler jadx = getSession(sessionId);
        return jadx.getClasses().stream()
                .map(JavaClass::getFullName)
                .filter(name -> packagePrefix == null || packagePrefix.isEmpty() || name.startsWith(packagePrefix))
                .collect(Collectors.toList());
    }

    public String getManifest(String sessionId) {
        JadxDecompiler jadx = getSession(sessionId);
        for (ResourceFile res : jadx.getResources()) {
            if ("AndroidManifest.xml".equals(res.getOriginalName())) {
                return res.loadContent().getText().getCodeStr();
            }
        }
        return "AndroidManifest.xml not found";
    }

    public String getMethodSource(String sessionId, String className, String methodName) {
        JadxDecompiler jadx = getSession(sessionId);
        JavaClass cls = jadx.searchJavaClassByOrigFullName(className);
        if (cls == null) {
            cls = jadx.searchJavaClassByAliasFullName(className);
        }
        if (cls == null) {
            throw new IllegalArgumentException("Class not found: " + className);
        }
        for (jadx.api.JavaMethod m : cls.getMethods()) {
            if (m.getName().equals(methodName)) {
                return m.getCodeStr();
            }
        }
        throw new IllegalArgumentException("Method not found: " + methodName + " in class " + className);
    }

    public List<String> getMethodXrefs(String sessionId, String className, String methodName) {
        JadxDecompiler jadx = getSession(sessionId);
        JavaClass cls = jadx.searchJavaClassByOrigFullName(className);
        if (cls == null) {
            cls = jadx.searchJavaClassByAliasFullName(className);
        }
        if (cls == null) {
            throw new IllegalArgumentException("Class not found: " + className);
        }
        
        jadx.api.JavaMethod targetMethod = null;
        for (jadx.api.JavaMethod m : cls.getMethods()) {
            if (m.getName().equals(methodName)) {
                targetMethod = m;
                break;
            }
        }
        if (targetMethod == null) {
            throw new IllegalArgumentException("Method not found: " + methodName + " in class " + className);
        }
        
        List<jadx.api.JavaNode> usages = targetMethod.getUseIn();
        if (usages == null) return List.of();
        
        return usages.stream()
            .map(node -> {
                String parent = node.getDeclaringClass() != null ? node.getDeclaringClass().getFullName() : "";
                return parent + "." + node.getName();
            })
            .distinct()
            .collect(Collectors.toList());
    }

    public List<String> getClassXrefs(String sessionId, String className) {
        JadxDecompiler jadx = getSession(sessionId);
        JavaClass cls = jadx.searchJavaClassByOrigFullName(className);
        if (cls == null) {
            cls = jadx.searchJavaClassByAliasFullName(className);
        }
        if (cls == null) {
            throw new IllegalArgumentException("Class not found: " + className);
        }
        
        List<jadx.api.JavaNode> usages = cls.getUseIn();
        if (usages == null) return List.of();
        
        return usages.stream()
            .map(node -> {
                String parent = node.getDeclaringClass() != null ? node.getDeclaringClass().getFullName() : "";
                return parent + "." + node.getName();
            })
            .distinct()
            .collect(Collectors.toList());
    }

    public List<String> searchStrings(String sessionId, String searchString, boolean exactMatch, boolean caseSensitive) {
        JadxDecompiler jadx = getSession(sessionId);
        List<String> results = new java.util.ArrayList<>();
        
        String query = caseSensitive ? searchString : searchString.toLowerCase();
        
        for (JavaClass cls : jadx.getClasses()) {
            for (jadx.api.JavaMethod method : cls.getMethods()) {
                jadx.core.dex.nodes.MethodNode mn = method.getMethodNode();
                if (mn.getInstructions() != null) {
                    for (jadx.core.dex.nodes.InsnNode insn : mn.getInstructions()) {
                        if (insn instanceof jadx.core.dex.instructions.ConstStringNode) {
                            String val = ((jadx.core.dex.instructions.ConstStringNode) insn).getString();
                            if (val == null) continue;
                            
                            boolean match = false;
                            if (exactMatch) {
                                match = caseSensitive ? val.equals(query) : val.equalsIgnoreCase(query);
                            } else {
                                match = caseSensitive ? val.contains(query) : val.toLowerCase().contains(query);
                            }
                            if (match) {
                                results.add(cls.getFullName() + "." + method.getName() + " : \"" + val + "\"");
                            }
                        }
                    }
                }
            }
        }
        return results.stream().distinct().collect(Collectors.toList());
    }
}
