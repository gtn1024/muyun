# MuYun Workflow 使用指南

## 目录
1. [概述](#概述)
2. [核心概念](#核心概念)
3. [快速开始](#快速开始)
4. [业务集成](#业务集成)
5. [API 接口说明](#api-接口说明)
6. [完整示例](#完整示例)
7. [高级用法](#高级用法)

---

## 概述

MuYun Workflow 是 MuYun 轻代码平台的工作流模块，提供完整的流程定义、流转、审批功能。

### 主要特性
- ✅ **轻量级设计**：遵循 MuYun 平台架构，简单易用
- ✅ **灵活的流程定义**：支持开始、结束、审批、会签、条件、并行等多种节点类型
- ✅ **多种办理人分配**：支持指定人员、角色、部门、发起人、上级、动态分配
- ✅ **完整的历史记录**：记录每次操作的详细信息
- ✅ **消息通知集成**：自动通知待办任务
- ✅ **业务集成友好**：通过 `IWorkflowAbility` 接口轻松集成工作流

---

## 核心概念

### 1. 流程定义 (Workflow Definition)
流程的蓝图，定义了流程的基本信息、节点、流转关系。

**主要属性：**
- `v_name`: 流程名称
- `v_code`: 流程编码（唯一标识）
- `v_category`: 流程分类
- `dict_workflow_status`: 状态（draft/active/suspended/archived）
- `i_version`: 版本号

### 2. 流程节点 (Workflow Node)
流程中的各个环节，如开始、审批、结束等。

**节点类型：**
- `start`: 开始节点 - 流程的起点，每个流程只能有一个
- `end`: 结束节点 - 流程的终点，可以有多个
- `approve`: 审批节点 - 需要人工审批的节点
- `countersign`: 会签节点 - 多人并行审批（待实现）
- `condition`: 条件节点 - 根据条件自动流转（待实现）
- `parallel`: 并行节点 - 同时执行多个分支（待实现）

**办理人类型：**
- `user`: 指定人员
- `role`: 角色（查询角色下的所有用户）
- `department`: 部门（查询部门下的所有用户）
- `starter`: 发起人
- `superior`: 上级（发起人的上级）
- `dynamic`: 动态（根据流程变量计算）

### 3. 流程流转 (Workflow Transition)
定义节点之间的连接关系和流转条件。

**主要属性：**
- `id_from_node`: 源节点ID
- `id_to_node`: 目标节点ID
- `v_name`: 流转名称（如"同意"、"驳回"）
- `v_condition`: 流转条件表达式

### 4. 流程实例 (Workflow Instance)
运行中的流程实例，与具体的业务单据关联。

**实例状态：**
- `running`: 运行中
- `completed`: 已完成
- `terminated`: 已终止
- `suspended`: 已暂停

### 5. 工作任务 (Workflow Task)
待办/已办任务，分配给具体的办理人。

**任务状态：**
- `pending`: 待办
- `completed`: 已完成
- `rejected`: 已驳回
- `transferred`: 已转办
- `cancelled`: 已取消

---

## 快速开始

### 第一步：创建流程定义

```java
// 1. 创建流程定义
String definitionId = workflowDefinitionController.create(Map.of(
    "v_name", "请假审批流程",
    "v_code", "leave_approval",
    "v_category", "人事管理",
    "dict_workflow_status", "draft",
    "v_description", "员工请假审批流程"
));

// 2. 创建开始节点
String startNodeId = workflowNodeController.create(Map.of(
    "id_at_workflow_definition", definitionId,
    "v_name", "开始",
    "v_code", "start",
    "dict_node_type", "start",
    "i_order", 1
));

// 3. 创建审批节点
String approveNodeId = workflowNodeController.create(Map.of(
    "id_at_workflow_definition", definitionId,
    "v_name", "部门经理审批",
    "v_code", "manager_approve",
    "dict_node_type", "approve",
    "dict_assignee_type", "role",
    "ids_assignee", List.of("manager_role_id"),
    "i_order", 2
));

// 4. 创建结束节点
String endNodeId = workflowNodeController.create(Map.of(
    "id_at_workflow_definition", definitionId,
    "v_name", "结束",
    "v_code", "end",
    "dict_node_type", "end",
    "i_order", 3
));

// 5. 创建流转关系：开始 -> 审批
workflowTransitionController.create(Map.of(
    "id_at_workflow_definition", definitionId,
    "id_from_node", startNodeId,
    "id_to_node", approveNodeId,
    "v_name", "提交",
    "i_order", 1
));

// 6. 创建流转关系：审批 -> 结束
workflowTransitionController.create(Map.of(
    "id_at_workflow_definition", definitionId,
    "id_from_node", approveNodeId,
    "id_to_node", endNodeId,
    "v_name", "同意",
    "i_order", 1
));

// 7. 激活流程
workflowDefinitionController.update(definitionId, Map.of(
    "dict_workflow_status", "active"
));
```

### 第二步：启动流程实例

```java
// 启动流程
String instanceId = workflowEngine.startProcess(
    "leave_approval",           // 流程定义编码
    "business_key_001",         // 业务单据ID
    "leave_request",            // 业务模块别名
    "user_id_001",             // 发起人ID
    Map.of(                    // 流程变量
        "days", 3,
        "leaveType", "annual"
    )
);
```

### 第三步：处理任务

```java
// 获取我的待办任务
PageResult<Map> todoList = workflowTaskController.getTodoList(1, 10);

// 完成任务
workflowEngine.completeTask(
    taskId,                    // 任务ID
    "同意",                     // 操作
    "同意请假申请",              // 审批意见
    Map.of()                   // 表单数据
);
```

---

## 业务集成

### 实现 IWorkflowAbility 接口

业务模块通过实现 `IWorkflowAbility` 接口即可拥有工作流能力。

```java
@Startup
@Tag(description = "请假申请")
@Path(BASE_PATH + "/leave_request")
public class LeaveRequestController extends ScaffoldForPlatform
    implements IModuleRegisterAbility, IWorkflowAbility {

    @Inject
    WorkflowEngine workflowEngine;

    @Inject
    ModuleController moduleController;

    @Override
    public String getMainTable() {
        return "example_leave_request";
    }

    @Override
    public void fitOut(TableWrapper wrapper) {
        wrapper
            .setComment("请假申请")
            .setPrimaryKey(PresetColumn.ID_POSTGRES_UUID)
            .setInherit(BaseBusinessTable.TABLE)
            .addColumn("v_title", "标题")
            .addColumn("d_start_date", "开始日期")
            .addColumn("d_end_date", "结束日期")
            .addColumn("i_days", "请假天数")
            .addColumn("v_reason", "请假原因")
            .addColumn("dict_leave_type", "请假类型")
            .addColumn("dict_status", "状态", "'draft'")
            .addColumn("id_at_workflow_instance", "流程实例ID");
    }

    // 1. 指定工作流编码
    @Override
    public String getWorkflowCode() {
        return "leave_approval";
    }

    // 2. 提交审批接口
    @POST
    @Path("/submit/{id}")
    @Operation(summary = "提交审批")
    public String submitForApproval(@PathParam("id") String id) {
        // 业务校验
        beforeSubmitWorkflow(id, null);

        // 获取流程变量
        Map<String, Object> variables = getWorkflowVariables(id);

        // 启动工作流
        String instanceId = workflowEngine.startProcess(
            getWorkflowCode(),
            id,
            getModuleConfig().getAlias(),
            getUser().getId(),
            variables
        );

        // 更新业务状态
        this.update(id, Map.of(
            "dict_status", "approving",
            "id_at_workflow_instance", instanceId
        ));

        // 流程启动后回调
        afterWorkflowStarted(id, instanceId);

        return instanceId;
    }

    // 3. 提交前校验
    @Override
    public void beforeSubmitWorkflow(String id, Map body) {
        Map<String, ?> data = view(id);

        if (data.get("d_start_date") == null || data.get("d_end_date") == null) {
            throw new MuYunException("请假日期不能为空");
        }

        if (data.get("v_reason") == null || ((String) data.get("v_reason")).isEmpty()) {
            throw new MuYunException("请假原因不能为空");
        }
    }

    // 4. 提供流程变量
    @Override
    public Map<String, Object> getWorkflowVariables(String id) {
        Map<String, ?> data = view(id);

        Map<String, Object> variables = new HashMap<>();
        variables.put("starterId", getUser().getId());
        variables.put("days", data.get("i_days"));
        variables.put("leaveType", data.get("dict_leave_type"));
        variables.put("applicantName", getUser().getName());
        variables.put("businessKey", id);

        // 根据请假天数决定审批级别
        Integer days = (Integer) data.get("i_days");
        if (days != null) {
            if (days <= 3) {
                variables.put("approvalLevel", "manager");
            } else if (days <= 7) {
                variables.put("approvalLevel", "director");
            } else {
                variables.put("approvalLevel", "ceo");
            }
        }

        return variables;
    }

    // 5. 审批通过后的回调
    @Override
    public void afterWorkflowApproved(String id) {
        this.update(id, Map.of("dict_status", "approved"));
        System.out.println("请假申请 [" + id + "] 已审批通过");
        // 可以在这里发送通知、更新考勤等
    }

    // 6. 审批驳回后的回调
    @Override
    public void afterWorkflowRejected(String id) {
        this.update(id, Map.of("dict_status", "rejected"));
        System.out.println("请假申请 [" + id + "] 已被驳回");
    }

    // 7. 流程完成后的回调
    @Override
    public void afterWorkflowCompleted(String id, String instanceId, String status) {
        System.out.println("请假申请 [" + id + "] 流程已完成，状态: " + status);
    }

    @Override
    public ModuleConfig getModuleConfig() {
        return ModuleConfig.ofName("请假申请")
            .setAlias("leave_request")
            .setTable(getMainTable())
            .setUrl("platform/leave/index")
            .addAction(new ModuleAction("submit", "提交审批", ModuleAction.TypeLike.UPDATE));
    }

    @Override
    public ModuleController getModuleController() {
        return moduleController;
    }
}
```

### IWorkflowAbility 接口方法说明

| 方法 | 说明 | 是否必须实现 |
|------|------|-------------|
| `getWorkflowCode()` | 返回工作流定义编码 | 是 |
| `beforeSubmitWorkflow(id, body)` | 提交审批前的校验 | 否 |
| `getWorkflowVariables(id)` | 提供流程变量 | 否（默认返回全部业务数据） |
| `afterWorkflowStarted(id, instanceId)` | 流程启动后的回调 | 否 |
| `afterWorkflowApproved(id)` | 审批通过后的回调 | 否 |
| `afterWorkflowRejected(id)` | 审批驳回后的回调 | 否 |
| `afterWorkflowCompleted(id, instanceId, status)` | 流程完成后的回调 | 否 |

---

## API 接口说明

### 流程定义管理

#### 1. 创建流程定义
```http
POST /api/platform/workflow_definition/create
Content-Type: application/json

{
  "v_name": "请假审批流程",
  "v_code": "leave_approval",
  "v_category": "人事管理",
  "dict_workflow_status": "draft",
  "v_description": "员工请假审批流程"
}
```

#### 2. 更新流程定义
```http
POST /api/platform/workflow_definition/update/{id}
Content-Type: application/json

{
  "v_name": "修改后的流程名称",
  "v_description": "修改后的描述"
}
```

#### 3. 查看流程定义
```http
GET /api/platform/workflow_definition/view/{id}
```

#### 4. 激活流程
```http
POST /api/platform/workflow_definition/activate/{id}
```

#### 5. 暂停流程
```http
POST /api/platform/workflow_definition/suspend/{id}
```

#### 6. 归档流程
```http
POST /api/platform/workflow_definition/archive/{id}
```

#### 7. 复制流程
```http
POST /api/platform/workflow_definition/copy/{id}
```

### 任务管理

#### 1. 我的待办
```http
GET /api/platform/workflow_task/todo?page=1&size=10
```

#### 2. 我的已办
```http
GET /api/platform/workflow_task/done?page=1&size=10
```

#### 3. 完成任务
```http
POST /api/platform/workflow_task/complete/{taskId}
Content-Type: application/json

{
  "v_action": "同意",
  "v_comment": "同意此申请",
  "j_form_data": {
    "approvalDate": "2025-01-01"
  }
}
```

#### 4. 转办任务
```http
POST /api/platform/workflow_task/transfer/{taskId}?toUser={userId}
```

#### 5. 签收任务
```http
POST /api/platform/workflow_task/claim/{taskId}
```

### 流程实例管理

#### 1. 查看流程实例
```http
GET /api/platform/workflow_instance/view/{id}
```

#### 2. 获取流程历史
```http
GET /api/platform/workflow_instance/history/{id}
```

#### 3. 获取流程图状态
```http
GET /api/platform/workflow_instance/diagram/{id}
```

---

## 完整示例

参见 `muyun-workflow/src/main/java/net/ximatai/muyun/workflow/example/LeaveRequestController.java`

这是一个完整的请假申请业务集成示例，展示了：
- 如何定义业务表结构
- 如何实现 IWorkflowAbility 接口
- 如何提交审批
- 如何处理审批结果回调
- 如何提供流程变量

---

## 高级用法

### 1. 多级审批

创建多个审批节点，通过流转关系串联：

```
开始 -> 部门经理审批 -> 总监审批 -> CEO审批 -> 结束
```

### 2. 条件流转

根据流程变量决定流转路径：

```java
// 创建条件流转
workflowTransitionController.create(Map.of(
    "id_at_workflow_definition", definitionId,
    "id_from_node", approveNodeId,
    "id_to_node", directorNodeId,
    "v_name", "提交上级",
    "v_condition", "days > 3"  // 请假天数大于3天需要上级审批
));
```

### 3. 动态办理人

根据流程变量动态计算办理人：

```java
// 节点配置
workflowNodeController.create(Map.of(
    "id_at_workflow_definition", definitionId,
    "v_name", "上级审批",
    "dict_node_type", "approve",
    "dict_assignee_type", "superior",  // 发起人的上级
    "i_order", 2
));
```

### 4. 驳回到指定节点

```java
// 创建驳回流转
workflowTransitionController.create(Map.of(
    "id_at_workflow_definition", definitionId,
    "id_from_node", approveNodeId,
    "id_to_node", startNodeId,  // 驳回到开始节点
    "v_name", "驳回",
    "i_order", 2
));
```

### 5. 监听流程事件

通过 EventBus 监听流程事件：

```java
@ApplicationScoped
public class WorkflowEventListener {
    
    void onTaskCreated(@Observes WorkflowTaskCreatedEvent event) {
        // 任务创建时的处理逻辑
        System.out.println("新任务创建: " + event.getTaskId());
    }
    
    void onTaskCompleted(@Observes WorkflowTaskCompletedEvent event) {
        // 任务完成时的处理逻辑
        System.out.println("任务完成: " + event.getTaskId());
    }
}
```

---

## 数据库表结构

### 1. workflow_definition（流程定义）
```sql
CREATE TABLE workflow_definition (
    id UUID PRIMARY KEY,
    v_name VARCHAR,           -- 流程名称
    v_code VARCHAR UNIQUE,    -- 流程编码
    v_category VARCHAR,       -- 流程分类
    j_content JSON,           -- 流程JSON定义
    i_version INTEGER,        -- 版本号
    dict_workflow_status VARCHAR,  -- 状态
    id_at_app_module UUID,    -- 关联模块ID
    v_description TEXT        -- 描述
);
```

### 2. workflow_node（流程节点）
```sql
CREATE TABLE workflow_node (
    id UUID PRIMARY KEY,
    id_at_workflow_definition UUID,  -- 流程定义ID
    v_name VARCHAR,                   -- 节点名称
    v_code VARCHAR,                   -- 节点编码
    dict_node_type VARCHAR,           -- 节点类型
    dict_assignee_type VARCHAR,       -- 办理人类型
    ids_assignee TEXT[],              -- 办理人ID列表
    j_form_config JSON,               -- 表单配置
    j_button_config JSON,             -- 按钮配置
    i_order INTEGER                   -- 排序
);
```

### 3. workflow_transition（流程流转）
```sql
CREATE TABLE workflow_transition (
    id UUID PRIMARY KEY,
    id_at_workflow_definition UUID,  -- 流程定义ID
    id_from_node UUID,                -- 源节点ID
    id_to_node UUID,                  -- 目标节点ID
    v_name VARCHAR,                   -- 流转名称
    v_condition VARCHAR,              -- 流转条件
    i_order INTEGER                   -- 排序
);
```

### 4. workflow_instance（流程实例）
```sql
CREATE TABLE workflow_instance (
    id UUID PRIMARY KEY,
    id_at_workflow_definition UUID,  -- 流程定义ID
    v_business_key VARCHAR,          -- 业务单据ID
    id_at_app_module UUID,           -- 业务模块ID
    dict_instance_status VARCHAR,    -- 状态
    id_starter UUID,                 -- 发起人ID
    t_start_time TIMESTAMP,          -- 开始时间
    t_end_time TIMESTAMP,            -- 结束时间
    j_variables JSON                 -- 流程变量
);
```

### 5. workflow_task（任务）
```sql
CREATE TABLE workflow_task (
    id UUID PRIMARY KEY,
    id_at_workflow_instance UUID,    -- 流程实例ID
    id_at_workflow_node UUID,        -- 节点ID
    v_name VARCHAR,                  -- 任务名称
    dict_task_status VARCHAR,        -- 状态
    id_assignee UUID,                -- 当前办理人ID
    id_prev_assignee UUID,           -- 上一办理人ID
    t_claim_time TIMESTAMP,          -- 签收时间
    t_complete_time TIMESTAMP,       -- 完成时间
    v_comment TEXT,                  -- 审批意见
    v_action VARCHAR,                -- 操作
    j_form_data JSON                 -- 表单数据
);
```

### 6. workflow_history（流程历史）
```sql
CREATE TABLE workflow_history (
    id UUID PRIMARY KEY,
    id_at_workflow_instance UUID,    -- 流程实例ID
    id_at_workflow_task UUID,        -- 任务ID
    v_action_type VARCHAR,           -- 操作类型
    id_operator UUID,                -- 操作人ID
    t_operate_time TIMESTAMP,        -- 操作时间
    v_comment TEXT,                  -- 意见
    j_snapshot JSON                  -- 数据快照
);
```

---

## 测试

参见 `muyun-boot/src/test/java/net/ximatai/muyun/test/workflow/WorkflowTest.java`

运行测试：
```bash
./gradlew test --tests WorkflowTest
```

---

## 注意事项

1. **流程定义必须先激活**：只有状态为 `active` 的流程才能启动实例
2. **开始节点唯一**：每个流程定义只能有一个开始节点
3. **审批节点必须指定办理人**：审批节点必须配置办理人类型和办理人列表
4. **流程变量用途**：流程变量可用于条件判断、动态办理人分配等场景
5. **任务权限校验**：完成任务时会校验当前用户是否为任务办理人
6. **事务一致性**：业务数据更新和流程操作建议在同一事务中完成

---

## 常见问题

### Q1: 如何实现会签（多人审批）？
A: 会签功能正在开发中，当前可以通过创建多个审批节点串联实现。

### Q2: 如何实现驳回到任意节点？
A: 创建从当前节点到目标节点的流转关系，流转名称设置为"驳回"。

### Q3: 如何查看流程实例的完整历史？
A: 调用 `/api/platform/workflow_instance/history/{instanceId}` 接口。

### Q4: 如何实现并行审批（多个分支同时执行）？
A: 并行节点功能正在开发中。

### Q5: 流程变量有什么用？
A: 流程变量用于：
- 条件流转判断
- 动态计算办理人
- 业务数据传递
- 流程监控和统计

---

## 更新日志

### v0.1.13
- 初始版本
- 支持基础流程定义、节点、流转
- 支持流程启动和任务完成
- 支持待办任务查询
- 集成消息通知

---

## 技术支持

如有问题，请联系开发团队或提交 Issue。

