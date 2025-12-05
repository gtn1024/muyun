package net.ximatai.muyun.workflow.controller;

import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import net.ximatai.muyun.ability.IChildrenAbility;
import net.ximatai.muyun.ability.IReferableAbility;
import net.ximatai.muyun.ability.ISoftDeleteAbility;
import net.ximatai.muyun.ability.ITreeAbility;
import net.ximatai.muyun.ability.curd.std.IQueryAbility;
import net.ximatai.muyun.base.BaseBusinessTable;
import net.ximatai.muyun.core.db.PresetColumn;
import net.ximatai.muyun.core.exception.MuYunException;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.model.ChildTableInfo;
import net.ximatai.muyun.model.QueryItem;
import net.ximatai.muyun.platform.ScaffoldForPlatform;
import net.ximatai.muyun.platform.ability.IModuleRegisterAbility;
import net.ximatai.muyun.platform.controller.DictCategoryController;
import net.ximatai.muyun.platform.controller.ModuleController;
import net.ximatai.muyun.platform.model.Dict;
import net.ximatai.muyun.platform.model.DictCategory;
import net.ximatai.muyun.platform.model.ModuleAction;
import net.ximatai.muyun.platform.model.ModuleConfig;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.ximatai.muyun.platform.PlatformConst.BASE_PATH;
import static net.ximatai.muyun.workflow.controller.WorkflowDefinitionController.MODULE_ALIAS;

@Startup
@Tag(description = "工作流定义")
@Path(BASE_PATH + "/" + MODULE_ALIAS)
public class WorkflowDefinitionController extends ScaffoldForPlatform
    implements ITreeAbility, IQueryAbility, IChildrenAbility, IModuleRegisterAbility, ISoftDeleteAbility, IReferableAbility {

    public final static String MODULE_ALIAS = "workflow_definition";

    @Inject
    WorkflowNodeController workflowNodeController;

    @Inject
    WorkflowTransitionController workflowTransitionController;

    @Inject
    DictCategoryController dictCategoryController;

    @Inject
    ModuleController moduleController;

    @Override
    public String getMainTable() {
        return "workflow_definition";
    }

    @Override
    protected void afterInit() {
        super.afterInit();

        // 注册工作流状态字典
        dictCategoryController.putDictCategory(
            new DictCategory("dict_workflow_status", "platform_dir", "工作流状态", 0).setDictList(
                new Dict("draft", "草稿"),
                new Dict("active", "已激活"),
                new Dict("suspended", "已暂停"),
                new Dict("archived", "已归档")
            ), false);

        // 注册节点类型字典
        dictCategoryController.putDictCategory(
            new DictCategory("dict_node_type", "platform_dir", "节点类型", 0).setDictList(
                new Dict("start", "开始节点"),
                new Dict("end", "结束节点"),
                new Dict("approve", "审批节点"),
                new Dict("countersign", "会签节点"),
                new Dict("condition", "条件节点"),
                new Dict("parallel", "并行节点")
            ), false);

        // 注册办理人类型字典
        dictCategoryController.putDictCategory(
            new DictCategory("dict_assignee_type", "platform_dir", "办理人类型", 0).setDictList(
                new Dict("user", "指定人员"),
                new Dict("role", "角色"),
                new Dict("department", "部门"),
                new Dict("starter", "发起人"),
                new Dict("superior", "上级"),
                new Dict("dynamic", "动态")
            ), false);
    }

    @Override
    public void fitOut(TableWrapper wrapper) {
        wrapper
            .setComment("工作流定义")
            .setPrimaryKey(PresetColumn.ID_POSTGRES_UUID)
            .setInherit(BaseBusinessTable.TABLE)
            .addColumn("v_name", "流程名称")
            .addColumn("v_code", "流程编码")
            .addColumn("v_category", "流程分类")
            .addColumn("j_content", "流程JSON定义")
            .addColumn("i_version", "版本号", "1")
            .addColumn("dict_workflow_status", "状态", "'draft'")
            .addColumn("id_at_app_module", "关联模块ID")
            .addColumn("v_description", "描述")
            .addIndex("v_code", true);
    }

    @Override
    public List<ChildTableInfo> getChildren() {
        return List.of(
            workflowNodeController.toChildTable("id_at_workflow_definition")
                .setAutoDelete()
                .setChildAlias("nodes"),
            workflowTransitionController.toChildTable("id_at_workflow_definition")
                .setAutoDelete()
                .setChildAlias("transitions")
        );
    }

    @Override
    public void beforeCreate(Map body) {
        String code = (String) body.get("v_code");
        if (code != null) {
            List result = this.view(null, null, true, null, Map.of("v_code", code)).getList();
            if (!result.isEmpty()) {
                throw new MuYunException("流程编码已存在");
            }
        }
    }

    @POST
    @Path("/activate/{id}")
    @Operation(summary = "激活流程")
    public int activate(@PathParam("id") String id) {
        // 校验流程定义完整性
        Map<String, ?> definition = view(id);
        validateDefinition(definition);

        return getDB().updateItem(getSchemaName(), getMainTable(), Map.of(
            "id", id,
            "dict_workflow_status", "active"
        ));
    }

    @POST
    @Path("/suspend/{id}")
    @Operation(summary = "暂停流程")
    public int suspend(@PathParam("id") String id) {
        return getDB().updateItem(getSchemaName(), getMainTable(), Map.of(
            "id", id,
            "dict_workflow_status", "suspended"
        ));
    }

    @POST
    @Path("/archive/{id}")
    @Operation(summary = "归档流程")
    public int archive(@PathParam("id") String id) {
        return getDB().updateItem(getSchemaName(), getMainTable(), Map.of(
            "id", id,
            "dict_workflow_status", "archived"
        ));
    }

    @POST
    @Path("/copy/{id}")
    @Operation(summary = "复制流程")
    public String copy(@PathParam("id") String id) {
        Map<String, ?> source = view(id);

        HashMap newDef = new HashMap();

        // 复制主表数据
        newDef.put("v_name", source.get("v_name") + " (副本)");
        newDef.put("v_code", source.get("v_code") + "_copy_" + System.currentTimeMillis());
        newDef.put("v_category", source.get("v_category"));
        newDef.put("j_content", source.get("j_content"));
        newDef.put("i_version", 1);
        newDef.put("dict_workflow_status", "draft");
        newDef.put("v_description", source.get("v_description"));

        return create(newDef);
    }

    private void validateDefinition(Map<String, ?> definition) {
        // TODO: 实现流程定义的完整性校验
        // 1. 至少有一个开始节点
        // 2. 至少有一个结束节点
        // 3. 所有节点都有流转关系
        // 4. 没有孤立的节点
    }

    @Override
    public ModuleConfig getModuleConfig() {
        return ModuleConfig.ofName("工作流管理")
            .setAlias(MODULE_ALIAS)
            .setTable(getMainTable())
            .setUrl("platform/workflow/definition/index")
            .addAction(new ModuleAction("activate", "激活流程", ModuleAction.TypeLike.UPDATE))
            .addAction(new ModuleAction("suspend", "暂停流程", ModuleAction.TypeLike.UPDATE))
            .addAction(new ModuleAction("archive", "归档流程", ModuleAction.TypeLike.UPDATE))
            .addAction(new ModuleAction("copy", "复制流程", ModuleAction.TypeLike.CREATE));
    }

    @Override
    public ModuleController getModuleController() {
        return moduleController;
    }

    @Override
    public List<QueryItem> queryItemList() {
        return List.of(
            QueryItem.of("v_code")
        );
    }
}
