package net.ximatai.muyun.test.workflow;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import jakarta.inject.Inject;
import net.ximatai.muyun.core.config.MuYunConfig;
import net.ximatai.muyun.test.testcontainers.PostgresTestResource;
import net.ximatai.muyun.workflow.controller.*;
import net.ximatai.muyun.workflow.engine.WorkflowEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value = PostgresTestResource.class)
public class WorkflowTest {
    @Inject
    MuYunConfig config;

    @Inject
    WorkflowDefinitionController workflowDefinitionController;

    @Inject
    WorkflowNodeController workflowNodeController;

    @Inject
    WorkflowTransitionController workflowTransitionController;

    @Inject
    WorkflowInstanceController workflowInstanceController;

    @Inject
    WorkflowTaskController workflowTaskController;

    @Inject
    WorkflowEngine workflowEngine;

    private static final String BASE_PATH = "/api/platform";

    @Test
    public void testCreateWorkflowDefinition() {
        // 创建流程定义
        Map<String, Object> definition = Map.of(
            "v_name", "测试流程",
            "v_code", "test_workflow_" + System.currentTimeMillis(),
            "v_category", "测试分类",
            "i_version", 1,
            "dict_workflow_status", "draft",
            "v_description", "这是一个测试流程"
        );

        String response = given()
            .header("userID", config.superUserId())
            .contentType("application/json")
            .body(definition)
            .when()
            .post(BASE_PATH + "/workflow_definition/create")
            .then()
            .statusCode(200)
            .extract()
            .asString();

        assertNotNull(response);
        System.out.println("Created workflow definition: " + response);
    }

    @Test
    public void testCreateCompleteWorkflow() {
        // 创建完整的流程定义（包含节点和流转）
        String definitionId = workflowDefinitionController.create(Map.of(
            "v_name", "请假审批流程",
            "v_code", "leave_approval_" + System.currentTimeMillis(),
            "v_category", "人事管理",
            "dict_workflow_status", "draft",
            "v_description", "员工请假审批流程"
        ));

        assertNotNull(definitionId);

        // 创建开始节点
        String startNodeId = workflowNodeController.create(Map.of(
            "id_at_workflow_definition", definitionId,
            "v_name", "开始",
            "v_code", "start",
            "dict_node_type", "start",
            "i_order", 1
        ));

        // 创建审批节点
        String approveNodeId = workflowNodeController.create(Map.of(
            "id_at_workflow_definition", definitionId,
            "v_name", "部门经理审批",
            "v_code", "manager_approve",
            "dict_node_type", "approve",
            "dict_assignee_type", "role",
            "ids_assignee", List.of("manager_role_id"),
            "i_order", 2
        ));

        // 创建结束节点
        String endNodeId = workflowNodeController.create(Map.of(
            "id_at_workflow_definition", definitionId,
            "v_name", "结束",
            "v_code", "end",
            "dict_node_type", "end",
            "i_order", 3
        ));

        // 创建流转关系：开始 -> 审批
        workflowTransitionController.create(Map.of(
            "id_at_workflow_definition", definitionId,
            "id_from_node", startNodeId,
            "id_to_node", approveNodeId,
            "v_name", "提交",
            "i_order", 1
        ));

        // 创建流转关系：审批 -> 结束（同意）
        workflowTransitionController.create(Map.of(
            "id_at_workflow_definition", definitionId,
            "id_from_node", approveNodeId,
            "id_to_node", endNodeId,
            "v_name", "同意",
            "i_order", 1
        ));

        // 验证创建结果
        Map<String, ?> definitionView = workflowDefinitionController.view(definitionId);
        assertEquals("请假审批流程", definitionView.get("v_name"));

        System.out.println("Created complete workflow with nodes and transitions");
    }

    @Test
    public void testActivateWorkflow() {
        // 创建并激活流程
        String definitionId = workflowDefinitionController.create(Map.of(
            "v_name", "采购申请流程",
            "v_code", "purchase_request_" + System.currentTimeMillis(),
            "v_category", "采购管理",
            "dict_workflow_status", "draft"
        ));

        // 激活流程
       given()
            .header("userID", config.superUserId())
            .contentType("application/json")
            .when()
            .post(BASE_PATH + "/workflow_definition/activate/" + definitionId)
            .then()
            .statusCode(200)
            .body(is("1"));

        // 验证状态
        Map<String, ?> definition = workflowDefinitionController.view(definitionId);
        assertEquals("active", definition.get("dict_workflow_status"));

        System.out.println("Activated workflow definition");
    }

    @Test
    public void testCopyWorkflow() {
        // 创建原始流程
        String originalId = workflowDefinitionController.create(Map.of(
            "v_name", "原始流程",
            "v_code", "original_" + System.currentTimeMillis(),
            "v_category", "测试",
            "dict_workflow_status", "active",
            "v_description", "原始流程描述"
        ));

        // 复制流程
        String copiedId = given()
            .header("userID", config.superUserId())
            .contentType("application/json")
            .when()
            .post(BASE_PATH + "/workflow_definition/copy/" + originalId)
            .then()
            .statusCode(200)
            .extract()
            .asString();

        assertNotNull(copiedId);

        // 验证复制结果
        Map<String, ?> copiedDef = workflowDefinitionController.view(copiedId);
        assertTrue(((String) copiedDef.get("v_name")).contains("副本"));
        assertEquals("draft", copiedDef.get("dict_workflow_status"));

        System.out.println("Copied workflow definition");
    }

    @Test
    public void testQueryTasks() {
        // 查询待办任务
        var response = given()
            .header("userID", config.superUserId())
            .when()
            .get(BASE_PATH + "/workflow_task/todo?page=1&size=10")
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<Map<String, Object>>() {});

        assertNotNull(response);
        assertTrue(response.containsKey("list"));
        assertTrue(response.containsKey("total"));

        System.out.println("Queried todo tasks: " + response);
    }

    @Test
    public void testWorkflowLifecycle() {
        // 这个测试演示完整的工作流生命周期
        // 由于需要实际的用户和权限数据，这里只做结构性测试

        // 1. 创建流程定义
        String definitionId = workflowDefinitionController.create(Map.of(
            "v_name", "完整测试流程",
            "v_code", "full_test_" + System.currentTimeMillis(),
            "v_category", "测试",
            "dict_workflow_status", "draft"
        ));

        // 2. 添加节点
        String startNode = workflowNodeController.create(Map.of(
            "id_at_workflow_definition", definitionId,
            "v_name", "开始",
            "v_code", "start",
            "dict_node_type", "start",
            "i_order", 1
        ));

        String endNode = workflowNodeController.create(Map.of(
            "id_at_workflow_definition", definitionId,
            "v_name", "结束",
            "v_code", "end",
            "dict_node_type", "end",
            "i_order", 2
        ));

        // 3. 添加流转
        workflowTransitionController.create(Map.of(
            "id_at_workflow_definition", definitionId,
            "id_from_node", startNode,
            "id_to_node", endNode,
            "v_name", "完成",
            "i_order", 1
        ));

        // 4. 激活流程
        workflowDefinitionController.update(definitionId, Map.of(
            "dict_workflow_status", "active"
        ));

        // 验证流程定义已就绪
        Map<String, ?> definition = workflowDefinitionController.view(definitionId);
        assertEquals("active", definition.get("dict_workflow_status"));

        System.out.println("Workflow lifecycle test completed");
    }
}
