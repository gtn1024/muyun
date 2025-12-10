package net.ximatai.muyun.test.workflow;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import jakarta.inject.Inject;
import net.ximatai.muyun.core.config.MuYunConfig;
import net.ximatai.muyun.model.PageResult;
import net.ximatai.muyun.test.testcontainers.PostgresTestResource;
import net.ximatai.muyun.workflow.controller.*;
import net.ximatai.muyun.workflow.engine.WorkflowEngine;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(value = PostgresTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("工作流模块完整测试")
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
    WorkflowHistoryController workflowHistoryController;

    @Inject
    WorkflowEngine workflowEngine;

    private static final String BASE_PATH = "/api/platform";

    // 测试数据，在各个测试间共享
    private static String testDefinitionId;
    private static String testStartNodeId;
    private static String testApproveNodeId;
    private static String testEndNodeId;
    private static String testInstanceId;
    private static String testTaskId;

    @BeforeEach
    void setUp() {
        // 如果还没有创建测试流程定义，则创建一个
        if (testDefinitionId == null) {
            createTestWorkflowDefinition();
        }
    }

    /**
     * 创建测试用的工作流定义
     */
    private void createTestWorkflowDefinition() {
        System.out.println("\n========== 初始化测试工作流定义 ==========");

        // 创建流程定义
        testDefinitionId = workflowDefinitionController.create(Map.of(
            "v_name", "测试审批流程",
            "v_code", "test_approval_" + System.currentTimeMillis(),
            "v_category", "测试分类",
            "dict_workflow_status", "draft",
            "v_description", "用于测试的审批流程"
        ));
        System.out.println("✓ 创建流程定义: " + testDefinitionId);

        // 创建开始节点
        testStartNodeId = workflowNodeController.create(Map.of(
            "id_at_workflow_definition", testDefinitionId,
            "v_name", "开始",
            "v_code", "start",
            "dict_node_type", "start",
            "i_order", 1
        ));
        System.out.println("✓ 创建开始节点: " + testStartNodeId);

        // 创建审批节点
        testApproveNodeId = workflowNodeController.create(Map.of(
            "id_at_workflow_definition", testDefinitionId,
            "v_name", "审批",
            "v_code", "approve",
            "dict_node_type", "approve",
            "dict_assignee_type", "user",
            "ids_assignee", List.of(config.superUserId()),
            "i_order", 2
        ));
        System.out.println("✓ 创建审批节点: " + testApproveNodeId);

        // 创建结束节点
        testEndNodeId = workflowNodeController.create(Map.of(
            "id_at_workflow_definition", testDefinitionId,
            "v_name", "结束",
            "v_code", "end",
            "dict_node_type", "end",
            "i_order", 3
        ));
        System.out.println("✓ 创建结束节点: " + testEndNodeId);

        // 创建流转：开始 -> 审批
        String transition1 = workflowTransitionController.create(Map.of(
            "id_at_workflow_definition", testDefinitionId,
            "id_from_node", testStartNodeId,
            "id_to_node", testApproveNodeId,
            "v_name", "提交",
            "i_order", 1
        ));
        System.out.println("✓ 创建流转: 开始->审批");

        // 创建流转：审批 -> 结束（同意）
        String transition2 = workflowTransitionController.create(Map.of(
            "id_at_workflow_definition", testDefinitionId,
            "id_from_node", testApproveNodeId,
            "id_to_node", testEndNodeId,
            "v_name", "同意",
            "i_order", 1
        ));
        System.out.println("✓ 创建流转: 审批->结束");

        // 激活流程
        workflowDefinitionController.update(testDefinitionId, Map.of(
            "dict_workflow_status", "active"
        ));
        System.out.println("✓ 激活流程定义");
        System.out.println("==========================================\n");
    }

    @Test
    @Order(1)
    @DisplayName("1. 测试创建流程定义")
    public void test01CreateWorkflowDefinition() {
        System.out.println("\n[测试1] 创建流程定义");

        Map<String, Object> definition = Map.of(
            "v_name", "单元测试流程",
            "v_code", "unit_test_workflow_" + System.currentTimeMillis(),
            "v_category", "测试分类",
            "i_version", 1,
            "dict_workflow_status", "draft",
            "v_description", "这是一个单元测试流程"
        );

        String defId = given()
            .header("userID", config.superUserId())
            .contentType("application/json")
            .body(definition)
            .when()
            .post(BASE_PATH + "/workflow_definition/create")
            .then()
            .statusCode(200)
            .extract()
            .asString();

        assertNotNull(defId);

        // 验证创建结果
        Map<String, ?> created = workflowDefinitionController.view(defId);
        assertEquals("单元测试流程", created.get("v_name"));
        assertEquals("draft", created.get("dict_workflow_status"));

        System.out.println("✓ 流程定义创建成功: " + defId);
    }

    @Test
    @Order(2)
    @DisplayName("2. 测试创建完整流程（包含节点和流转）")
    public void test02CreateCompleteWorkflow() {
        System.out.println("\n[测试2] 创建完整流程（包含节点和流转）");

        // 创建流程定义
        String definitionId = workflowDefinitionController.create(Map.of(
            "v_name", "请假审批流程",
            "v_code", "leave_approval_" + System.currentTimeMillis(),
            "v_category", "人事管理",
            "dict_workflow_status", "draft",
            "v_description", "员工请假审批流程"
        ));
        assertNotNull(definitionId);
        System.out.println("✓ 创建流程定义: " + definitionId);

        // 创建开始节点
        String startNodeId = workflowNodeController.create(Map.of(
            "id_at_workflow_definition", definitionId,
            "v_name", "开始",
            "v_code", "start",
            "dict_node_type", "start",
            "i_order", 1
        ));
        System.out.println("✓ 创建开始节点: " + startNodeId);

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
        System.out.println("✓ 创建审批节点: " + approveNodeId);

        // 创建结束节点
        String endNodeId = workflowNodeController.create(Map.of(
            "id_at_workflow_definition", definitionId,
            "v_name", "结束",
            "v_code", "end",
            "dict_node_type", "end",
            "i_order", 3
        ));
        System.out.println("✓ 创建结束节点: " + endNodeId);

        // 创建流转关系：开始 -> 审批
        workflowTransitionController.create(Map.of(
            "id_at_workflow_definition", definitionId,
            "id_from_node", startNodeId,
            "id_to_node", approveNodeId,
            "v_name", "提交",
            "i_order", 1
        ));
        System.out.println("✓ 创建流转: 开始->审批");

        // 创建流转关系：审批 -> 结束（同意）
        workflowTransitionController.create(Map.of(
            "id_at_workflow_definition", definitionId,
            "id_from_node", approveNodeId,
            "id_to_node", endNodeId,
            "v_name", "同意",
            "i_order", 1
        ));
        System.out.println("✓ 创建流转: 审批->结束");

        // 验证创建结果
        Map<String, ?> definitionView = workflowDefinitionController.view(definitionId);
        assertEquals("请假审批流程", definitionView.get("v_name"));

        System.out.println("✓ 完整流程创建成功");
    }

    @Test
    @Order(3)
    @DisplayName("3. 测试激活流程")
    public void test03ActivateWorkflow() {
        System.out.println("\n[测试3] 激活流程");

        // 创建并激活流程
        String definitionId = workflowDefinitionController.create(Map.of(
            "v_name", "采购申请流程",
            "v_code", "purchase_request_" + System.currentTimeMillis(),
            "v_category", "采购管理",
            "dict_workflow_status", "draft"
        ));
        System.out.println("✓ 创建流程: " + definitionId);

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

        System.out.println("✓ 流程激活成功");
    }

    @Test
    @Order(4)
    @DisplayName("4. 测试暂停和归档流程")
    public void test04SuspendAndArchiveWorkflow() {
        System.out.println("\n[测试4] 暂停和归档流程");

        String definitionId = workflowDefinitionController.create(Map.of(
            "v_name", "状态测试流程",
            "v_code", "status_test_" + System.currentTimeMillis(),
            "v_category", "测试",
            "dict_workflow_status", "active"
        ));
        System.out.println("✓ 创建并激活流程: " + definitionId);

        // 暂停流程
        given()
            .header("userID", config.superUserId())
            .contentType("application/json")
            .when()
            .post(BASE_PATH + "/workflow_definition/suspend/" + definitionId)
            .then()
            .statusCode(200);

        Map<String, ?> suspended = workflowDefinitionController.view(definitionId);
        assertEquals("suspended", suspended.get("dict_workflow_status"));
        System.out.println("✓ 流程已暂停");

        // 归档流程
        given()
            .header("userID", config.superUserId())
            .contentType("application/json")
            .when()
            .post(BASE_PATH + "/workflow_definition/archive/" + definitionId)
            .then()
            .statusCode(200);

        Map<String, ?> archived = workflowDefinitionController.view(definitionId);
        assertEquals("archived", archived.get("dict_workflow_status"));
        System.out.println("✓ 流程已归档");
    }

    @Test
    @Order(5)
    @DisplayName("5. 测试复制流程")
    public void test05CopyWorkflow() {
        System.out.println("\n[测试5] 复制流程");

        // 创建原始流程
        String originalId = workflowDefinitionController.create(Map.of(
            "v_name", "原始流程",
            "v_code", "original_" + System.currentTimeMillis(),
            "v_category", "测试",
            "dict_workflow_status", "active",
            "v_description", "原始流程描述"
        ));
        System.out.println("✓ 创建原始流程: " + originalId);

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
        assertNotEquals(originalId, copiedId);
        System.out.println("✓ 复制流程: " + copiedId);

        // 验证复制结果
        Map<String, ?> copiedDef = workflowDefinitionController.view(copiedId);
        assertTrue(((String) copiedDef.get("v_name")).contains("副本"));
        assertEquals("draft", copiedDef.get("dict_workflow_status"));

        System.out.println("✓ 流程复制成功");
    }

    @Test
    @Order(6)
    @DisplayName("6. 测试启动流程实例")
    public void test06StartProcessInstance() {
        System.out.println("\n[测试6] 启动流程实例");

        // 准备流程变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("申请人", "张三");
        variables.put("申请金额", 5000);
        variables.put("申请原因", "测试启动流程");

        // 启动流程
        testInstanceId = workflowEngine.startProcess(
            testDefinitionId,
            "TEST_BUSINESS_" + System.currentTimeMillis(),
            testDefinitionId,
            config.superUserId(),
            variables
        );

        assertNotNull(testInstanceId);
        System.out.println("✓ 流程实例启动成功: " + testInstanceId);

        // 验证实例状态
        Map<String, ?> instance = workflowInstanceController.view(testInstanceId);
        assertEquals("running", instance.get("dict_instance_status"));
        assertEquals(testDefinitionId, instance.get("id_at_workflow_definition"));
        assertEquals(config.superUserId(), instance.get("id_starter"));

        System.out.println("✓ 实例状态验证通过");

        // 验证任务已创建
        PageResult<Map> tasks = workflowTaskController.view(
            null, null, true, null,
            Map.of("id_at_workflow_instance", testInstanceId)
        );

        assertTrue(tasks.getTotal() > 0, "应该创建了待办任务");
        testTaskId = (String) tasks.getList().get(0).get("id");

        System.out.println("✓ 待办任务已创建: " + testTaskId);
    }

    @Test
    @Order(7)
    @DisplayName("7. 测试查询待办任务")
    public void test07QueryTodoTasks() {
        System.out.println("\n[测试7] 查询待办任务");

        PageResult<Map> response = given()
            .header("userID", config.superUserId())
            .when()
            .get(BASE_PATH + "/workflow_task/todo?page=1&size=10")
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<PageResult<Map>>() {});

        assertNotNull(response);
        assertNotNull(response.getList());

        System.out.println("✓ 待办任务数量: " + response.getTotal());

        // 打印任务详情
        if (!response.getList().isEmpty()) {
            Map<String, ?> task = response.getList().get(0);
            System.out.println("  - 任务名称: " + task.get("v_name"));
            System.out.println("  - 任务状态: " + task.get("dict_task_status"));
        }
    }

    @Test
    @Order(8)
    @DisplayName("8. 测试完成任务")
    public void test08CompleteTask() {
        System.out.println("\n[测试8] 完成任务");

        if (testTaskId == null) {
            System.out.println("⚠ 跳过测试：没有可用的任务ID");
            return;
        }

        // 完成任务
        Map<String, Object> completeData = new HashMap<>();
        completeData.put("v_action", "同意");
        completeData.put("v_comment", "测试通过，同意");
        completeData.put("j_form_data", Map.of("审批意见", "同意", "审批时间", System.currentTimeMillis()));

        given()
            .header("userID", config.superUserId())
            .contentType("application/json")
            .body(completeData)
            .when()
            .post(BASE_PATH + "/workflow_task/complete/" + testTaskId)
            .then()
            .statusCode(200);

        System.out.println("✓ 任务完成成功");

        // 验证任务状态
        Map<String, ?> task = workflowTaskController.view(testTaskId);
        assertEquals("completed", task.get("dict_task_status"));
        assertEquals("同意", task.get("v_action"));

        System.out.println("✓ 任务状态已更新为: completed");
    }

    @Test
    @Order(9)
    @DisplayName("9. 测试查询已办任务")
    public void test09QueryDoneTasks() {
        System.out.println("\n[测试9] 查询已办任务");

        PageResult<Map> response = given()
            .header("userID", config.superUserId())
            .when()
            .get(BASE_PATH + "/workflow_task/done?page=1&size=10")
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<PageResult<Map>>() {});

        assertNotNull(response);
        assertNotNull(response.getList());

        System.out.println("✓ 已办任务数量: " + response.getTotal());

        if (!response.getList().isEmpty()) {
            Map<String, ?> task = response.getList().get(0);
            System.out.println("  - 任务名称: " + task.get("v_name"));
            System.out.println("  - 操作: " + task.get("v_action"));
        }
    }

    @Test
    @Order(10)
    @DisplayName("10. 测试查询流程历史")
    public void test10QueryWorkflowHistory() {
        System.out.println("\n[测试10] 查询流程历史");

        if (testInstanceId == null) {
            System.out.println("⚠ 跳过测试：没有可用的实例ID");
            return;
        }

        PageResult<Map> history = given()
            .header("userID", config.superUserId())
            .when()
            .get(BASE_PATH + "/workflow_instance/history/" + testInstanceId)
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<PageResult<Map>>() {});

        assertNotNull(history);
        assertTrue(history.getTotal() > 0, "应该有历史记录");

        System.out.println("✓ 历史记录数量: " + history.getTotal());

        // 打印历史记录
        for (Map<String, ?> record : history.getList()) {
            System.out.println("  - 操作: " + record.get("v_action_type") +
                             " | 意见: " + record.get("v_comment"));
        }
    }

    @Test
    @Order(11)
    @DisplayName("11. 测试获取流程图状态")
    public void test11GetWorkflowDiagram() {
        System.out.println("\n[测试11] 获取流程图状态");

        if (testInstanceId == null) {
            System.out.println("⚠ 跳过测试：没有可用的实例ID");
            return;
        }

        Map<String, Object> diagram = given()
            .header("userID", config.superUserId())
            .when()
            .get(BASE_PATH + "/workflow_instance/diagram/" + testInstanceId)
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<Map<String, Object>>() {});

        assertNotNull(diagram);
        assertTrue(diagram.containsKey("definition"));
        assertTrue(diagram.containsKey("instance"));

        System.out.println("✓ 流程图数据获取成功");
    }

    @Test
    @Order(12)
    @DisplayName("12. 测试流程定义的查询功能")
    public void test12QueryWorkflowDefinitions() {
        System.out.println("\n[测试12] 查询流程定义");

        // 按编码查询
        Map<String, Object> filter = Map.of("v_code", testDefinitionId.substring(0, 10));

        PageResult<Map> result = given()
            .header("userID", config.superUserId())
            .contentType("application/json")
            .body(filter)
            .when()
            .post(BASE_PATH + "/workflow_definition/view?noPage=true")
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<PageResult<Map>>() {});

        assertNotNull(result);
        System.out.println("✓ 查询到 " + result.getTotal() + " 个流程定义");
    }

    @Test
    @Order(13)
    @DisplayName("13. 测试任务转办功能")
    public void test13TransferTask() {
        System.out.println("\n[测试13] 任务转办");

        // 创建一个新实例
        String instanceId = workflowEngine.startProcess(
            testDefinitionId,
            "TRANSFER_TEST_" + System.currentTimeMillis(),
            testDefinitionId,
            config.superUserId(),
            Map.of("test", "transfer")
        );

        // 获取任务
        PageResult<Map> tasks = workflowTaskController.view(
            null, null, true, null,
            Map.of("id_at_workflow_instance", instanceId)
        );

        if (tasks.getTotal() > 0) {
            String taskId = (String) tasks.getList().get(0).get("id");

            // 转办任务（这里只是测试接口，实际转办给同一个人）
            given()
                .header("userID", config.superUserId())
                .contentType("application/json")
                .queryParam("toUser", config.superUserId())
                .when()
                .post(BASE_PATH + "/workflow_task/transfer/" + taskId)
                .then()
                .statusCode(200);

            System.out.println("✓ 任务转办成功");

            // 验证任务状态
            Map<String, ?> task = workflowTaskController.view(taskId);
            assertEquals("transferred", task.get("dict_task_status"));
        } else {
            System.out.println("⚠ 没有可转办的任务");
        }
    }

    @Test
    @Order(14)
    @DisplayName("14. 测试任务签收功能")
    public void test14ClaimTask() {
        System.out.println("\n[测试14] 任务签收");

        // 创建一个新实例
        String instanceId = workflowEngine.startProcess(
            testDefinitionId,
            "CLAIM_TEST_" + System.currentTimeMillis(),
            testDefinitionId,
            config.superUserId(),
            Map.of("test", "claim")
        );

        // 获取任务
        PageResult<Map> tasks = workflowTaskController.view(
            null, null, true, null,
            Map.of("id_at_workflow_instance", instanceId)
        );

        if (tasks.getTotal() > 0) {
            String taskId = (String) tasks.getList().get(0).get("id");

            // 签收任务
            given()
                .header("userID", config.superUserId())
                .contentType("application/json")
                .when()
                .post(BASE_PATH + "/workflow_task/claim/" + taskId)
                .then()
                .statusCode(200);

            System.out.println("✓ 任务签收成功");

            // 验证签收时间已更新
            Map<String, ?> task = workflowTaskController.view(taskId);
            assertNotNull(task.get("t_claim_time"));
            System.out.println("✓ 签收时间已记录");
        } else {
            System.out.println("⚠ 没有可签收的任务");
        }
    }

    @Test
    @Order(15)
    @DisplayName("15. 测试完整的工作流生命周期")
    public void test15CompleteWorkflowLifecycle() {
        System.out.println("\n[测试15] 完整工作流生命周期");

        // 1. 创建流程定义
        String defId = workflowDefinitionController.create(Map.of(
            "v_name", "生命周期测试流程",
            "v_code", "lifecycle_test_" + System.currentTimeMillis(),
            "v_category", "测试",
            "dict_workflow_status", "draft"
        ));
        System.out.println("✓ 步骤1: 创建流程定义");

        // 2. 添加节点
        String startNode = workflowNodeController.create(Map.of(
            "id_at_workflow_definition", defId,
            "v_name", "开始",
            "v_code", "start",
            "dict_node_type", "start",
            "i_order", 1
        ));

        String endNode = workflowNodeController.create(Map.of(
            "id_at_workflow_definition", defId,
            "v_name", "结束",
            "v_code", "end",
            "dict_node_type", "end",
            "i_order", 2
        ));
        System.out.println("✓ 步骤2: 添加开始和结束节点");

        // 3. 添加流转
        workflowTransitionController.create(Map.of(
            "id_at_workflow_definition", defId,
            "id_from_node", startNode,
            "id_to_node", endNode,
            "v_name", "完成",
            "i_order", 1
        ));
        System.out.println("✓ 步骤3: 添加流转关系");

        // 4. 激活流程
        workflowDefinitionController.update(defId, Map.of(
            "dict_workflow_status", "active"
        ));
        System.out.println("✓ 步骤4: 激活流程");

        // 5. 启动流程实例
        String instanceId = workflowEngine.startProcess(
            defId,
            "LIFECYCLE_TEST",
            defId,
            config.superUserId(),
            Map.of("测试", "完整生命周期")
        );
        System.out.println("✓ 步骤5: 启动流程实例");

        // 6. 验证流程状态
        Map<String, ?> definition = workflowDefinitionController.view(defId);
        assertEquals("active", definition.get("dict_workflow_status"));

        Map<String, ?> instance = workflowInstanceController.view(instanceId);
        assertNotNull(instance);

        System.out.println("✓ 完整生命周期测试通过");
    }
}
