package org.texttechnologylab.duui.pipeline;

public final class DUUIGeneratorScope<T> extends DUUIAbstractFlowScope<T> {
    private final DUUIGenerator<T> generator;

    private DUUIGeneratorScope(DUUIPipelineScope pipeline, DUUIGenerator<T> generator) {
        super(pipeline, generator.outputType());
        this.generator = generator;
        pipeline.registerGenerator(generator);
    }

    static <T> DUUIGeneratorScope<T> open(DUUIPipelineScope pipeline, DUUIGenerator<T> generator) {
        return new DUUIGeneratorScope<>(pipeline, generator);
    }

    public DUUIGenerator<T> generator() {
        return generator;
    }
}
