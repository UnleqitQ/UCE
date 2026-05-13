package org.texttechnologylab.duui.pipeline;

import org.texttechnologylab.duui.artifact.DUUIArtifact;
import org.texttechnologylab.duui.orchestration.DUUIWorker;

final class DUUIForkComponent<P, C> implements DUUIComponent<P> {
    private final DUUIFork<P, C> fork;

    DUUIForkComponent(DUUIFork<P, C> fork) {
        this.fork = fork;
    }

    @Override
    public DUUIArtifact<P> process(DUUIArtifact<P> artifact) throws Exception {
        fork.fork(artifact, emitted -> DUUIWorker.current().requireCurrentTask().context().emit(emitted));
        return artifact;
    }
}
