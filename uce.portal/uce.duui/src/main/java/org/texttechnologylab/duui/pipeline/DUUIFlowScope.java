package org.texttechnologylab.duui.pipeline;

import org.texttechnologylab.duui.artifact.DUUIArtifactType;

public interface DUUIFlowScope<T> extends AutoCloseable {
    DUUIArtifactType<T> artifactType();

    DUUIStageScope<T> linear(String id);

    DUUIStageScope<T> parallel(String id);

    void addStage(DUUIStage<T> stage);

    DUUIPipelineScope pipeline();

    @Override
    void close();
}
