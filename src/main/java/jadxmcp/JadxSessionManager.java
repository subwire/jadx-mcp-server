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

    public void renameClass(String sessionId, String className, String newName) {
        if (className.startsWith("java.")) {
            throw new IllegalArgumentException("Cannot rename java.* classes");
        }
        JadxDecompiler jadx = getSession(sessionId);
        JavaClass cls = jadx.searchJavaClassByOrigFullName(className);
        if (cls == null) {
            cls = jadx.searchJavaClassByAliasFullName(className);
        }
        if (cls == null) {
            throw new IllegalArgumentException("Class not found: " + className);
        }
        
        jadx.core.dex.info.ClassInfo classInfo = cls.getClassNode().getClassInfo();
        if (newName.contains(".")) {
            classInfo.changePkgAndName(
                newName.substring(0, newName.lastIndexOf('.')),
                newName.substring(newName.lastIndexOf('.') + 1)
            );
        } else {
            classInfo.changeShortName(newName);
        }
        
        cls.unload();
    }

    public void renameMethod(String sessionId, String className, String methodName, String newName) {
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
            jadx.core.dex.info.MethodInfo info = m.getMethodNode().getMethodInfo();
            if (info.getName().equals(methodName) || info.getAlias().equals(methodName)) {
                targetMethod = m;
                break;
            }
        }
        if (targetMethod == null) {
            throw new IllegalArgumentException("Method not found: " + methodName + " in class " + className);
        }
        
        targetMethod.getMethodNode().getMethodInfo().setAlias(newName);
        cls.unload();
    }

    public void renameField(String sessionId, String className, String fieldName, String newName) {
        JadxDecompiler jadx = getSession(sessionId);
        JavaClass cls = jadx.searchJavaClassByOrigFullName(className);
        if (cls == null) {
            cls = jadx.searchJavaClassByAliasFullName(className);
        }
        if (cls == null) {
            throw new IllegalArgumentException("Class not found: " + className);
        }
        
        jadx.api.JavaField targetField = null;
        for (jadx.api.JavaField f : cls.getFields()) {
            jadx.core.dex.info.FieldInfo info = f.getFieldNode().getFieldInfo();
            if (info.getName().equals(fieldName) || info.getAlias().equals(fieldName)) {
                targetField = f;
                break;
            }
        }
        if (targetField == null) {
            throw new IllegalArgumentException("Field not found: " + fieldName + " in class " + className);
        }
        
        targetField.getFieldNode().getFieldInfo().setAlias(newName);
        cls.unload();
    }

    public String getResource(String sessionId, String resourcePath) {
        JadxDecompiler jadx = getSession(sessionId);
        for (jadx.api.ResourceFile res : jadx.getResources()) {
            if (resourcePath.equals(res.getOriginalName())) {
                jadx.core.xmlgen.ResContainer rc = res.loadContent();
                if (rc != null && rc.getText() != null) {
                    return rc.getText().getCodeStr();
                }
                return "Resource found, but no text content available.";
            }
        }
        throw new IllegalArgumentException("Resource not found: " + resourcePath);
    }

    public List<String> searchXml(String sessionId, String searchString, boolean exactMatch, boolean caseSensitive, boolean xmlOnly) {
        JadxDecompiler jadx = getSession(sessionId);
        List<String> results = new java.util.ArrayList<>();
        String query = caseSensitive ? searchString : searchString.toLowerCase();

        for (jadx.api.ResourceFile res : jadx.getResources()) {
            String name = res.getOriginalName();
            if (xmlOnly && name != null && !name.toLowerCase().endsWith(".xml")) {
                continue;
            }
            try {
                jadx.core.xmlgen.ResContainer rc = res.loadContent();
                traverseResContainerForSearch(rc, name, query, exactMatch, caseSensitive, results);
            } catch (Exception e) {
                // Ignore decoding errors for individual resources
            }
        }
        return results;
    }

    private void traverseResContainerForSearch(jadx.core.xmlgen.ResContainer rc, String currentPath, String query, boolean exactMatch, boolean caseSensitive, List<String> results) {
        if (rc == null) return;
        jadx.core.xmlgen.ResContainer.DataType type = rc.getDataType();
        if (type == jadx.core.xmlgen.ResContainer.DataType.TEXT || type == jadx.core.xmlgen.ResContainer.DataType.RES_TABLE) {
            jadx.api.ICodeInfo textInfo = rc.getText();
            if (textInfo != null) {
                String text = textInfo.getCodeStr();
                if (text != null) {
                    String[] lines = text.split("\\r?\\n");
                    for (int i = 0; i < lines.length; i++) {
                        String line = lines[i];
                        boolean match = false;
                        if (exactMatch) {
                            match = caseSensitive ? line.equals(query) : line.equalsIgnoreCase(query);
                        } else {
                            match = caseSensitive ? line.contains(query) : line.toLowerCase().contains(query);
                        }
                        if (match) {
                            results.add(currentPath + ":" + (i + 1) + ": " + line.trim());
                        }
                    }
                }
            }
        }
        if (rc.getSubFiles() != null) {
            for (jadx.core.xmlgen.ResContainer sub : rc.getSubFiles()) {
                String subPath = currentPath;
                if (sub.getName() != null && !sub.getName().isEmpty()) {
                    subPath = currentPath + " -> " + sub.getName();
                }
                traverseResContainerForSearch(sub, subPath, query, exactMatch, caseSensitive, results);
            }
        }
    }
}
