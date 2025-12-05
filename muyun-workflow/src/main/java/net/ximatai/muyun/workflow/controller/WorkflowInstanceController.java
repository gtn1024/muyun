package net.ximatai.muyun.workflow.controller;

import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import net.ximatai.muyun.ability.IReferableAbility;
import net.ximatai.muyun.ability.IReferenceAbility;
import net.ximatai.muyun.base.BaseBusinessTable;
import net.ximatai.muyun.core.db.PresetColumn;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.model.PageResult;
import net.ximatai.muyun.model.ReferenceInfo;
import net.ximatai.muyun.platform.ScaffoldForPlatform;
import net.ximatai.muyun.platform.ability.IModuleRegisterAbility;
import net.ximatai.muyun.platform.controller.DictController;
import net.ximatai.muyun.platform.controller.ModuleController;
import net.ximatai.muyun.platform.controller.UserInfoController;
import net.ximatai.muyun.platform.model.ModuleConfig;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

import static net.ximatai.muyun.platform.PlatformConst.BASE_PATH;
import static net.ximatai.muyun.workflow.controller.WorkflowInstanceController.MODULE_ALIAS;

@Startup
@Tag(description = "工作流实例")
@Path(BASE_PATH + "/" + MODULE_ALIAS)
public class WorkflowInstanceController extends ScaffoldForPlatform
    implements IReferableAbility, IReferenceAbility, IModuleRegisterAbility {

    public final static String MODULE_ALIAS = "workflow_instance";

    @Inject
    WorkflowDefinitionController workflowDefinitionController;

    @Inject
    UserInfoController userInfoController;

    @Inject
    DictController dictController;

    @Inject
    ModuleController moduleController;

    @Inject
    WorkflowHistoryController workflowHistoryController;

    @Override
    public String getMainTable() {
        return "workflow_instance";
    }

    @Override
    public void fitOut(TableWrapper wrapper) {
        wrapper
            .setComment("工作流实例")
            .setPrimaryKey(PresetColumn.ID_POSTGRES_UUID)
            .setInherit(BaseBusinessTable.TABLE)
            .addColumn("id_at_workflow_definition", "流程定义ID")
            .addColumn("v_business_key", "业务单据ID")
            .addColumn("id_at_app_module", "业务模块ID")
            .addColumn("dict_instance_status", "实例状态", "'running'")
            .addColumn("id_starter", "发起人ID")
            .addColumn("t_start_time", "开始时间")
            .addColumn("t_end_time", "结束时间")
            .addColumn("j_variables", "流程变量")
            .addIndex("id_at_workflow_definition", false)
            .addIndex("v_business_key", false)
            .addIndex("id_starter", false);
    }

    @Override
    public List<ReferenceInfo> getReferenceList() {
        return List.of(
            workflowDefinitionController.toReferenceInfo("id_at_workflow_definition")
                .add("v_name", "v_workflow_name"),
            userInfoController.toReferenceInfo("id_starter")
                .add("v_name", "v_starter_name"),
            dictController.toReferenceInfo("dict_instance_status")
                .add("v_name", "v_status_name")
        );
    }

    @GET
    @Path("/history/{id}")
    @Operation(summary = "获取流程历史")
    public PageResult<Map> getHistory(@PathParam("id") String instanceId) {
        return workflowHistoryController.view(null, null, true, null, Map.of("id_at_workflow_instance", instanceId));
    }

    @GET
    @Path("/diagram/{id}")
    @Operation(summary = "获取流程图状态")
    public Map<String, Object> getDiagram(@PathParam("id") String instanceId) {
        // TODO: 返回流程图数据，包含节点、流转、当前节点高亮信息
        Map<String, ?> instance = view(instanceId);
        String definitionId = (String) instance.get("id_at_workflow_definition");
        Map<String, ?> definition = workflowDefinitionController.view(definitionId);

        return Map.of(
            "definition", definition,
            "instance", instance,
            "currentNodes", List.of() // TODO: 查询当前活动节点
        );
    }

    @Override
    public ModuleConfig getModuleConfig() {
        return ModuleConfig.ofName("流程实例")
            .setAlias(MODULE_ALIAS)
            .setTable(getMainTable())
            .setUrl("platform/workflow/instance/index");
    }

    @Override
    public ModuleController getModuleController() {
        return moduleController;
    }
}
