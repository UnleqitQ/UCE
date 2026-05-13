package org.texttechnologylab.duui.pipeline;

import org.texttechnologylab.duui.artifact.DUUIArtifact;

@FunctionalInterface
public interface DUUIComponent<T> {
    DUUIArtifact<T> process(DUUIArtifact<T> artifact) throws Exception;
}
