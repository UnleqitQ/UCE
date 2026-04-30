package org.texttechnologylab.uce.corpusimporter;

import com.google.gson.Gson;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.texttechnologylab.uce.common.config.CommonConfig;
import org.texttechnologylab.uce.common.config.CorpusConfig;
import org.texttechnologylab.uce.common.config.SpringConfig;
import org.texttechnologylab.uce.common.exceptions.ExceptionUtils;
import org.texttechnologylab.uce.common.models.corpus.Corpus;
import org.texttechnologylab.uce.common.models.corpus.Document;
import org.texttechnologylab.uce.common.models.imp.ImportLog;
import org.texttechnologylab.uce.common.models.imp.ImportStatus;
import org.texttechnologylab.uce.common.models.imp.LogStatus;
import org.texttechnologylab.uce.common.models.imp.UCEImport;
import org.texttechnologylab.uce.common.security.DocumentAccessManager;
import org.texttechnologylab.uce.common.services.EmbeddingService;
import org.texttechnologylab.uce.common.services.LexiconService;
import org.texttechnologylab.uce.common.services.PostgresqlDataInterface_Impl;
import org.texttechnologylab.uce.common.utils.StringUtils;
import org.texttechnologylab.uce.common.utils.SystemStatus;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DUUIAEImporter extends JCasAnnotator_ImplBase {
    private static final Logger logger = LogManager.getLogger(DUUIAEImporter.class);
    private static final Gson gson = new Gson();
    private static final int BATCH_SIZE = 2000;
    private static final Path EXTERNAL_CORPUS_CONFIG_PATH = Path.of("/app/config/UCECorpusConfigEmpty.json");
    private static final Path LEGACY_CORPUS_CONFIG_PATH = Path.of("uce.corpus-importer/src/main/resources/UCECorpusConfigEmpty.json");

    public static final String PARAM_IMPORTER_NUMBER = "importerNumber";
    @ConfigurationParameter(name = PARAM_IMPORTER_NUMBER, mandatory = false, defaultValue = "1")
    private int importerNumber;

    public static final String PARAM_NUM_THREADS = "numThreads";
    @ConfigurationParameter(name = PARAM_NUM_THREADS, mandatory = false, defaultValue = "1")
    private int numThreads;

    public static final String PARAM_DISPATCH_MODE = "dispatchMode";
    @ConfigurationParameter(name = PARAM_DISPATCH_MODE, mandatory = false, defaultValue = "MIXED")
    private String dispatchMode;

    public static final String PARAM_CAS_VIEW = "casView";
    @ConfigurationParameter(name = PARAM_CAS_VIEW, mandatory = false)
    private String casView;

    public static final String PARAM_SOURCE_PATH = "sourcePath";
    @ConfigurationParameter(name = PARAM_SOURCE_PATH, mandatory = false)
    private String sourcePath;

    public static final String PARAM_CORPUS_CONFIG_JSON = "corpusConfigJson";
    @ConfigurationParameter(name = PARAM_CORPUS_CONFIG_JSON, mandatory = false)
    private String corpusConfigJson;

    public static final String PARAM_STAGE_DISPATCH_MODES = "stageDispatchModes";
    @ConfigurationParameter(name = PARAM_STAGE_DISPATCH_MODES, mandatory = false)
    private String[] stageDispatchModes;

    public static final String PARAM_STAGE_PARALLELISM = "stageParallelism";
    @ConfigurationParameter(name = PARAM_STAGE_PARALLELISM, mandatory = false)
    private String[] stageParallelism;

    private AnnotationConfigApplicationContext springContext;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        this.springContext = new AnnotationConfigApplicationContext(SpringConfig.class);
    }

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        ImportExecutionContext ctx = new ImportExecutionContext();
        ctx.importId = UUID.randomUUID().toString();
        ctx.importerNumber = importerNumber;
        ctx.numThreads = Math.max(1, numThreads);
        ctx.dispatchMode = dispatchMode;
        ctx.casView = casView;
        ctx.sourcePath = sourcePath;
        ctx.corpusConfigJson = corpusConfigJson;
        ctx.stageDispatchModes = stageDispatchModes;
        ctx.stageParallelism = stageParallelism;
        ctx.originalCas = jCas;
        ctx.workingCas = jCas;
        ctx.springContext = springContext;
        ctx.filePath = "DUUI-CAS-Import-" + System.currentTimeMillis() + ".xmi";

        try {
            ImportExecutionContextHolder.set(ctx);
            executeStageGraph(jCas, ctx.numThreads);
        } catch (Exception e) {
            throw new AnalysisEngineProcessException(e);
        } finally {
            closeGuardQuietly(ctx);
            ImportExecutionContextHolder.clear();
        }
    }

    @Override
    public void destroy() {
        if (springContext != null) {
            springContext.close();
        }
        super.destroy();
    }

    private static void closeGuardQuietly(ImportExecutionContext ctx) {
        if (ctx != null && ctx.adminGuard != null) {
            try {
                ctx.adminGuard.close();
            } catch (Exception ignored) {
            }
            ctx.adminGuard = null;
        }
    }

    private static void executeStageGraph(JCas jCas, int parallelism) throws Exception {
        DispatchRuntime dispatchRuntime = DispatchRuntime.fromContext(ImportExecutionContextHolder.get(), parallelism);
        try (dispatchRuntime) {
            buildPipeline().execute(jCas, dispatchRuntime);
        }
    }

    private static Pipeline buildPipeline() {
        return new Pipeline(List.of(
                new ImportInitAE(),
                new CorpusConfigLoadAE(),
                new CorpusEnsureAE(),
                new UceMetadataFilterLoadAE(),
                new CasViewSelectAE(),
                new DocumentCreateAE(),
                new DocumentDuplicateCheckAE(),
                new MimePayloadAE(),
                new MetadataTitleInfoAE(),
                new S3ArchiveAE(),
                MultiStage.of(DispatchPolicy.cpu(), List.of(
                        new UceMetadataExtractAE(),
                        new SentenceExtractAE(),
                        new NamedEntityExtractAE(),
                        new SentimentExtractAE(),
                        new EmotionExtractAE(),
                        new LemmaExtractAE(),
                        new SemanticRoleExtractAE(),
                        new TimeExtractAE(),
                        new WikiLinkExtractAE(),
                        new NegationExtractAE(),
                        new UnifiedTopicExtractAE(),
                        new PermissionExtractAE()
                )),
                new GeoNamesExtractAE(),
                new TaxonomyExtractAE(),
                new PageExtractAE(),
                MultiStage.of(DispatchPolicy.mixed(), List.of(
                        new ImageExtractAE(),
                        new LogicalLinksExtractAE()
                )),
                new DocumentPersistAE(),
                new DocumentPostProcessAE(),
                new BatchPostProcessAE(),
                new CorpusFinalizeAE(),
                new ImportLogAE()
        ));
    }

    enum DispatchMode {
        IO,
        CPU,
        MIXED
    }

    private enum ExecutorKind {
        PLATFORM,
        VIRTUAL,
        CALLER
    }

    static final class DispatchPolicy {
        private final DispatchMode mode;
        private final Integer parallelism;
        private final boolean caller;

        private DispatchPolicy(DispatchMode mode, Integer parallelism, boolean caller) {
            this.mode = mode;
            this.parallelism = parallelism;
            this.caller = caller;
        }

        static DispatchPolicy inherit() {
            return new DispatchPolicy(null, null, false);
        }

        static DispatchPolicy io() {
            return new DispatchPolicy(DispatchMode.IO, null, false);
        }

        static DispatchPolicy cpu() {
            return new DispatchPolicy(DispatchMode.CPU, null, false);
        }

        static DispatchPolicy mixed() {
            return new DispatchPolicy(DispatchMode.MIXED, null, false);
        }

        static DispatchPolicy caller() {
            return new DispatchPolicy(null, null, true);
        }

        static DispatchPolicy of(DispatchMode mode, Integer parallelism) {
            return new DispatchPolicy(mode, parallelism, false);
        }

        DispatchPolicy merge(DispatchPolicy override) {
            if (override == null) {
                return this;
            }
            return new DispatchPolicy(
                    override.mode != null ? override.mode : mode,
                    override.parallelism != null ? override.parallelism : parallelism,
                    override.caller || caller
            );
        }
    }

    private static final class Pipeline {
        private final List<?> nodes;

        private Pipeline(List<?> nodes) {
            this.nodes = nodes;
        }

        void execute(JCas jCas, DispatchRuntime dispatchRuntime) throws AnalysisEngineProcessException {
            for (Object node : nodes) {
                if (node instanceof StageAE) {
                    dispatchRuntime.executeStage((StageAE) node, DispatchPolicy.inherit(), jCas);
                } else if (node instanceof MultiStage) {
                    ((MultiStage) node).execute(jCas, dispatchRuntime);
                }
            }
        }
    }

    private static final class MultiStage {
        private final DispatchPolicy policy;
        private final List<StageAE> stages;

        private MultiStage(DispatchPolicy policy, List<StageAE> stages) {
            this.policy = policy;
            this.stages = stages;
        }

        static MultiStage of(List<StageAE> stages) {
            return new MultiStage(DispatchPolicy.inherit(), stages);
        }

        static MultiStage of(DispatchPolicy policy, List<StageAE> stages) {
            return new MultiStage(policy, stages);
        }

        public void execute(JCas jCas, DispatchRuntime dispatchRuntime) throws AnalysisEngineProcessException {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (StageAE stage : stages) {
                futures.add(dispatchRuntime.submitStage(stage, policy, jCas));
            }
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof AnalysisEngineProcessException) {
                    throw (AnalysisEngineProcessException) cause;
                }
                throw new AnalysisEngineProcessException(cause == null ? e : cause);
            }
        }
    }

    private static final class DispatchRuntime implements AutoCloseable {
        private final DispatchPolicy rootPolicy;
        private final Map<String, DispatchPolicy> stageOverrides;
        private final Map<ExecutorKey, DispatchExecutor> executors = new HashMap<>();

        private DispatchRuntime(
                DispatchPolicy rootPolicy,
                Map<String, DispatchPolicy> stageOverrides
        ) {
            this.rootPolicy = rootPolicy;
            this.stageOverrides = stageOverrides;
        }

        static DispatchRuntime fromContext(ImportExecutionContext ctx, int parallelism) {
            int configuredParallelism = Math.max(1, parallelism);
            DispatchMode rootMode = parseEnum(DispatchMode.class, ctx.dispatchMode, DispatchMode.MIXED);
            Map<String, DispatchPolicy> overrides = parseOverrides(
                    ctx.stageDispatchModes,
                    ctx.stageParallelism
            );

            return new DispatchRuntime(
                    DispatchPolicy.of(rootMode, configuredParallelism),
                    overrides
            );
        }

        void executeStage(StageAE stage, DispatchPolicy parentPolicy, JCas jCas) throws AnalysisEngineProcessException {
            try {
                submitStage(stage, parentPolicy, jCas).join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof AnalysisEngineProcessException) {
                    throw (AnalysisEngineProcessException) cause;
                }
                throw new AnalysisEngineProcessException(cause == null ? e : cause);
            }
        }

        CompletableFuture<Void> submitStage(StageAE stage, DispatchPolicy parentPolicy, JCas jCas) {
            DispatchPolicy resolved = resolve(stage, parentPolicy);
            ImportExecutionContext ctx = ImportExecutionContextHolder.get();

            if (resolved.caller) {
                return runInCaller(stage, jCas, ctx);
            }
            return executorFor(resolved).submit(() -> runWithContext(stage, jCas, ctx));
        }

        private DispatchPolicy resolve(StageAE stage, DispatchPolicy parentPolicy) {
            DispatchPolicy resolved = rootPolicy
                    .merge(parentPolicy)
                    .merge(stage.dispatchPolicy());

            DispatchPolicy override = stageOverrides.get(stage.getClass().getSimpleName());
            if (override == null) {
                override = stageOverrides.get(stage.getClass().getName());
            }
            resolved = resolved.merge(override);

            int parallelism = resolved.parallelism == null ? 1 : Math.max(0, resolved.parallelism);
            return new DispatchPolicy(resolved.mode, parallelism, resolved.caller);
        }

        private DispatchExecutor executorFor(DispatchPolicy policy) {
            ExecutorKey key = new ExecutorKey(executorForMode(policy.mode), policy.parallelism == null ? 1 : policy.parallelism);
            return executors.computeIfAbsent(key, DispatchRuntime::createExecutor);
        }

        private static ExecutorKind executorForMode(DispatchMode mode) {
            if (mode == DispatchMode.IO) {
                return ExecutorKind.VIRTUAL;
            }
            return ExecutorKind.PLATFORM;
        }

        private static CompletableFuture<Void> runInCaller(StageAE stage, JCas jCas, ImportExecutionContext ctx) {
            try {
                runWithContext(stage, jCas, ctx);
                return CompletableFuture.completedFuture(null);
            } catch (CompletionException e) {
                return CompletableFuture.failedFuture(e.getCause() == null ? e : e.getCause());
            }
        }

        private static void runWithContext(StageAE stage, JCas jCas, ImportExecutionContext ctx) {
            ImportExecutionContext previous = ImportExecutionContextHolder.get();
            AutoCloseable adminGuard = null;
            try {
                ImportExecutionContextHolder.set(ctx);
                if (ctx != null && ctx.accessManager != null) {
                    adminGuard = ctx.accessManager.asAdmin();
                }
                stage.process(jCas);
            } catch (AnalysisEngineProcessException e) {
                throw new CompletionException(e);
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                if (adminGuard != null) {
                    try {
                        adminGuard.close();
                    } catch (Exception ignored) {
                    }
                }
                if (previous != null) {
                    ImportExecutionContextHolder.set(previous);
                } else {
                    ImportExecutionContextHolder.clear();
                }
            }
        }

        private static Map<String, DispatchPolicy> parseOverrides(
                String[] stageDispatchModes,
                String[] stageParallelism
        ) {
            Map<String, DispatchPolicy> overrides = new HashMap<>();
            mergeDispatchModes(overrides, stageDispatchModes);
            mergeParallelism(overrides, stageParallelism);
            return overrides;
        }

        private static void mergeDispatchModes(Map<String, DispatchPolicy> overrides, String[] entries) {
            for (StageDispatchEntry entry : parseEntries(entries)) {
                mergeOverride(overrides, entry.stageName, DispatchPolicy.of(
                        parseEnum(DispatchMode.class, entry.value, null),
                        null
                ));
            }
        }

        private static void mergeParallelism(Map<String, DispatchPolicy> overrides, String[] entries) {
            for (StageDispatchEntry entry : parseEntries(entries)) {
                mergeOverride(overrides, entry.stageName, DispatchPolicy.of(
                        null,
                        Integer.parseInt(entry.value.trim())
                ));
            }
        }

        private static void mergeOverride(Map<String, DispatchPolicy> overrides, String stageName, DispatchPolicy policy) {
            overrides.merge(stageName, policy, DispatchPolicy::merge);
        }

        private static List<StageDispatchEntry> parseEntries(String[] entries) {
            if (entries == null || entries.length == 0) {
                return List.of();
            }
            List<StageDispatchEntry> parsed = new ArrayList<>();
            for (String entry : entries) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                int separator = entry.indexOf('=');
                if (separator < 0) {
                    separator = entry.indexOf(':');
                }
                if (separator < 1 || separator == entry.length() - 1) {
                    throw new IllegalArgumentException("Invalid stage dispatch override: " + entry);
                }
                parsed.add(new StageDispatchEntry(
                        entry.substring(0, separator).trim(),
                        entry.substring(separator + 1).trim()
                ));
            }
            return parsed;
        }

        private static DispatchExecutor createExecutor(ExecutorKey key) {
            if (key.kind == ExecutorKind.VIRTUAL) {
                return new DispatchExecutor(
                        Executors.newVirtualThreadPerTaskExecutor(),
                        key.parallelism > 0 ? new Semaphore(key.parallelism) : null
                );
            }
            return new DispatchExecutor(Executors.newFixedThreadPool(Math.max(1, key.parallelism)), null);
        }

        @Override
        public void close() {
            for (DispatchExecutor executor : executors.values()) {
                executor.close();
            }
        }
    }

    private static final class ExecutorKey {
        private final ExecutorKind kind;
        private final int parallelism;

        private ExecutorKey(ExecutorKind kind, int parallelism) {
            this.kind = kind;
            this.parallelism = parallelism;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ExecutorKey)) {
                return false;
            }
            ExecutorKey that = (ExecutorKey) o;
            return parallelism == that.parallelism && kind == that.kind;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, parallelism);
        }
    }

    private static final class DispatchExecutor implements AutoCloseable {
        private final ExecutorService executor;
        private final Semaphore permits;

        private DispatchExecutor(ExecutorService executor, Semaphore permits) {
            this.executor = executor;
            this.permits = permits;
        }

        CompletableFuture<Void> submit(Runnable runnable) {
            return CompletableFuture.runAsync(() -> runWithPermit(runnable), executor);
        }

        private void runWithPermit(Runnable runnable) {
            boolean acquired = false;
            try {
                if (permits != null) {
                    permits.acquire();
                    acquired = true;
                }
                runnable.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } finally {
                if (acquired) {
                    permits.release();
                }
            }
        }

        @Override
        public void close() {
            executor.shutdown();
        }
    }

    private static final class StageDispatchEntry {
        private final String stageName;
        private final String value;

        private StageDispatchEntry(String stageName, String value) {
            this.stageName = stageName;
            this.value = value;
        }
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, T defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Enum.valueOf(enumType, value.trim().toUpperCase());
    }

    private static Object invokePrivate(Object target, String methodName, Class<?>[] types, Object[] args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(methodName, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    static class ImportExecutionContext {
        String importId;
        int importerNumber;
        int numThreads;
        String dispatchMode;
        String casView;
        String sourcePath;
        String corpusConfigJson;
        String[] stageDispatchModes;
        String[] stageParallelism;
        long startedAt = System.currentTimeMillis();
        long finishedAt;
        String filePath;
        JCas originalCas;
        JCas workingCas;
        AnnotationConfigApplicationContext springContext;
        DocumentAccessManager accessManager;
        AutoCloseable adminGuard;
        PostgresqlDataInterface_Impl db;
        LexiconService lexiconService;
        EmbeddingService embeddingService;
        Importer importer;
        CorpusConfig corpusConfig;
        Corpus corpus;
        Document document;
        boolean duplicateDocument;
        DocumentImportContinuation continuation;
        AtomicReference<CountDownLatch> batchLatch;
        AtomicInteger docInBatch;
        Object lock;
    }

    static class ImportExecutionContextHolder {
        private static final ThreadLocal<ImportExecutionContext> HOLDER = new ThreadLocal<>();
        static void set(ImportExecutionContext ctx) { HOLDER.set(ctx); }
        static ImportExecutionContext get() { return HOLDER.get(); }
        static void clear() { HOLDER.remove(); }
    }

    static abstract class StageAE extends JCasAnnotator_ImplBase {
        protected ImportExecutionContext ctx() { return ImportExecutionContextHolder.get(); }
        protected DispatchPolicy dispatchPolicy() { return DispatchPolicy.inherit(); }
    }

    public static class ImportInitAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.caller();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                ctx().db = ctx().springContext.getBean(PostgresqlDataInterface_Impl.class);
                ctx().lexiconService = ctx().springContext.getBean(LexiconService.class);
                ctx().embeddingService = ctx().springContext.getBean(EmbeddingService.class);
                ctx().accessManager = ctx().springContext.getBean(DocumentAccessManager.class);
                ctx().adminGuard = ctx().accessManager.asAdmin();

                var commonConfig = new CommonConfig();
                ExceptionUtils.tryCatchLog(
                        () -> SystemStatus.executeExternalDatabaseScripts(commonConfig.getDatabaseScriptsLocation(), ctx().db),
                        (ex) -> logger.warn("Couldn't execute external DB scripts.", ex));

                var uceImport = new UCEImport(ctx().importId, "import from DUUIAEImporter", ImportStatus.STARTING);
                uceImport.setTotalDocuments(1);
                ctx().db.saveOrUpdateUceImport(uceImport);

                ctx().importer = new Importer(
                        ctx().springContext,
                        null,
                        ctx().importerNumber,
                        ctx().importId,
                        ctx().casView,
                        ctx().originalCas,
                        true
                );
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    public static class CorpusConfigLoadAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.io();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                if (ctx().corpusConfigJson != null && !ctx().corpusConfigJson.isBlank()) {
                    ctx().corpusConfig = gson.fromJson(ctx().corpusConfigJson, CorpusConfig.class);
                    return;
                }
                Path configPath = Files.exists(EXTERNAL_CORPUS_CONFIG_PATH) ? EXTERNAL_CORPUS_CONFIG_PATH : LEGACY_CORPUS_CONFIG_PATH;
                String json = Files.readString(configPath);
                ctx().corpusConfig = gson.fromJson(json, CorpusConfig.class);
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    public static class CorpusEnsureAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.io();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                var corpus = new Corpus();
                Corpus existingCorpus = Importer.CreateDBCorpus(corpus, ctx().corpusConfig, ctx().db);
                ctx().corpus = existingCorpus != null ? existingCorpus : corpus;

                var importModel = ctx().db.getUceImportByImportId(ctx().importId);
                if (importModel != null) {
                    importModel.setTargetCorpusName(ctx().corpus.getName());
                    importModel.setTargetCorpusId(ctx().corpus.getId());
                    importModel.setStatus(ImportStatus.RUNNING);
                    ctx().db.saveOrUpdateUceImport(importModel);
                }
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    public static class UceMetadataFilterLoadAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.io();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                if (ctx().corpusConfig.getAnnotations().isUceMetadata()) {
                    var filters = new CopyOnWriteArrayList<>(ctx().db.getUCEMetadataFiltersByCorpusId(ctx().corpus.getId()));
                    setPrivateField(ctx().importer, "uceMetadataFilters", filters);
                }

                ctx().docInBatch = new AtomicInteger(0);
                ctx().lock = new Object();
                ctx().batchLatch = new AtomicReference<>(new CountDownLatch(0));
                ctx().continuation = new DocumentImportContinuation(
                        ctx().db,
                        ctx().lexiconService,
                        logger,
                        ctx().batchLatch,
                        ctx().docInBatch,
                        ctx().lock,
                        BATCH_SIZE,
                        ctx().corpus,
                        ctx().corpusConfig,
                        ctx().importerNumber,
                        ctx().importId,
                        ctx().embeddingService
                );
                setPrivateField(ctx().importer, "continuation", ctx().continuation);
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    public static class CasViewSelectAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                if (ctx().casView != null && !ctx().casView.isBlank()) {
                    ctx().workingCas = ctx().originalCas.getView(ctx().casView);
                } else {
                    ctx().workingCas = ctx().originalCas;
                }
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    public static class DocumentCreateAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                var metadata = JCasUtil.selectSingle(ctx().workingCas, DocumentMetaData.class);
                String rawDocId = metadata.getDocumentId();
                String numericDocId = rawDocId != null ? rawDocId.replaceAll("\\D+", "") : "";
                if (numericDocId.isBlank()) {
                    numericDocId = String.valueOf(System.currentTimeMillis());
                }
                metadata.setDocumentId(numericDocId);
                ctx().filePath = "DUUI-CAS-Import-" + numericDocId + ".xmi";

                String language = metadata.getLanguage() == null ? "" : metadata.getLanguage();
                String title = metadata.getDocumentTitle() == null ? "" : metadata.getDocumentTitle();
                ctx().document = new Document(language, title, metadata.getDocumentId(), ctx().corpus.getId());
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    public static class DocumentDuplicateCheckAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.io();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                if (ctx().document == null) {
                    ctx().duplicateDocument = true;
                    return;
                }
                boolean exists = ctx().db.documentExists(ctx().corpus.getId(), ctx().document.getDocumentId());
                if (!exists) {
                    ctx().duplicateDocument = false;
                    return;
                }

                ctx().duplicateDocument = true;
                Document existingDoc = ctx().db.getDocumentByCorpusAndDocumentId(ctx().corpus.getId(), ctx().document.getDocumentId());
                if (existingDoc != null && !existingDoc.isPostProcessed()) {
                    ctx().continuation.postProccessDocument(existingDoc, ctx().corpus, ctx().filePath);
                }
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    public static class MimePayloadAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.io();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                if (ctx().duplicateDocument || ctx().document == null) {
                    return;
                }

                ctx().document.setMimeType(ctx().workingCas.getSofaMimeType());
                String mime = ctx().document.getMimeType();
                if (mime != null && (Objects.equals(mime, "application/pdf") || Objects.equals(mime, "pdf"))) {
                    ctx().document.setFullText("");
                    byte[] bytes = ctx().workingCas.getSofaDataStream().readAllBytes();
                    ctx().document.setDocumentData(bytes);
                } else if (mime != null && (mime.startsWith("image/") || Objects.equals(mime, "image/jpeg") || Objects.equals(mime, "image/png"))) {
                    ctx().document.setFullText("");
                    byte[] bytes = ctx().workingCas.getSofaDataStream().readAllBytes();
                    ctx().document.setDocumentData(bytes);
                } else {
                    ctx().document.setFullText(ctx().workingCas.getDocumentText());
                }
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    public static class MetadataTitleInfoAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            runPrivateDocumentStage("setMetadataTitleInfo", new Class<?>[]{Document.class, JCas.class, CorpusConfig.class},
                    new Object[]{ctx().document, ctx().workingCas, ctx().corpusConfig});
        }
    }

    public static class S3ArchiveAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.io();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                if (ctx().duplicateDocument || ctx().document == null) {
                    return;
                }
                if (!ctx().corpusConfig.getOther().isEnableS3Storage()) {
                    return;
                }
                if (ctx().sourcePath == null || ctx().sourcePath.isBlank()) {
                    return;
                }

                String fileExtension = StringUtils.getFileExtension(ctx().sourcePath);
                String contentType = StringUtils.getContentTypeByExtension(fileExtension);
                Object s3Service = getPrivateField(ctx().importer, "s3StorageService");
                String minioObjectName = (String) s3Service.getClass()
                        .getMethod("buildCasXmiObjectName", long.class, String.class)
                        .invoke(s3Service, ctx().corpus.getId(), ctx().document.getDocumentId());

                InputStream in = (InputStream) invokePrivate(ctx().importer, "openInputStreamBasedOnExtension",
                        new Class<?>[]{String.class}, new Object[]{ctx().sourcePath});
                if (in == null) {
                    return;
                }

                Method uploadMethod = null;
                for (Method method : s3Service.getClass().getMethods()) {
                    if (method.getName().equals("uploadCasInputStream") && method.getParameterCount() == 4) {
                        uploadMethod = method;
                        break;
                    }
                }
                if (uploadMethod != null) {
                    uploadMethod.invoke(s3Service, in, minioObjectName, contentType, new HashMap<>());
                }
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    public static class UceMetadataExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isUceMetadata()) {
                runPrivateDocumentStage("setUceMetadata", new Class<?>[]{Document.class, JCas.class, long.class},
                        new Object[]{ctx().document, ctx().workingCas, ctx().corpus.getId()});
            }
        }
    }

    public static class SentenceExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isSentence()) {
                runPrivateDocumentStage("setSentences", new Class<?>[]{Document.class, JCas.class},
                        new Object[]{ctx().document, ctx().workingCas});
            }
        }
    }

    public static class NamedEntityExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isNamedEntity()) {
                runPrivateDocumentStage("setNamedEntities", new Class<?>[]{Document.class, JCas.class},
                        new Object[]{ctx().document, ctx().workingCas});
            }
        }
    }

    public static class GeoNamesExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isNamedEntity() && ctx().corpusConfig.getAnnotations().isGeoNames()) {
                runPrivateDocumentStage("setGeoNames", new Class<?>[]{Document.class, JCas.class},
                        new Object[]{ctx().document, ctx().workingCas});
            }
        }
    }

    public static class SentimentExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isSentiment()) {
                runPrivateDocumentStage("setSentiments", new Class<?>[]{Document.class, JCas.class},
                        new Object[]{ctx().document, ctx().workingCas});
            }
        }
    }

    public static class EmotionExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isEmotion()) {
                runPrivateDocumentStage("setEmotions", new Class<?>[]{Document.class, JCas.class},
                        new Object[]{ctx().document, ctx().workingCas});
            }
        }
    }

    public static class LemmaExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isLemma()) {
                runPrivateDocumentStage("setLemmata", new Class<?>[]{Document.class, JCas.class},
                        new Object[]{ctx().document, ctx().workingCas});
            }
        }
    }

    public static class SemanticRoleExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isSrLink()) {
                runPrivateDocumentStage("setSemanticRoleLabels", new Class<?>[]{Document.class, JCas.class},
                        new Object[]{ctx().document, ctx().workingCas});
            }
        }
    }

    public static class TimeExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isTime()) {
                runPrivateDocumentStage("setTimes", new Class<?>[]{Document.class, JCas.class},
                        new Object[]{ctx().document, ctx().workingCas});
            }
        }
    }

    public static class TaxonomyExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().getTaxon().isAnnotated()) {
                runPrivateDocumentStage("setTaxonomy", new Class<?>[]{Document.class, JCas.class, CorpusConfig.class},
                        new Object[]{ctx().document, ctx().workingCas, ctx().corpusConfig});
            }
        }
    }

    public static class WikiLinkExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isWikipediaLink()) {
                runPrivateDocumentStage("setWikiLinks", new Class<?>[]{Document.class, JCas.class},
                        new Object[]{ctx().document, ctx().workingCas});
            }
        }
    }

    public static class NegationExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isCompleteNegation()) {
                try {
                    ctx().importer.setCompleteNegations(ctx().document, ctx().workingCas);
                } catch (Exception e) {
                    throw new AnalysisEngineProcessException(e);
                }
            }
        }
    }

    public static class UnifiedTopicExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isUnifiedTopic()) {
                runPrivateDocumentStage("setUnifiedTopic", new Class<?>[]{Document.class, JCas.class},
                        new Object[]{ctx().document, ctx().workingCas});
            }
        }
    }

    public static class LogicalLinksExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.io();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isLogicalLinks()) {
                runPrivateDocumentStage("setLogicLinks", new Class<?>[]{Document.class, JCas.class, long.class, String.class},
                        new Object[]{ctx().document, ctx().workingCas, ctx().corpus.getId(), ctx().filePath});
            }
        }
    }

    public static class PageExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            runPrivateDocumentStage("setPages", new Class<?>[]{Document.class, JCas.class, CorpusConfig.class},
                    new Object[]{ctx().document, ctx().workingCas, ctx().corpusConfig});
        }
    }

    public static class ImageExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.mixed();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            if (ctx().corpusConfig.getAnnotations().isImage()) {
                runPrivateDocumentStage("setImages", new Class<?>[]{Document.class, JCas.class},
                        new Object[]{ctx().document, ctx().workingCas});
            }
        }
    }

    public static class PermissionExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            runPrivateDocumentStage("setPermissions", new Class<?>[]{Document.class, JCas.class},
                    new Object[]{ctx().document, ctx().workingCas});
        }
    }

    public static class DocumentPersistAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.io();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                if (ctx().duplicateDocument || ctx().document == null) {
                    return;
                }
                ctx().db.saveDocument(ctx().document);
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    public static class DocumentPostProcessAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.io();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                if (ctx().duplicateDocument || ctx().document == null) {
                    return;
                }
                ctx().continuation.postProccessDocument(ctx().document, ctx().corpus, ctx().filePath);
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    public static class BatchPostProcessAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.io();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                ExceptionUtils.tryCatchLog(
                        () -> ctx().db.callLogicalLinksRefresh(),
                        ex -> logger.error("Batch logical links refresh failed.", ex)
                );
                ExceptionUtils.tryCatchLog(
                        () -> ctx().lexiconService.updateLexicon(false),
                        ex -> logger.error("Batch lexicon refresh failed.", ex)
                );
                ExceptionUtils.tryCatchLog(
                        () -> ctx().db.callGeonameLocationRefresh(),
                        ex -> logger.error("Batch geoname location refresh failed.", ex)
                );
                ctx().continuation.postProccessCorpus(ctx().corpus, ctx().corpusConfig);
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    public static class CorpusFinalizeAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.io();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                ExceptionUtils.tryCatchLog(
                        () -> ctx().db.callLogicalLinksRefresh(),
                        ex -> logger.error("Final logical links refresh failed.", ex)
                );
                ExceptionUtils.tryCatchLog(
                        () -> ctx().lexiconService.updateLexicon(false),
                        ex -> logger.error("Final lexicon refresh failed.", ex)
                );
                ExceptionUtils.tryCatchLog(
                        () -> ctx().db.callGeonameLocationRefresh(),
                        ex -> logger.error("Final geoname location refresh failed.", ex)
                );
                ctx().continuation.postProccessCorpus(ctx().corpus, ctx().corpusConfig);

                var uceImport = ctx().db.getUceImportByImportId(ctx().importId);
                if (uceImport != null) {
                    uceImport.setStatus(ImportStatus.FINISHED);
                    ctx().db.saveOrUpdateUceImport(uceImport);
                }
                ctx().finishedAt = System.currentTimeMillis();
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    public static class ImportLogAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.io();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                long duration = ctx().finishedAt > 0 ? ctx().finishedAt - ctx().startedAt : 0L;
                ctx().db.saveOrUpdateImportLog(
                        new ImportLog(
                                String.valueOf(ctx().importerNumber),
                                "DUUIAEImporter finished.",
                                LogStatus.FINISHED,
                                ctx().filePath,
                                ctx().importId,
                                duration
                        )
                );
            } catch (Exception e) {
                throw new AnalysisEngineProcessException(e);
            }
        }
    }

    private static void runPrivateDocumentStage(String method, Class<?>[] types, Object[] args) throws AnalysisEngineProcessException {
        try {
            if (ImportExecutionContextHolder.get().duplicateDocument || ImportExecutionContextHolder.get().document == null) {
                return;
            }
            invokePrivate(ImportExecutionContextHolder.get().importer, method, types, args);
        } catch (Exception e) {
            throw new AnalysisEngineProcessException(e);
        }
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(target);
    }
}
