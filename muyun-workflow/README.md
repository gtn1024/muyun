# MuYun Workflow Module

MuYun 轻代码平台的工作流模块，提供完整的流程定义、流转、审批功能。

## 特性

- **轻量级设计**：遵循 MuYun 平台架构，简单易用
- **灵活的流程定义**：支持开始、结束、审批、会签、条件、并行等多种节点类型
- **多种办理人分配**：支持指定人员、角色、部门、发起人、上级、动态分配
- **完整的历史记录**：记录每次操作的详细信息
- **消息通知集成**：自动通知待办任务
- **业务集成友好**：通过 `IWorkflowAbility` 接口轻松集成工作流

## 模块结构

```
muyun-workflow/
├── src/main/java/net/ximatai/muyun/workflow/
│   ├── controller/                     # 控制器层
│   │   ├── WorkflowDefinitionController.java    # 流程定义管理
│   │   ├── WorkflowNodeController.java          # 流程节点管理
│   │   ├── WorkflowTransitionController.java    # 流程流转管理
│   │   ├── WorkflowInstanceController.java      # 流程实例管理
│   │   ├── WorkflowTaskController.java          # 任务管理
│   │   └── WorkflowHistoryController.java       # 流程历史
│   ├── engine/                         # 引擎层
│   │   └── WorkflowEngine.java                  # 工作流引擎
│   ├── ability/                        # 能力接口
│   │   └── IWorkflowAbility.java               # 工作流集成接口
│   └── example/                        # 示例
│       └── LeaveRequestController.java         # 请假申请示例
└── src/test/java/net/ximatai/muyun/workflow/
    └── WorkflowTest.java                       # 单元测试
```

## 数据库表结构

### 1. workflow_definition（流程定义）
主表，存储流程定义信息

| 字段 | 类型 | 说明 |
|-----|------|------|
| id | UUID | 主键 |
| v_name | VARCHAR | 流程名称 |
| v_code | VARCHAR | 流程编码（唯一） |
| v_category | VARCHAR | 流程分类 |
| j_content | JSON | 流程JSON定义 |
| i_version | INTEGER | 版本号 |
| dict_workflow_status | VARCHAR | 状态（draft/active/suspended/archived） |
| id_at_app_module | UUID | 关联模块ID |
| v_description | TEXT | 描述 |

### 2. workflow_node（流程节点）
子表，存储流程节点信息

| 字段 | 类型 | 说明 |
|-----|------|------|
| id | UUID | 主键 |
| id_at_workflow_definition | UUID | 流程定义ID |
| v_name | VARCHAR | 节点名称 |
| v_code | VARCHAR | 节点编码 |
| dict_node_type | VARCHAR | 节点类型 |
| dict_assignee_type | VARCHAR | 办理人类型 |
| ids_assignee | TEXT[] | 办理人ID列表 |
| j_form_config | JSON | 表单配置 |
| j_button_config | JSON | 按钮配置 |
| i_order | INTEGER | 排序 |

### 3. workflow_transition（流程流转）
子表，定义节点之间的流转关系

| 字段 | 类型 | 说明 |
|-----|------|------|
| id | UUID | 主键 |
| id_at_workflow_definition | UUID | 流程定义ID |
| id_from_node | UUID | 源节点ID |
| id_to_node | UUID | 目标节点ID |
| v_name | VARCHAR | 流转名称（如"同意"、"驳回"） |
| v_condition | VARCHAR | 流转条件表达式 |
| i_order | INTEGER | 排序 |

### 4. workflow_instance（流程实例）
主表，存储运行中的流程实例

| 字段 | 类型 | 说明 |
|-----|------|------|
| id | UUID | 主键 |
| id_at_workflow_definition | UUID | 流程定义ID |
| v_business_key | VARCHAR | 业务单据ID |
| id_at_app_module | UUID | 业务模块ID |
| dict_instance_status | VARCHAR | 状态（running/completed/terminated/suspended） |
| id_starter | UUID | 发起人ID |
| t_start_time | TIMESTAMP | 开始时间 |
| t_end_time | TIMESTAMP | 结束时间 |
| j_variables | JSON | 流程变量 |

### 5. workflow_task（任务）
主表，存储待办/已办任务

| 字段 | 类型 | 说明 |
|-----|------|------|
| id | UUID | 主键 |
| id_at_workflow_instance | UUID | 流程实例ID |
| id_at_workflow_node | UUID | 节点ID |
| v_name | VARCHAR | 任务名称 |
| dict_task_status | VARCHAR | 状态（pending/completed/rejected/transferred/cancelled） |
| id_assignee | UUID | 当前办理人ID |
| id_prev_assignee | UUID | 上一办理人ID |
| t_claim_time | TIMESTAMP | 签收时间 |
| t_complete_time | TIMESTAMP | 完成时间 |
| v_comment | TEXT | 审批意见 |
| v_action | VARCHAR | 操作 |
| j_form_data | JSON | 表单数据 |

### 6. workflow_history（流程历史）
主表，记录流程操作历史

| 字段 | 类型 | 说明 |
|-----|------|------|
| id | UUID | 主键 |
| id_at_workflow_instance | UUID | 流程实例ID |
| id_at_workflow_task | UUID | 任务ID |
| v_action_type | VARCHAR | 操作类型 |
| id_operator | UUID | 操作人ID |
| t_operate_time | TIMESTAMP | 操作时间 |
| v_comment | TEXT | 意见 |
| j_snapshot | JSON | 数据快照 |

## API 接口

### 流程定义管理

- `POST /api/platform/workflow_definition/create` - 创建流程定义
- `POST /api/platform/workflow_definition/update/{id}` - 更新流程定义
- `GET /api/platform/workflow_definition/view/{id}` - 查看流程定义
- `GET /api/platform/workflow_definition/delete/{id}` - 删除流程定义（软删除）
- `POST /api/platform/workflow_definition/activate/{id}` - 激活流程
- `POST /api/platform/workflow_definition/suspend/{id}` - 暂停流程
- `POST /api/platform/workflow_definition/archive/{id}` - 归档流程
- `POST /api/platform/workflow_definition/copy/{id}` - 复制流程

### 流程实例管理

- `GET /api/platform/workflow_instance/view/{id}` - 查看流程实例
- `GET /api/platform/workflow_instance/history/{id}` - 获取流程历史
- `GET /api/platform/workflow_instance/diagram/{id}` - 获取流程图状态

### 任务管理

- `GET /api/platform/workflow_task/todo` - 我的待办
- `GET /api/platform/workflow_task/done` - 我的已办
- `POST /api/platform/workflow_task/complete/{id}` - 完成任务
- `POST /api/platform/workflow_task/transfer/{id}?toUser={userId}` - 转办任务
- `POST /api/platform/workflow_task/claim/{id}` - 签收任务

## 业务集成指南

### 1. 实现 IWorkflowAbility 接口

```java
@Startup
@Tag(description = "请假申请")
@Path(BASE_PATH + "/leave_request")
public class LeaveRequestController extends ScaffoldForPlatform
    implements IModuleRegisterAbility, IQueryAbility, IWorkflowAbility {

    @Inject
    WorkflowEngine workflowEngine;

    @Override
    public String getWorkflowCode() {
        return "leave_approval"; // 对应的工作流定义编码
    }

    @POST
    @Path("/submit/{id}")
    public String submitForApproval(@PathParam("id") String id) {
        // 启动工作流
        String instanceId = workflowEngine.startProcess(
            getWorkflowCode(),
            id,
            getModuleConfig().getAlias(),
            getUser().getId(),
            getWorkflowVariables(id)
        );

        // 更新业务状态
        this.update(id, Map.of(
            "dict_status", "approving",
            "id_at_workflow_instance", instanceId
        ));

        return instanceId;
    }

    @Override
    public void afterWorkflowApproved(String id) {
        // 审批通过后的业务逻辑
        this.update(id, Map.of("dict_status", "approved"));
    }

    @Override
    public void afterWorkflowRejected(String id) {
        // 审批驳回后的业务逻辑
        this.update(id, Map.of("dict_status", "rejected"));
    }
}
```

### 2. 创建流程定义

在系统中创建对应的工作流定义，包括：

1. 定义流程基本信息（名称、编码、分类）
2. 添加节点（开始、审批、结束等）
3. 定义流转关系（节点之间的连接）
4. 配置办理人规则
5. 激活流程

### 3. 流程变量

通过 `getWorkflowVariables()` 方法提供流程变量，这些变量可用于：

- 条件判断（流转条件）
- 动态办理人分配
- 业务逻辑处理

```java
@Override
public Map<String, Object> getWorkflowVariables(String id) {
    Map<String, ?> data = view(id);

    Map<String, Object> variables = new HashMap<>();
    variables.put("starterId", getUser().getId());
    variables.put("amount", data.get("n_amount"));
    variables.put("applicantDept", getUser().getDepartmentId());

    return variables;
}
```

## 节点类型说明

### 1. 开始节点（start）
- 流程的起点
- 每个流程只能有一个开始节点
- 不需要指定办理人

### 2. 结束节点（end）
- 流程的终点
- 可以有多个结束节点（不同结束状态）
- 不需要指定办理人

### 3. 审批节点（approve）
- 需要人工审批的节点
- 必须指定办理人
- 支持同意、驳回等操作

### 4. 会签节点（countersign）
- 多人并行审批
- 支持全部同意、一票否决等策略
- TODO: 待实现

### 5. 条件节点（condition）
- 根据条件自动流转
- 不需要人工干预
- TODO: 待实现

### 6. 并行节点（parallel）
- 同时执行多个分支
- 等待所有分支完成后合并
- TODO: 待实现

## 办理人类型

- **user**：指定人员
- **role**：角色（查询角色下的所有用户）
- **department**：部门（查询部门下的所有用户）
- **starter**：发起人
- **superior**：上级（发起人的上级）
- **dynamic**：动态（根据流程变量计算）

## 完整示例

参见 `LeaveRequestController.java`，这是一个完整的请假申请业务集成示例。

## 扩展开发

### 1. 自定义条件表达式求值
在 `WorkflowEngine.evaluateCondition()` 方法中实现

### 2. 自定义办理人计算逻辑
在 `WorkflowEngine.calculateAssignees()` 方法中扩展

### 3. 自定义流程监听器
通过 EventBus 监听流程事件

## 测试

运行测试：
```bash
./gradlew :muyun-workflow:test
```

## 依赖

- muyun-core
- muyun-platform

## TODO

- [ ] 会签节点实现
- [ ] 条件节点实现
- [ ] 并行节点实现
- [ ] 子流程支持
- [ ] 定时任务支持
- [ ] 流程版本管理
- [ ] 流程监控面板
- [ ] 更复杂的条件表达式引擎
- [ ] 流程图可视化支持

## 贡献

欢迎提交 Issue 和 Pull Request！
