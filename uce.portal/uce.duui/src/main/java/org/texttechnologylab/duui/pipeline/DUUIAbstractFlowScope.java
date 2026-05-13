package org.texttechnologylab.duui.pipeline;

import org.texttechnologylab.duui.artifact.DUUIArtifactType;

abstract class DUUIAbstractFlowScope<T> implements DUUIFlowScope<T> {
    private final DUUIPipelineScope pipeline;
    private final DUUIArtifactType<T> artifactType;
    private boolean closed;

    DUUIAbstractFlowScope(DUUIPipelineScope pipeline, DUUIArtifactType<T> artifactType) {
        this.pipeline = pipeline;
        this.artifactType = artifactType;
        this.pipeline.ensureCheckpoint(artifactType);
    }

    @Override
    public DUUIArtifactType<T> artifactType() {
        return artifactType;
    }

    @Override
    public DUUIStageScope<T> linear(String id) {
        return new DUUIStageScope<>(this, id, DUUIStageType.LINEAR);
    }

    @Override
    public DUUIStageScope<T> parallel(String id) {
        return new DUUIStageScope<>(this, id, DUUIStageType.PARALLEL);
    }

    @Override
    public void addStage(DUUIStage<T> stage) {
        pipeline.addStage(artifactType, stage);
    }

    @Override
    public DUUIPipelineScope pipeline() {
        return pipeline;
    }

    @Override
    public void close() {
        if (!closed) {
            onClose();
            closed = true;
        }
    }

    protected void onClose() {
    }
}
