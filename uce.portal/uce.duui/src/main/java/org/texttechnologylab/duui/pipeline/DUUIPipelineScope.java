package org.texttechnologylab.duui.pipeline;

import org.texttechnologylab.duui.artifact.DUUIArtifactType;
import org.texttechnologylab.duui.exception.DUUIFailurePolicy;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DUUIPipelineScope implements AutoCloseable {
    private final DUUIPipeline.Builder builder;
    private final Map<DUUIArtifactType<?>, DUUICheckpoint.Builder<?>> checkpoints = new LinkedHashMap<>();
    private DUUIPipeline pipeline;
    private boolean closed;

    private DUUIPipelineScope(String id) {
        this.builder = DUUIPipeline.builder(id);
    }

    public static DUUIPipelineScope open(String id) {
        return new DUUIPipelineScope(id);
    }

    public DUUIPipelineScope failurePolicy(DUUIFailurePolicy failurePolicy) {
        builder.failurePolicy(failurePolicy);
        return this;
    }

    public <T> DUUIGeneratorScope<T> add(DUUIGenerator<T> generator) {
        return DUUIGeneratorScope.open(this, generator);
    }

    <T> void registerGenerator(DUUIGenerator<T> generator) {
        builder.generator(generator);
        checkpoint(generator.outputType());
    }

    <T> void addStage(DUUIArtifactType<T> artifactType, DUUIStage<T> stage) {
        checkpoint(artifactType).stage(stage);
    }

    <T> void ensureCheckpoint(DUUIArtifactType<T> artifactType) {
        checkpoint(artifactType);
    }

    @SuppressWarnings("unchecked")
    private <T> DUUICheckpoint.Builder<T> checkpoint(DUUIArtifactType<T> artifactType) {
        return (DUUICheckpoint.Builder<T>) checkpoints.computeIfAbsent(
                artifactType,
                type -> DUUICheckpoint.builder(type.id(), artifactType)
        );
    }

    public DUUIPipeline build() {
        if (pipeline == null) {
            for (DUUICheckpoint.Builder<?> checkpoint : checkpoints.values()) {
                builder.checkpoint(checkpoint.build());
            }
            pipeline = builder.build();
        }
        return pipeline;
    }

    @Override
    public void close() {
        if (!closed) {
            build();
            closed = true;
        }
    }
}
