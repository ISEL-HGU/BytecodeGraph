
package org.example;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.modref.ModRef;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.MonitorUtil;
import com.ibm.wala.util.intset.OrdinalSet;

import java.io.File;
import java.util.*;

public class WalaSession {

    public final AnalysisScope scope;
    public final IClassHierarchy cha;
    public final AnalysisCache cache;
    public final CallGraph cg;
    public final PointerAnalysis<InstanceKey> pa;
    public final com.ibm.wala.ipa.modref.ModRef<com.ibm.wala.ipa.callgraph.propagation.InstanceKey> modRef;
    public Map<CGNode, OrdinalSet<PointerKey>> modCache = new HashMap<>();
    public Map<CGNode, OrdinalSet<PointerKey>> refCache = new HashMap<>();


    private WalaSession(AnalysisScope scope, IClassHierarchy cha, AnalysisCache cache,
                        CallGraph cg, PointerAnalysis pa, ModRef modRef) {
        this.scope = scope; this.cha = cha; this.cache = cache;
        this.cg = cg; this.pa = pa;
        this.modRef = modRef;
    }

    /** 루트(classpath root)로 세션을 1회 초기화 */
    public static WalaSession init(String classpathRoot, Set<String> unblockPatterns, List<String> extraLibPaths) throws Exception {
        AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();

        // 1) 동적 Exclusions 설정 (파일 수정 없이 메모리에서 처리)
        File exclusionsFile = new File("exclusions.txt");
        if (exclusionsFile.exists()) {
            List<String> filteredLines = new ArrayList<>();
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(exclusionsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        filteredLines.add(line);
                        continue;
                    }

                    // unblockPatterns에 포함된 패키지라면 주석 처리된 것처럼 무시
                    boolean shouldUnblock = unblockPatterns.stream().anyMatch(trimmed::contains);
                    if (shouldUnblock) {
                        filteredLines.add("# " + line + " // Dynamically unblocked");
                    } else {
                        filteredLines.add(line);
                    }
                }
            }

            // 메모리 상의 데이터를 기반으로 Exclusions 설정
            String combined = String.join("\n", filteredLines);
            scope.setExclusions(new com.ibm.wala.util.config.FileOfClasses(
                    new java.io.ByteArrayInputStream(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }

        // 2) 기본 클래스패스 및 JDK 추가
        com.ibm.wala.core.util.config.AnalysisScopeReader.instance
                .addClassPathToScope(classpathRoot, scope, ClassLoaderReference.Application);
        addPrimordialJars(scope);

        // 3) 외부 라이브러리(JavaFX 등) 동적 추가
        if (extraLibPaths != null) {
            for (String libPath : extraLibPaths) {
                File libFile = new File(libPath);
                if (libFile.exists()) {
                    if (libFile.isDirectory()) {
                        // 디렉토리 내 모든 jar 추가
                        File[] jars = libFile.listFiles((dir, name) -> name.endsWith(".jar"));
                        if (jars != null) {
                            for (File jar : jars) {
                                scope.addToScope(ClassLoaderReference.Extension, new java.util.jar.JarFile(jar));
                            }
                        }
                    } else if (libPath.endsWith(".jar")) {
                        scope.addToScope(ClassLoaderReference.Extension, new java.util.jar.JarFile(libFile));
                    }
                }
            }
        }

        // 3) 핵심 분석 인프라 생성 (실패 시 진단 로직 작동)
        IClassHierarchy cha;
        try {
            cha = com.ibm.wala.ipa.cha.ClassHierarchyFactory.make(scope);
        } catch (Exception e) {
            // Exception e를 추가로 인자로 넘김
            throw e;
        }

        // 4) Entrypoints 및 분석 옵션 설정
        Iterable<Entrypoint> eps =
                new com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints(scope, cha);
        AnalysisOptions options = new AnalysisOptions(scope, eps);
        // Reflection 설정은 유지하기로 하였으므로 기본값(또는 명시적 설정)을 사용합니다.

        AnalysisCache cache = new AnalysisCacheImpl();

        // 5) CallGraph 및 PointerAnalysis 생성
        CallGraphBuilder<InstanceKey> builder =
                Util.makeZeroCFABuilder(Language.JAVA, options, cache, cha);
        CallGraph cg = builder.makeCallGraph(options, (MonitorUtil.IProgressMonitor) null);
        PointerAnalysis<InstanceKey> pa = builder.getPointerAnalysis();

        // 6) ModRef 전역 계산 초기화
        ModRef<InstanceKey> modRef = ModRef.make();

        return new WalaSession(scope, cha, cache, cg, pa, modRef);
    }

    private static void addPrimordialJars(AnalysisScope scope) throws Exception {
        String[] rels = {
                "lib\\rt.jar", "lib\\jce.jar", "lib\\jsse.jar", "lib\\sunjce_provider.jar"
        };
        boolean hasRt=false, hasJce=false;
        for (String rel : rels) {
            File jar = new File(rel);
            if (jar.exists()) {
                scope.addToScope(ClassLoaderReference.Primordial, new java.util.jar.JarFile(jar));
                if (rel.endsWith("rt.jar")) hasRt = true;
                if (rel.endsWith("jce.jar")) hasJce = true;
            }
        }
        if (!hasRt)  throw new IllegalStateException("rt.jar not found");
        if (!hasJce) throw new IllegalStateException("jce.jar not found");
    }

}
