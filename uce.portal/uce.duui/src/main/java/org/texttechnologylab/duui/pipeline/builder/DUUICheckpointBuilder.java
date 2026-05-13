package org.texttechnologylab.duui.pipeline.builder;

import org.texttechnologylab.duui.exception.DUUIFailurePolicy;
import org.texttechnologylab.duui.pipeline.DUUICheckpoint;
import org.texttechnologylab.duui.pipeline.DUUICheckpointConfig;
import org.texttechnologylab.duui.pipeline.DUUIComponent;
import org.texttechnologylab.duui.pipeline.DUUIStage;
import org.texttechnologylab.duui.util.DUUIScope;

import java.util.ArrayList;
import java.util.List;

public final class DUUICheckpointBuilder<T> {
    private final String id;
    private final Class<T> payloadType;
    private final List<DUUIStage<T>> stages = new ArrayList<>();
    private DUUICheckpointConfig config;
    private DUUIFailurePolicy failurePolicy;

    private DUUICheckpointBuilder(String id, Class<T> payloadType) {
        this.id = id;
        this.payloadType = payloadType;
    }

    public static <T> DUUICheckpointBuilder<T> create(String id, Class<T> payloadType) {
        return new DUUICheckpointBuilder<>(id, payloadType);
    }

    public DUUICheckpointBuilder<T> stage(DUUIStage<T> stage) {
        stages.add(stage);
        return this;
    }

    public DUUICheckpointBuilder<T> component(String id, DUUIComponent<T> component) {
        stages.add(DUUIStage.of(id, component));
        return this;
    }

    public DUUIScope<DUUIStageBuilder<T>> stage(String id) {
        return DUUIScope.of(DUUIStageBuilder.create(id), builder -> stage(builder.build()));
    }

    public DUUICheckpointBuilder<T> config(DUUICheckpointConfig config) {
        this.config = config;
        return this;
    }

    public DUUICheckpointBuilder<T> failurePolicy(DUUIFailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
        return this;
    }

    public DUUICheckpoint<T> build() {
        DUUICheckpoint.Builder<T> builder = DUUICheckpoint.builder(id, payloadType)
                .config(config)
                .failurePolicy(failurePolicy);
        stages.forEach(builder::stage);
        return builder.build();
    }
}
