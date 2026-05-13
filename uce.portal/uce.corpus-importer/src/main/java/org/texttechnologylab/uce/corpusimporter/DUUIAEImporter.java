package org.texttechnologylab.uce.corpusimporter;

import com.google.gson.Gson;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.texttechnologylab.duui.artifact.DUUIArtifact;
import org.texttechnologylab.duui.artifact.DUUIArtifactType;
import org.texttechnologylab.duui.orchestration.DUUIDispatchMode;
import org.texttechnologylab.duui.orchestration.DUUIDispatchPolicy;
import org.texttechnologylab.duui.orchestration.DUUIExecutionContext;
import org.texttechnologylab.duui.orchestration.DUUIOrchestrator;
import org.texttechnologylab.duui.pipeline.DUUICheckpoint;
import org.texttechnologylab.duui.pipeline.DUUIComponent;
import org.texttechnologylab.duui.pipeline.DUUIComponents;
import org.texttechnologylab.duui.pipeline.DUUIAdapter;
import org.texttechnologylab.duui.pipeline.DUUIExecutor;
import org.texttechnologylab.duui.pipeline.DUUIPipeline;
import org.texttechnologylab.duui.pipeline.DUUIStage;
import org.texttechnologylab.duui.pipeline.DUUIStageType;
import org.texttechnologylab.uce.common.config.CommonConfig;
import org.texttechnologylab.uce.common.config.CorpusConfig;
import org.texttechnologylab.uce.common.config.SpringConfig;
import org.texttechnologylab.uce.common.exceptions.ExceptionUtils;
import org.texttechnologylab.uce.common.models.corpus.Corpus;
import org.texttechnologylab.uce.common.models.corpus.Document;
import org.texttechnologylab.uce.common.models.imp.ImportLog;
import org.texttechnologylab.uce.common.models.imp.ImportStatus;
import org.texttechnologylab.uce.common.models.imp.LogStatus;
import org.texttechnologylab.uce.common.security.DocumentAccessManager;
import org.texttechnologylab.uce.common.services.AgeGraphService;
import org.texttechnologylab.uce.common.services.EmbeddingService;
import org.texttechnologylab.uce.common.services.LexiconService;
import org.texttechnologylab.uce.common.services.PostgresqlDataInterface_Impl;
import org.texttechnologylab.uce.common.utils.StringUtils;
import org.texttechnologylab.uce.common.utils.SystemStatus;
import org.texttechnologylab.uce.corpusimporter.pipeline.artifact.UCECorpus;
import org.texttechnologylab.uce.corpusimporter.pipeline.artifact.UCEDocument;
import org.texttechnologylab.uce.corpusimporter.pipeline.artifact.UCEImport;
import org.texttechnologylab.annotation.domain.Association;
import org.texttechnologylab.annotation.domain.Domain;
import org.texttechnologylab.annotation.domain.Equivalence;
import org.texttechnologylab.annotation.domain.Membership;
import org.texttechnologylab.annotation.domain.Reference;
import org.texttechnologylab.annotation.domain.Sequence;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DUUIAEImporter extends JCasAnnotator_ImplBase {
    private static final Logger logger = LogManager.getLogger(DUUIAEImporter.class);
    private static final Gson gson = new Gson();
    private static final int BATCH_SIZE = 2000;
    private static final Path EXTERNAL_CORPUS_CONFIG_PATH = Path.of("/app/config/UCECorpusConfigEmpty.json");
    private static final Path LEGACY_CORPUS_CONFIG_PATH = Path.of("uce.corpus-importer/src/main/resources/UCECorpusConfigEmpty.json");
    private static final DUUIArtifactType<UCEImport> UCE_IMPORT_ARTIFACT = DUUIArtifactType.of("uce/import");
    private static final DUUIArtifactType<UCECorpus> UCE_CORPUS_ARTIFACT = DUUIArtifactType.of("uce/corpus");
    private static final DUUIArtifactType<UCEDocument> UCE_DOCUMENT_ARTIFACT = DUUIArtifactType.of("uce/document");

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

    public static final String PARAM_ENABLE_DOMAIN_GRAPH_IMPORT = "enableDomainGraphImport";
    @ConfigurationParameter(name = PARAM_ENABLE_DOMAIN_GRAPH_IMPORT, mandatory = false, defaultValue = "false")
    private boolean enableDomainGraphImport;

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
        ctx.enableDomainGraphImport = enableDomainGraphImport;
        ctx.originalCas = jCas;
        ctx.workingCas = jCas;
        ctx.springContext = springContext;
        ctx.filePath = "DUUI-CAS-Import-" + System.currentTimeMillis() + ".xmi";

        try {
            executeStageGraph(jCas, ctx);
        } catch (Exception e) {
            throw new AnalysisEngineProcessException(e);
        } finally {
            closeGuardQuietly(ctx);
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

    private static void executeStageGraph(JCas jCas, ImportExecutionContext ctx) throws Exception {
        DUUIExecutionContext executionContext = new DUUIExecutionContext();
        executionContext.put(ImportExecutionContext.class, ctx);
        try (DUUIExecutor executor = new DUUIExecutor(ctx.importId)) {
            DUUIOrchestrator orchestrator = new DUUIOrchestrator(
                    ctx.importId,
                    buildPipeline(ctx),
                    null,
                    null,
                    executor,
                    null
            );
            DUUIArtifact<UCEImport> root = DUUIArtifact.of(new UCEImport(
                    ctx.importId,
                    ctx.sourcePath == null || ctx.sourcePath.isBlank() ? List.of(Path.of(".")) : List.of(Path.of(ctx.sourcePath)),
                    ctx.importerNumber,
                    ctx.numThreads,
                    ctx.casView
            ), UCE_IMPORT_ARTIFACT);
            orchestrator.run(List.of(root), executionContext);
        }
    }

    private static ImportExecutionContext currentImportContext() {
        return org.texttechnologylab.duui.orchestration.DUUIWorker.current().requireCurrentTask().context().require(ImportExecutionContext.class);
    }

    private static DUUIPipeline buildPipeline(ImportExecutionContext ctx) {
        return DUUIPipeline.builder("uce-importer-" + ctx.importId)
                .checkpoint(DUUICheckpoint.builder("uce-import", UCE_IMPORT_ARTIFACT)
                        .stage(stage("ImportInitAE", new ImportInitAE()))
                        .stage(stage("CorpusConfigLoadAE", new CorpusConfigLoadAE()))
                        .stage(stage("CorpusEnsureAE", new CorpusEnsureAE()))
                        .stage(stage("UceMetadataFilterLoadAE", new UceMetadataFilterLoadAE()))
                        .stage(DUUIStage.of("EmitCorpusArtifact", emitCorpus()))
                        .build())
                .checkpoint(DUUICheckpoint.builder("uce-corpus", UCE_CORPUS_ARTIFACT)
                        .stage(DUUIStage.of("EmitDocumentArtifact", emitDocument()))
                        .build())
                .checkpoint(DUUICheckpoint.builder("uce-document", UCE_DOCUMENT_ARTIFACT)
                        .stage(stage("CasViewSelectAE", new CasViewSelectAE()))
                        .stage(stage("DomainGraphExtractAE", new DomainGraphExtractAE()))
                        .stage(stage("DocumentCreateAE", new DocumentCreateAE()))
                        .stage(stage("DocumentDuplicateCheckAE", new DocumentDuplicateCheckAE()))
                        .stage(stage("MimePayloadAE", new MimePayloadAE()))
                        .stage(stage("MetadataTitleInfoAE", new MetadataTitleInfoAE()))
                        .stage(stage("S3ArchiveAE", new S3ArchiveAE()))
                        .stage(groupedStage("CoreDocumentExtraction", DispatchPolicy.cpu(), List.of(
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
                        )))
                        .stage(stage("GeoNamesExtractAE", new GeoNamesExtractAE()))
                        .stage(stage("TaxonomyExtractAE", new TaxonomyExtractAE()))
                        .stage(stage("PageExtractAE", new PageExtractAE()))
                        .stage(groupedStage("SecondaryDocumentExtraction", DispatchPolicy.mixed(), List.of(
                                new ImageExtractAE(),
                                new LogicalLinksExtractAE()
                        )))
                        .stage(stage("DocumentPersistAE", new DocumentPersistAE()))
                        .stage(stage("DomainGraphPersistAE", new DomainGraphPersistAE()))
                        .stage(stage("DocumentPostProcessAE", new DocumentPostProcessAE()))
                        .stage(stage("BatchPostProcessAE", new BatchPostProcessAE()))
                        .stage(stage("CorpusFinalizeAE", new CorpusFinalizeAE()))
                        .stage(stage("ImportLogAE", new ImportLogAE()))
                        .build())
                .build();
    }

    private static <T> DUUIStage<T> stage(String id, StageAE stage) {
        return new DUUIStage<>(id, id, stageComponent(stage), id, null);
    }

    private static DUUIStage<UCEDocument> groupedStage(String id, DispatchPolicy policy, List<StageAE> stages) {
        List<DUUIComponent<UCEDocument>> components = stages.stream()
                .map(DUUIAEImporter::<UCEDocument>stageComponent)
                .toList();
        return new DUUIStage<>(
                id,
                id,
                DUUIStageType.LINEAR,
                components,
                id,
                toDUUIDispatchPolicy(policy),
                null
        );
    }

    private static <T> DUUIComponent<T> stageComponent(StageAE stage) {
        return artifact -> {
            stage.process(currentImportContext().originalCas);
            return artifact;
        };
    }

    private static DUUIComponent<UCEImport> emitCorpus() {
        return DUUIComponents.adapter(new DUUIAdapter<UCEImport, UCECorpus>() {
            @Override
            public DUUIArtifactType<UCEImport> inputType() { return UCE_IMPORT_ARTIFACT; }

            @Override
            public DUUIArtifactType<UCECorpus> outputType() { return UCE_CORPUS_ARTIFACT; }

            @Override
            public DUUIArtifact<UCECorpus> adapt(DUUIArtifact<UCEImport> artifact) {
                ImportExecutionContext ctx = currentImportContext();
                UCEImport payload = artifact.payload();
                Path root = payload.importRoots().isEmpty() ? Path.of(".") : payload.importRoots().get(0);
                UCECorpus corpus = new UCECorpus(ctx.importId, root);
                corpus.corpusConfig(ctx.corpusConfig);
                corpus.corpus(ctx.corpus);
                return artifact.childArtifact(corpus, UCE_CORPUS_ARTIFACT);
            }
        });
    }

    private static DUUIComponent<UCECorpus> emitDocument() {
        return DUUIComponents.adapter(new DUUIAdapter<UCECorpus, UCEDocument>() {
            @Override
            public DUUIArtifactType<UCECorpus> inputType() { return UCE_CORPUS_ARTIFACT; }

            @Override
            public DUUIArtifactType<UCEDocument> outputType() { return UCE_DOCUMENT_ARTIFACT; }

            @Override
            public DUUIArtifact<UCEDocument> adapt(DUUIArtifact<UCECorpus> artifact) {
                ImportExecutionContext ctx = currentImportContext();
                UCEDocument document = new UCEDocument(ctx.importId, ctx.corpus.getId(), Path.of(ctx.filePath));
                document.originalCas(ctx.originalCas);
                document.selectedCas(ctx.workingCas);
                return artifact.childArtifact(document, UCE_DOCUMENT_ARTIFACT);
            }
        });
    }

    private static DUUIDispatchPolicy toDUUIDispatchPolicy(DispatchPolicy policy) {
        if (policy == null || policy.caller) {
            return DUUIDispatchPolicy.CALLER;
        }
        DUUIDispatchMode mode = switch (policy.mode == null ? DispatchMode.MIXED : policy.mode) {
            case IO -> DUUIDispatchMode.IO;
            case CPU -> DUUIDispatchMode.CPU;
            case MIXED -> DUUIDispatchMode.MIXED;
        };
        return DUUIDispatchPolicy.of(mode, policy.parallelism);
    }

    enum DispatchMode {
        IO,
        CPU,
        MIXED
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
        boolean enableDomainGraphImport;
        long startedAt = System.currentTimeMillis();
        long finishedAt;
        String filePath;
        JCas originalCas;
        JCas workingCas;
        AnnotationConfigApplicationContext springContext;
        DocumentAccessManager accessManager;
        AutoCloseable adminGuard;
        PostgresqlDataInterface_Impl db;
        AgeGraphService ageGraphService;
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
        DomainGraphBuffer domainGraphBuffer;
    }

    static abstract class StageAE extends JCasAnnotator_ImplBase {
        protected ImportExecutionContext ctx() { return currentImportContext(); }
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
                ctx().ageGraphService = ctx().springContext.getBean(AgeGraphService.class);
                ctx().lexiconService = ctx().springContext.getBean(LexiconService.class);
                ctx().embeddingService = ctx().springContext.getBean(EmbeddingService.class);
                ctx().accessManager = ctx().springContext.getBean(DocumentAccessManager.class);
                ctx().adminGuard = ctx().accessManager.asAdmin();

                var commonConfig = new CommonConfig();
                ExceptionUtils.tryCatchLog(
                        () -> SystemStatus.executeExternalDatabaseScripts(commonConfig.getDatabaseScriptsLocation(), ctx().db),
                        (ex) -> logger.warn("Couldn't execute external DB scripts.", ex));

                var uceImport = new org.texttechnologylab.uce.common.models.imp.UCEImport(ctx().importId, "import from DUUIAEImporter", ImportStatus.STARTING);
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

    public static class DomainGraphExtractAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.cpu();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                if (!ctx().enableDomainGraphImport) {
                    return;
                }

                Map<Domain, String> uidByDomain = new IdentityHashMap<>();
                List<DomainGraphNode> nodes = new ArrayList<>();
                for (Domain domain : JCasUtil.select(ctx().workingCas, Domain.class)) {
                    String id = trimToNull(domain.getId());
                    if (id == null) {
                        logger.warn("Skipping Domain FS without id: {}", domain.getType().getName());
                        continue;
                    }
                    String uid = domainUid(ctx().corpus.getId(), id);
                    uidByDomain.put(domain, uid);
                    nodes.add(new DomainGraphNode(
                            uid,
                            domain.getType().getName(),
                            domain.getName(),
                            domain.getUri(),
                            domain.getMetadata(),
                            gson.toJson(serializeFeatures(domain))
                    ));
                }

                List<DomainGraphEdge> edges = new ArrayList<>();
                for (Association association : JCasUtil.select(ctx().workingCas, Association.class)) {
                    AssociationEndpoints endpoints = resolveAssociationEndpoints(association);
                    if (endpoints == null || endpoints.left() == null || endpoints.right() == null) {
                        logger.warn("Skipping Association FS without resolvable endpoints: {}", association.getType().getName());
                        continue;
                    }
                    String leftUid = uidByDomain.get(endpoints.left());
                    String rightUid = uidByDomain.get(endpoints.right());
                    if (leftUid == null || rightUid == null) {
                        logger.warn("Skipping Association FS with endpoints missing imported Domain ids: {}", association.getType().getName());
                        continue;
                    }
                    String uid = trimToNull(association.getId());
                    if (uid == null) {
                        uid = derivedAssociationUid(ctx().corpus.getId(), association, leftUid, rightUid, endpoints.qualifier());
                    } else {
                        uid = "corpus:" + ctx().corpus.getId() + ":association:" + uid;
                    }
                    edges.add(new DomainGraphEdge(
                            uid,
                            association.getType().getName(),
                            leftUid,
                            rightUid,
                            association.getName(),
                            association.getMetadata(),
                            gson.toJson(serializeFeatures(association))
                    ));
                }

                ctx().domainGraphBuffer = new DomainGraphBuffer(nodes, edges);
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

    public static class DomainGraphPersistAE extends StageAE {
        @Override
        protected DispatchPolicy dispatchPolicy() {
            return DispatchPolicy.io();
        }

        @Override
        public void process(JCas jCas) throws AnalysisEngineProcessException {
            try {
                if (!ctx().enableDomainGraphImport || ctx().duplicateDocument || ctx().document == null || ctx().domainGraphBuffer == null) {
                    return;
                }
                if (ctx().document.getId() <= 0) {
                    throw new IllegalStateException("Domain graph import requires a persisted document id.");
                }

                ctx().ageGraphService.ensureGraph();
                for (DomainGraphNode node : ctx().domainGraphBuffer.nodes()) {
                    ctx().ageGraphService.upsertDomainNode(new AgeGraphService.DomainNode(
                            node.uid(),
                            node.uimaType(),
                            ctx().corpus.getId(),
                            ctx().document.getId(),
                            node.name(),
                            node.uri(),
                            node.metadata(),
                            node.featuresJson()
                    ));
                }
                for (DomainGraphEdge edge : ctx().domainGraphBuffer.edges()) {
                    ctx().ageGraphService.upsertAssociationEdge(new AgeGraphService.AssociationEdge(
                            edge.uid(),
                            edge.uimaType(),
                            ctx().corpus.getId(),
                            ctx().document.getId(),
                            edge.leftUid(),
                            edge.rightUid(),
                            edge.name(),
                            edge.metadata(),
                            edge.featuresJson()
                    ));
                }
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

    private record DomainGraphBuffer(List<DomainGraphNode> nodes, List<DomainGraphEdge> edges) {
    }

    private record DomainGraphNode(
            String uid,
            String uimaType,
            String name,
            String uri,
            String metadata,
            String featuresJson
    ) {
    }

    private record DomainGraphEdge(
            String uid,
            String uimaType,
            String leftUid,
            String rightUid,
            String name,
            String metadata,
            String featuresJson
    ) {
    }

    private record AssociationEndpoints(Domain left, Domain right, String qualifier) {
    }

    private static String domainUid(long corpusId, String domainId) {
        return "corpus:" + corpusId + ":domain:" + domainId;
    }

    private static String derivedAssociationUid(long corpusId, Association association, String leftUid, String rightUid, String qualifier) {
        String basis = corpusId + "|" + association.getType().getName() + "|" + leftUid + "|" + rightUid + "|" + nullToEmpty(qualifier);
        return "corpus:" + corpusId + ":association:" + UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8));
    }

    private static AssociationEndpoints resolveAssociationEndpoints(Association association) {
        if (association instanceof Membership membership) {
            return new AssociationEndpoints(membership.getWhole(), membership.getPart(), String.valueOf(membership.getOrder()));
        }
        if (association instanceof Sequence sequence) {
            return new AssociationEndpoints(sequence.getPrevious(), sequence.getNext(), String.valueOf(sequence.getOrder()));
        }
        if (association instanceof Reference reference) {
            return new AssociationEndpoints(reference.getContext(), reference.getReferent(), reference.getRole());
        }
        if (association instanceof Equivalence equivalence) {
            return new AssociationEndpoints(equivalence.getOne(), equivalence.getOther(), equivalence.getBasis());
        }
        return null;
    }

    private static Map<String, Object> serializeFeatures(FeatureStructure fs) {
        Map<String, Object> values = new HashMap<>();
        for (Feature feature : fs.getType().getFeatures()) {
            String name = feature.getShortName();
            try {
                if (feature.getRange().isPrimitive()) {
                    values.put(name, fs.getFeatureValueAsString(feature));
                    continue;
                }
                FeatureStructure value = fs.getFeatureValue(feature);
                if (value instanceof Domain domain) {
                    values.put(name, domain.getId());
                } else if (value != null) {
                    values.put(name, value.getType().getName());
                }
            } catch (Exception e) {
                values.put(name, null);
            }
        }
        return values;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void runPrivateDocumentStage(String method, Class<?>[] types, Object[] args) throws AnalysisEngineProcessException {
        try {
            ImportExecutionContext ctx = currentImportContext();
            if (ctx.duplicateDocument || ctx.document == null) {
                return;
            }
            invokePrivate(ctx.importer, method, types, args);
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
