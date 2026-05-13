package org.texttechnologylab.duui.pipeline.builder;

import org.texttechnologylab.duui.exception.DUUIFailurePolicy;
import org.texttechnologylab.duui.pipeline.DUUICheckpoint;
import org.texttechnologylab.duui.pipeline.DUUIPipeline;
import org.texttechnologylab.duui.util.DUUIScope;

import java.util.ArrayList;
import java.util.List;

public final class DUUIPipelineBuilder {
    private final String id;
    private final List<DUUICheckpoint<?>> checkpoints = new ArrayList<>();
    private DUUIFailurePolicy failurePolicy;

    private DUUIPipelineBuilder(String id) { this.id = id; }

    public static DUUIPipelineBuilder create(String id) { return new DUUIPipelineBuilder(id); }

    public DUUIPipelineBuilder checkpoint(DUUICheckpoint<?> checkpoint) {
        checkpoints.add(checkpoint);
        return this;
    }

    public <T> DUUIScope<DUUICheckpointBuilder<T>> checkpoint(String id, Class<T> payloadType) {
        return DUUIScope.of(DUUICheckpointBuilder.create(id, payloadType), builder -> checkpoint(builder.build()));
    }

    public DUUIPipelineBuilder failurePolicy(DUUIFailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
        return this;
    }

    public DUUIPipeline build() {
        DUUIPipeline.Builder builder = DUUIPipeline.builder(id).failurePolicy(failurePolicy);
        checkpoints.forEach(builder::checkpoint);
        return builder.build();
    }
}
