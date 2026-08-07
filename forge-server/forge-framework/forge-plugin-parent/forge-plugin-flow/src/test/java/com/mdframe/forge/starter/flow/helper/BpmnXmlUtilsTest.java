package com.mdframe.forge.starter.flow.helper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmnXmlUtilsTest {

    @Test
    void shouldRemoveDuplicateUnreferencedSequenceFlows() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI">
                  <bpmn:process id="leave_multi" isExecutable="true">
                    <bpmn:startEvent id="startEvent">
                      <bpmn:outgoing>Flow_0fnqi4c</bpmn:outgoing>
                    </bpmn:startEvent>
                    <bpmn:userTask id="deptApprove">
                      <bpmn:incoming>Flow_0fnqi4c</bpmn:incoming>
                      <bpmn:outgoing>Flow_09gi1bi</bpmn:outgoing>
                    </bpmn:userTask>
                    <bpmn:userTask id="hrApprove">
                      <bpmn:incoming>Flow_09gi1bi</bpmn:incoming>
                      <bpmn:outgoing>Flow_1bw7xaf</bpmn:outgoing>
                    </bpmn:userTask>
                    <bpmn:endEvent id="endEvent">
                      <bpmn:incoming>Flow_1bw7xaf</bpmn:incoming>
                    </bpmn:endEvent>
                    <bpmn:sequenceFlow id="flow1" sourceRef="startEvent" targetRef="deptApprove" />
                    <bpmn:sequenceFlow id="flow2" sourceRef="deptApprove" targetRef="hrApprove" />
                    <bpmn:sequenceFlow id="flow3" sourceRef="hrApprove" targetRef="endEvent" />
                    <bpmn:sequenceFlow id="Flow_0fnqi4c" sourceRef="startEvent" targetRef="deptApprove" />
                    <bpmn:sequenceFlow id="Flow_09gi1bi" sourceRef="deptApprove" targetRef="hrApprove" />
                    <bpmn:sequenceFlow id="Flow_1bw7xaf" sourceRef="hrApprove" targetRef="endEvent" />
                  </bpmn:process>
                  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="leave_multi">
                      <bpmndi:BPMNEdge id="Flow_0fnqi4c_di" bpmnElement="Flow_0fnqi4c">
                        <di:waypoint x="216" y="178" />
                        <di:waypoint x="280" y="178" />
                      </bpmndi:BPMNEdge>
                    </bpmndi:BPMNPlane>
                  </bpmndi:BPMNDiagram>
                </bpmn:definitions>
                """;

        BpmnXmlUtils.NormalizationResult result = BpmnXmlUtils.normalizeDuplicateSequenceFlows(xml);

        assertTrue(result.hasRepairs());
        assertEquals(3, result.getRepairs().size());
        assertFalse(result.getBpmnXml().contains("id=\"flow1\""));
        assertFalse(result.getBpmnXml().contains("id=\"flow2\""));
        assertFalse(result.getBpmnXml().contains("id=\"flow3\""));
        assertTrue(result.getBpmnXml().contains("id=\"Flow_0fnqi4c\""));
        assertTrue(result.getBpmnXml().contains("id=\"Flow_09gi1bi\""));
        assertTrue(result.getBpmnXml().contains("id=\"Flow_1bw7xaf\""));
    }

    @Test
    void shouldKeepDifferentConditionalFlowsWithSameSourceAndTarget() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="approval" isExecutable="true">
                    <bpmn:exclusiveGateway id="decision" />
                    <bpmn:userTask id="approveTask" />
                    <bpmn:sequenceFlow id="flow1" sourceRef="decision" targetRef="approveTask">
                      <bpmn:conditionExpression>${amount &gt; 1000}</bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="flow2" sourceRef="decision" targetRef="approveTask">
                      <bpmn:conditionExpression>${priority == 'high'}</bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        BpmnXmlUtils.NormalizationResult result = BpmnXmlUtils.normalizeDuplicateSequenceFlows(xml);

        assertFalse(result.hasRepairs());
        assertEquals(xml, result.getBpmnXml());
    }

    @Test
    void shouldRejectDoctypeBeforeReadingExternalEntities() {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE definitions [
                  <!ENTITY external SYSTEM "file:///etc/passwd">
                ]>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="unsafe" isExecutable="true">
                    <documentation>&external;</documentation>
                  </process>
                </definitions>
                """;

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> BpmnXmlUtils.normalizeDuplicateSequenceFlows(xml));

        assertTrue(exception.getMessage().startsWith("BPMN XML 解析失败："));
    }
}
