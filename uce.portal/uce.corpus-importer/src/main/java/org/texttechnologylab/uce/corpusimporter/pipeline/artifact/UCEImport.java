package org.texttechnologylab.uce.corpusimporter.pipeline.artifact;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class UCEImport {
    private final String importId;
    private final List<Path> importRoots;
    private final int importerNumber;
    private final int requestedParallelism;
    private final String casView;

    public UCEImport(String importId, List<Path> importRoots, int importerNumber, int requestedParallelism, String casView) {
        this.importId = Objects.requireNonNull(importId, "importId");
        this.importRoots = Collections.unmodifiableList(new ArrayList<>(importRoots == null ? List.of() : importRoots));
        this.importerNumber = importerNumber;
        this.requestedParallelism = Math.max(1, requestedParallelism);
        this.casView = casView;
    }

    public String importId() { return importId; }
    public List<Path> importRoots() { return importRoots; }
    public int importerNumber() { return importerNumber; }
    public int requestedParallelism() { return requestedParallelism; }
    public String casView() { return casView; }
}
