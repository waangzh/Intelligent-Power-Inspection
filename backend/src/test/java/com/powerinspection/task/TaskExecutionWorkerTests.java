package com.powerinspection.task;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.powerinspection.robot.RobotBridgeExecutionClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskExecutionWorkerTests {
  @Mock private TaskExecutionLifecycleService lifecycle;
  @Mock private TaskExecutionControlService controls;
  @Mock private RobotEventIngestionService ingestion;
  @Mock private RobotBridgeExecutionClient bridge;
  @InjectMocks private TaskExecutionWorker worker;

  @Test
  void manualReconciliationExecutionIsNotAutomaticallyAdvanced() {
    TaskExecutionEntity execution = new TaskExecutionEntity();
    execution.setExecutionId("exec-1");
    execution.setStatus(TaskExecutionStatus.DISCONNECTED.name());
    execution.setRecoveryStatus(TaskExecutionStatus.STARTING.name());
    execution.setManualReconciliationRequired(true);
    when(lifecycle.nonTerminalExecutionIds()).thenReturn(List.of("exec-1"));
    when(lifecycle.findByExecutionId("exec-1")).thenReturn(execution);

    worker.runOnce();

    verifyNoInteractions(controls, ingestion, bridge);
  }
}
