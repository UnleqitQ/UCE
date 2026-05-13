package org.texttechnologylab.duui.pipeline;

import org.texttechnologylab.duui.artifact.DUUIArtifact;
import org.texttechnologylab.duui.artifact.DUUIArtifactEmitter;
import org.texttechnologylab.duui.artifact.DUUIArtifactType;

public interface DUUIGenerator<T> {
    DUUIArtifactType<T> outputType();

    void generate(DUUIArtifactEmitter<T> emitter) throws Exception;

    default DUUIArtifact<T> artifact(T payload) {
        return DUUIArtifact.of(payload, outputType());
    }

    default DUUIGeneratorScope<T> open(DUUIPipelineScope pipeline) {
        return pipeline.add(this);
    }
}
