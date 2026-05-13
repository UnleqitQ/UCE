package org.texttechnologylab.duui.orchestration;

import junit.framework.TestCase;
import org.texttechnologylab.duui.artifact.DUUIArtifact;
import org.texttechnologylab.duui.artifact.DUUIArtifactType;
import org.texttechnologylab.duui.pipeline.DUUICheckpoint;
import org.texttechnologylab.duui.pipeline.DUUIComponents;
import org.texttechnologylab.duui.pipeline.DUUIExecutor;
import org.texttechnologylab.duui.pipeline.DUUIFork;
import org.texttechnologylab.duui.pipeline.DUUIPipeline;
import org.texttechnologylab.duui.pipeline.DUUIStage;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class DUUIWorkerTaskExecutionTest extends TestCase {
    public void testCurrentWorkerFailsOutsideManagedExecution() {
        DUUIWorkerRegistry.unregisterCurrentThread();
        try {
            DUUIWorker.current();
            fail("Expected unmanaged worker lookup to fail.");
        } catch (DUUIFrameworkStateException expected) {
            assertTrue(expected.getMessage().contains("No DUUIWorker"));
        }
    }

    public void testPlatformTaskBindsWorkerAndClearsTask() {
        DUUIExecutor executor = new DUUIExecutor("platform-test");
        AtomicReference<DUUITask<String>> taskRef = new AtomicReference<>();
        DUUITask<String> task = executor.task(new DUUIExecutionContext(), () -> {
            DUUIWorker worker = DUUIWorker.current();
            assertEquals("platform-test", worker.orchestratorId());
            assertEquals(taskRef.get(), worker.currentTask());
            return worker.kind().name();
        });
        taskRef.set(task);

        executor.submit(task, DUUIDispatchPolicy.cpu());

        assertEquals(DUUIWorkerKind.PLATFORM.name(), task.await());
    }

    public void testVirtualTaskBindsWorkerAndClearsTask() {
        DUUIExecutor executor = new DUUIExecutor("virtual-test");
        AtomicReference<DUUITask<String>> taskRef = new AtomicReference<>();
        DUUITask<String> task = executor.task(new DUUIExecutionContext(), () -> {
            DUUIWorker worker = DUUIWorker.current();
            assertEquals("virtual-test", worker.orchestratorId());
            assertEquals(taskRef.get(), worker.currentTask());
            return worker.kind().name();
        });
        taskRef.set(task);

        executor.submit(task, DUUIDispatchPolicy.io());

        assertEquals(DUUIWorkerKind.VIRTUAL.name(), task.await());
    }

    public void testParallelStageEmissionsRouteToDownstreamCheckpoint() {
        DUUIArtifactType<String> rootType = DUUIArtifactType.of("test/root");
        DUUIArtifactType<Integer> childType = DUUIArtifactType.of("test/child");
        DUUIFork<String, Integer> firstFork = new DUUIFork<>() {
            @Override
            public DUUIArtifactType<String> inputType() { return rootType; }
            @Override
            public DUUIArtifactType<Integer> outputType() { return childType; }
            @Override
            public void fork(DUUIArtifact<String> artifact, org.texttechnologylab.duui.artifact.DUUIArtifactEmitter<Integer> emitter) throws Exception {
                emitter.emit(artifact.childArtifact(1, childType));
            }
        };
        DUUIFork<String, Integer> secondFork = new DUUIFork<>() {
            @Override
            public DUUIArtifactType<String> inputType() { return rootType; }
            @Override
            public DUUIArtifactType<Integer> outputType() { return childType; }
            @Override
            public void fork(DUUIArtifact<String> artifact, org.texttechnologylab.duui.artifact.DUUIArtifactEmitter<Integer> emitter) throws Exception {
                emitter.emit(artifact.childArtifact(2, childType));
            }
        };
        DUUICheckpoint<String> root = DUUICheckpoint.<String>builder("root", rootType)
                .stage(DUUIStage.parallel("fanout", List.of(
                        DUUIComponents.fork(firstFork),
                        DUUIComponents.fork(secondFork)
                )))
                .build();
        DUUICheckpoint<Integer> child = DUUICheckpoint.<Integer>builder("child", childType)
                .component("identity", artifact -> artifact)
                .build();
        DUUIPipeline pipeline = DUUIPipeline.builder("test").checkpoint(root).checkpoint(child).build();

        DUUIOrchestrationResult result = new DUUIOrchestrator(pipeline).run(DUUIArtifact.of("root", rootType));

        assertEquals(0, result.unroutableArtifacts().size());
        assertEquals(3, result.results().size());
    }
}
