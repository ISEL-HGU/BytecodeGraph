
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
    public static WalaSession init(String classpathRoot) throws Exception {
        AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();

        // 1) 제외 설정(Exclusions) 파일 로드 및 패턴 추출 (진단용)
        File exclusionsFile = new File("exclusions.txt");
        java.util.Set<String> exclusionPatterns = new java.util.HashSet<>();
        if (exclusionsFile.exists()) {
            // 파일을 읽어 실제 적용된 패턴들을 수집합니다.
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(exclusionsFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.startsWith("#") && !line.isEmpty()) {
                        exclusionPatterns.add(line);
                    }
                }
            }
            try (java.io.InputStream is = new java.io.FileInputStream(exclusionsFile)) {
                scope.setExclusions(new com.ibm.wala.util.config.FileOfClasses(is));
            }
        }

        // 2) 분석 대상 클래스패스 및 JDK 라이브러리 추가
        com.ibm.wala.core.util.config.AnalysisScopeReader.instance
                .addClassPathToScope(classpathRoot, scope, ClassLoaderReference.Application);
        addPrimordialJars(scope);

        // 3) 핵심 분석 인프라 생성 (실패 시 진단 로직 작동)
        IClassHierarchy cha;
        try {
            cha = com.ibm.wala.ipa.cha.ClassHierarchyFactory.make(scope);
        } catch (Exception e) {
            // Exception e를 추가로 인자로 넘김
            diagnoseFailure(classpathRoot, exclusionPatterns, e);
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

    /**
     * Diagnoses the cause of initialization failure and provides an English report.
     */
    public static void diagnoseFailure(String path, Set<String> exclusions, Exception e) {
        System.err.println("\n[!] Analysis Diagnosis Report");
        System.err.println("--------------------------------------------------");

        String errorMsg = e.getMessage() != null ? e.getMessage() : "";
        String missingClass = "";

        // WALA의 클래스 표기법(Lpackage/Class)에서 클래스명 추출
        if (errorMsg.contains("L")) {
            int start = errorMsg.indexOf("L");
            int end = errorMsg.indexOf(" ", start);
            missingClass = (end == -1) ? errorMsg.substring(start) : errorMsg.substring(start, end);
            missingClass = missingClass.replace("/", ".");
        }

        System.err.println("▶ Exception: " + e.getClass().getSimpleName());
        System.err.println("▶ Message: " + errorMsg);

        boolean isExcluded = false;
        if (!missingClass.isEmpty()) {
            String finalPath = missingClass.replace(".", "/");
            for (String pattern : exclusions) {
                if (finalPath.matches(pattern.replace("\\/", "/"))) {
                    isExcluded = true;
                    System.err.println("▶ Diagnosis: [Blocked by Exclusion Settings]");
                    System.err.println("   The class '" + missingClass + "' or its parent is restricted.");
                    System.err.println("   Rule: " + pattern);
                    System.err.println("   Solution: Comment out (#) this rule in exclusions.txt.");
                    break;
                }
            }
        }

        if (!isExcluded) {
            System.err.println("▶ Diagnosis: [Missing Dependency or Classpath Issue]");
            System.err.println("   Solution: Check if the JAR containing '" + missingClass + "' is in: " + path);
        }
        System.err.println("--------------------------------------------------\n");
    }
}
