/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.metl.core.persist;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.exception.IoException;
import org.jumpmind.metl.core.model.Agent;
import org.jumpmind.metl.core.model.AgentDeploymentSummary;
import org.jumpmind.metl.core.model.Component;
import org.jumpmind.metl.core.model.Flow;
import org.jumpmind.metl.core.model.Model;
import org.jumpmind.metl.core.model.ModelAttribute;
import org.jumpmind.metl.core.model.Plugin;
import org.jumpmind.metl.core.model.ProjectVersion;
import org.jumpmind.metl.core.model.ProjectVersionDependency;
import org.jumpmind.metl.core.model.Resource;
import org.jumpmind.metl.core.security.ISecurityService;
import org.jumpmind.persist.IPersistenceManager;
import org.jumpmind.symmetric.io.data.DbExport;
import org.jumpmind.symmetric.io.data.DbExport.Format;

public class ConfigurationSqlService extends AbstractConfigurationService {

    protected IDatabasePlatform databasePlatform;

    public ConfigurationSqlService(ISecurityService securityService, IDatabasePlatform databasePlatform,
            IPersistenceManager persistenceManager, String tablePrefix) {
        super(securityService, persistenceManager, tablePrefix);
        this.databasePlatform = databasePlatform;
    }
    
    @Override
    public void doInBackground() {
    }

    @Override
    public boolean isInstalled() {
        return databasePlatform.getTableFromCache(tableName(Component.class), false) != null;
    }

    @Override
    public List<Plugin> findActivePlugins() {
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        return template.query(String.format("select distinct artifact_group, artifact_name, load_order from ("
                + "select v.artifact_group, v.artifact_name, p.load_order from %1$s_project_version_definition_plugin v left join "
                + " %1$s_plugin p on v.artifact_name=p.artifact_name and v.artifact_group=p.artifact_group where v.enabled=1) "
                + " order by load_order, artifact_group, artifact_name", tablePrefix), new ISqlRowMapper<Plugin>() {
                    public Plugin mapRow(Row row) {
                        return new Plugin(row.getString("artifact_group"), row.getString("artifact_name"), row.getInt("load_order"));
                    }
                });
    }

    @Override
    public List<Plugin> findUnusedPlugins() {
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        return template.query(String.format(
                "select artifact_group, artifact_name, artifact_version from %1$s_plugin p where not exists "
                        + "(select 1 from %1$s_project_version_definition_plugin v where "
                        + "v.artifact_name=p.artifact_name and v.artifact_group=p.artifact_group and v.artifact_version=p.artifact_version) ",
                tablePrefix), new ISqlRowMapper<Plugin>() {
                    public Plugin mapRow(Row row) {
                        return new Plugin(row.getString("artifact_group"), row.getString("artifact_name"), row.getString("artifact_version"));
                    }
                });
    }

    @Override
    public boolean isDeployed(Flow flow) {
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        return template.queryForInt(String.format("select count(*) from %1$s_agent_deployment where flow_id = ? ", tablePrefix),
                flow.getId()) > 0;
    }

    @Override
    public List<String> findAllProjectVersionIds() {
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        return template.query(String.format("select id from %1$s_project_version where deleted=0", tablePrefix), new StringMapper());
    }

    public List<AgentDeploymentSummary> findAgentDeploymentSummary(String agentId) {
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        return template.query(String.format(
                "select p.name as project_name, v.version_label, '%2$s' as type, " +
                "d.id, d.name, d.start_type, d.log_level, d.start_expression, d.status, f.id as flow_id " +
                "from %1$s_agent_deployment d " +
                "inner join %1$s_flow f on f.id = d.flow_id " +
                "inner join %1$s_project_version v on v.id = f.project_version_id " +
                "inner join %1$s_project p on p.id = v.project_id " +
                "where d.agent_id = ? " +
                "union " +
                "select distinct p.name, v.version_label, '%3$s', " +
                "r.id, r.name, null, null, null, null, null as flow_id " +
                "from %1$s_agent_deployment d " +
                "inner join %1$s_flow f on f.id = d.flow_id " +
                "inner join %1$s_project_version v on v.id = f.project_version_id " +
                "inner join %1$s_project p on p.id = v.project_id " +
                "inner join %1$s_resource r on r.project_version_id = v.id " +
                "where d.agent_id = ? and r.deleted=0 " +
                "union " +
                "select distinct p.name, v.version_label, '%3$s', " +
                "r.id, r.name, null, null, null, null, null as flow_id " +
                "from %1$s_agent_deployment d " +
                "inner join %1$s_flow f on f.id = d.flow_id " +
                "inner join %1$s_project_version_dependency d on d.project_version_id = f.project_version_id " +
                "inner join %1$s_project_version v on v.id = d.target_project_version_id " +
                "inner join %1$s_project p on p.id = v.project_id " +
                "inner join %1$s_resource r on r.project_version_id = v.id " +
                "where d.agent_id = ? and r.deleted=0 order by 5 "    ,
                tablePrefix, AgentDeploymentSummary.TYPE_FLOW, AgentDeploymentSummary.TYPE_RESOURCE), 
                new ISqlRowMapper<AgentDeploymentSummary>() {
                    public AgentDeploymentSummary mapRow(Row row) {
                        AgentDeploymentSummary summary = new AgentDeploymentSummary();
                        summary.setProjectName(row.getString("project_name") + " (" + row.getString("version_label") + ")");
                        summary.setType(row.getString("type"));
                        summary.setName(row.getString("name"));
                        summary.setId(row.getString("id"));
                        summary.setStatus(row.getString("status"));
                        summary.setStartType(row.getString("start_type"));
                        summary.setLogLevel(row.getString("log_level"));
                        summary.setStartExpression(row.getString("start_expression"));
                        summary.setArtifactId(row.getString("flow_id", false));
                        return summary;
                    }
                }, agentId, agentId, agentId);
    }

    @Override
    protected List<ModelAttribute> findAllAttributesForModel(String modelId) {
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        String sql = String.format(
                "select * from %1$s_model_attribute where entity_id in (select id from %1$s_model_entity where model_id=?)", tablePrefix);
        return template.query(sql, new ISqlRowMapper<ModelAttribute>() {
            @Override
            public ModelAttribute mapRow(Row row) {
                return persistenceManager.map(row, ModelAttribute.class, null, null, tableName(ModelAttribute.class));
            }
        }, new Object[] { modelId });
    }

    @Override
    public String export(Agent agent) {
        try {
            StringBuilder out = new StringBuilder();

            /* @formatter:off */
            String[][] CONFIG = {
                    {"AGENT", "WHERE ID='%2$s' AND DELETED=0"," ORDER BY ID",                                                                                                                                                                              },
                    {"AGENT_DEPLOYMENT", "WHERE AGENT_ID='%2$s'"," ORDER BY ID",                                                                                                                                                                                                                                },
                    {"AGENT_DEPLOYMENT_PARAMETER", "WHERE AGENT_DEPLOYMENT_ID in (SELECT ID FROM %1$s_AGENT_DEPLOYMENT WHERE AGENT_ID='%2$s')"," ORDER BY ID",                                                                                                                                                                                                                         },
                    {"AGENT_PARAMETER", "WHERE AGENT_ID='%2$s'"," ORDER BY ID",                                                                                                                                                                                                            },
                    {"AGENT_RESOURCE_SETTING", "WHERE AGENT_ID='%2$s'"," ORDER BY RESOURCE_ID, NAME",                                                                                                                                                       },
            };
            /* @formatter:on */

            for (int i = CONFIG.length - 1; i >= 0; i--) {
                String[] entry = CONFIG[i];
                out.append(String.format("DELETE FROM %s_%s %s;\n", tablePrefix, entry[0],
                        String.format(entry[1], tablePrefix, agent.getId()).replace("AND DELETED=0", "")));
            }

            for (int i = 0; i < CONFIG.length; i++) {
                String[] entry = CONFIG[i];
                out.append(export(entry[0], entry[1], entry[2], agent));
            }

            return out.toString();
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    protected String getComponentIds(Flow flow) {
        StringBuilder componentIds = new StringBuilder();
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        List<Row> results = template.query(String.format(
                "SELECT ID, SHARED FROM %1$s_COMPONENT WHERE ID IN (SELECT COMPONENT_ID FROM %1$s_FLOW_STEP WHERE FLOW_ID='%2$s')",
                tablePrefix, flow.getId()));
        for (Row row : results) {
            componentIds.append("'");
            componentIds.append(row.get("ID"));
            componentIds.append("'");
            componentIds.append(",");
            if (row.getString("SHARED").equals("1")) {
                throw new UnsupportedOperationException("Cannot export flows that utilize shared components");
            }
        }
        componentIds.deleteCharAt(componentIds.length() - 1);
        return componentIds.toString();
    }

    protected String export(String table, String where, String orderBy, Agent agent) throws IOException {
        DbExport export = new DbExport(databasePlatform);
        export.setWhereClause(String.format(where + orderBy, tablePrefix, agent.getId()));
        export.setFormat(Format.SQL);
        export.setUseQuotedIdentifiers(false);
        export.setNoCreateInfo(true);
        return export.exportTables(new String[] { String.format("%s_%s", tablePrefix, table) });
    }

    protected String export(String table, String where, String orderBy, ProjectVersion projectVersion, String[] columnsToExclude)
            throws IOException {
        DbExport export = new DbExport(databasePlatform);
        export.setWhereClause(String.format(where + orderBy, tablePrefix, projectVersion.getId(), projectVersion.getProjectId()));
        export.setFormat(Format.SQL);
        export.setUseQuotedIdentifiers(false);
        export.setNoCreateInfo(true);
        export.setExcludeColumns(columnsToExclude);
        return export.exportTables(new String[] { String.format("%s_%s", tablePrefix, table) });
    }

    protected String export(String table, String where, String orderBy, ProjectVersion projectVersion, Flow flow, String componentIds,
            String[] columnsToExclude) throws IOException {
        DbExport export = new DbExport(databasePlatform);
        export.setWhereClause(String.format(where + orderBy, tablePrefix, projectVersion.getId(), projectVersion.getProjectId(), flow.getId(),
                componentIds));
        export.setFormat(Format.SQL);
        export.setUseQuotedIdentifiers(false);
        export.setNoCreateInfo(true);
        export.setExcludeColumns(columnsToExclude);
        return export.exportTables(new String[] { String.format("%s_%s", tablePrefix, table) });
    }

    @Override
    public boolean isUserLoginEnabled() {
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        return template.queryForInt(String.format("select count(*) from %1$s_user where password is not null", tablePrefix)) > 0;
    }

    @Override
    protected boolean doesTableExist(Class<?> clazz) {
        return databasePlatform.getTableFromCache(tableName(clazz), false) != null;
    }

    @Override
    public List<Component> findDependentSharedComponents(String flowId) {
        List<Component> sharedComponents = new ArrayList<Component>();
        final String SHARED_COMPONENTS_BY_FLOW_SQL = "select distinct c.id from %1$s_flow_step fs inner join %1$s_component c on fs.component_id = c.id where fs.flow_id = '%2$s' and c.shared=1";
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        List<Row> ids = template.query(String.format(SHARED_COMPONENTS_BY_FLOW_SQL, tablePrefix, flowId));
        for (Row row : ids) {
            sharedComponents.add(this.findComponent(row.getString("id"), false));
        }
        return sharedComponents;
    }

    @Override
    public List<Resource> findDependentResources(String flowId) {

        List<Resource> resources = new ArrayList<Resource>();
        final String RESOURCES_BY_FLOW_SQL = "select distinct c.resource_id from %1$s_flow_step fs inner join %1$s_component c on fs.component_id = c.id where fs.flow_id = '%2$s' and resource_id is not null " +
                "union select distinct cs.value from metl_flow_step fs inner join metl_component c on fs.component_id = c.id inner join metl_component_setting cs on cs.component_id = c.id where fs.flow_id = '%2$s' " +
                "and cs.name in ('source.resource','target.resource')";
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        List<Row> ids = template.query(String.format(RESOURCES_BY_FLOW_SQL, tablePrefix, flowId));
        for (Row row : ids) {
            resources.add(this.findResource(row.getString("resource_id")));
        }
        return resources;
    }

    @Override
    public List<Model> findDependentModels(String flowId) {
        List<Model> models = new ArrayList<Model>();
        final String MODELS_BY_FLOW_SQL = "select distinct model_id from  "
                + "(select distinct output_model_id as model_id from %1$s_flow_step fs inner join %1$s_component c on fs.component_id = c.id where fs.flow_id = '%2$s' and output_model_id is not null union "
                + " select distinct input_model_id as model_id from %1$s_flow_step fs inner join %1$s_component c on fs.component_id = c.id where fs.flow_id = '%2$s' and input_model_id is not null)";
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        List<Row> ids = template.query(String.format(MODELS_BY_FLOW_SQL, tablePrefix, flowId));
        for (Row row : ids) {
            models.add(this.findModel(row.getString("model_id")));
        }
        return models;
    }

    @Override
    public List<Flow> findDependentFlows(String projectVersionId) {
        List<Flow> flows = new ArrayList<Flow>();
        final String FLOWS_BY_PROJECT_SQL = "select distinct id from %1$s_flow where project_version_id =  '%2$s'";
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        List<Row> ids = template.query(String.format(FLOWS_BY_PROJECT_SQL, tablePrefix, projectVersionId));
        for (Row row : ids) {
            flows.add(this.findFlow(row.getString("id")));
        }
        return flows;
    }

    @Override
    public List<Flow> findAffectedFlowsByFlow(String flowId) {
        List<Flow> flows = new ArrayList<Flow>();

        final String AFFECTED_FLOWS_BY_FLOW_SQL = "select distinct flow_id from %1$s_flow_step fs inner join %1$s_component c on fs.component_id = c.id "
                + "inner join %1$s_component_setting cs on cs.component_id = c.id " + "where cs.name='flow.id' and cs.value = '%2$s'";
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        List<Row> ids = template.query(String.format(AFFECTED_FLOWS_BY_FLOW_SQL, tablePrefix, flowId));
        for (Row row : ids) {
            flows.add(this.findFlow(row.getString("flow_id")));
        }

        return flows;
    }

    @Override
    public List<Flow> findAffectedFlowsByResource(String resourceId) {
        List<Flow> flows = new ArrayList<Flow>();

        final String AFFECTED_FLOWS_BY_RESOURCE_SQL = "select distinct flow_id from %1$s_flow_step fs inner join %1$s_component c on fs.component_id = c.id "
                + "where c.resource_id = '%2$s'";
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        List<Row> ids = template.query(String.format(AFFECTED_FLOWS_BY_RESOURCE_SQL, tablePrefix, resourceId));
        for (Row row : ids) {
            flows.add(this.findFlow(row.getString("flow_id")));
        }

        return flows;
    }

    @Override
    public List<Flow> findAffectedFlowsByModel(String modelId) {
        List<Flow> flows = new ArrayList<Flow>();

        final String AFFECTED_FLOWS_BY_MODEL_SQL = "select distinct flow_id from %1$s_flow_step fs inner join %1$s_component c on fs.component_id = c.id "
                + "where c.input_model_id = '%2$s' or c.output_model_id = '%2$s'";
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        List<Row> ids = template.query(String.format(AFFECTED_FLOWS_BY_MODEL_SQL, tablePrefix, modelId));
        for (Row row : ids) {
            flows.add(this.findFlow(row.getString("flow_id")));
        }
        return flows;
    }

    @Override
    public void deleteReleasePackageProjectVersionsForReleasePackage(String releasePackageId) {
        final String DELETE_RELEASE_PACKAGE_VERSIONS_FOR_RELEASE_PACKAGE = "delete from %1$s_release_package_project_version " +
                "where release_package_id = '%2$s'";
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        template.update(String.format(DELETE_RELEASE_PACKAGE_VERSIONS_FOR_RELEASE_PACKAGE, tablePrefix, releasePackageId));                
    }
    
    
    @Override
    public void updateProjectVersionDependency(ProjectVersionDependency dependency, String newTargetProjectVersionId) {
        //TODO: audit events
        Map<String, String> oldToNewResourceIdMap = getOldToNewResourceIdMap(dependency, newTargetProjectVersionId);
        Map<String, String> oldToNewModelIdMap = getOldToNewModelIdMap(dependency, newTargetProjectVersionId);
        Map<String, String> oldToNewModelEntityIdMap = getOldToNewModelEntityIdMap(oldToNewModelIdMap);
        Map<String, String> oldToNewModelAttributeIdMap = getOldToNewModelAttributeIdMap(oldToNewModelEntityIdMap);

        ISqlTransaction transaction = databasePlatform.getSqlTemplate().startSqlTransaction();
        try {        
            updateProjectVersionWithNewDependencyGUIDs(oldToNewResourceIdMap, oldToNewModelIdMap,
                    oldToNewModelEntityIdMap, oldToNewModelAttributeIdMap, newTargetProjectVersionId,
                    transaction);
            transaction.commit();
        } catch (Exception e) {
            log.error(String.format("Error updating project version dependencies %s",e.getMessage()));
            transaction.rollback();
        }
    }
    
    private Map<String, String> getOldToNewResourceIdMap(ProjectVersionDependency dependency, String newTargetProjectVersionId) {
        Map<String, String> oldToNewResourceIdMap = new HashMap<String, String>();
        final String RESOURCES_USED_FROM_DEPENDENT_PROJECTS = 
                "select \n" + 
                "   distinct c.resource_id\n" + 
                "   ,nr.id\n" + 
                "from \n" + 
                "   %1$s_component c\n" + 
                "   inner join %1$s_project_version pv\n" + 
                "      on c.project_version_id = pv.id\n" + 
                "   left outer join %1$s_resource cr\n" + 
                "      on cr.id = c.resource_id\n" + 
                "      and cr.project_version_id = pv.id\n" + 
                "   inner join %1$s_project_version_dependency pvd\n" + 
                "      on pvd.project_version_id = pv.id\n" + 
                "   inner join %1$s_resource dr\n" + 
                "      on dr.project_version_id = pvd.target_project_version_id\n" + 
                "      and dr.id = c.resource_id\n" + 
                "   inner join %1$s_resource nr\n" + 
                "      on nr.project_version_id = '%2$s'" + 
                "      and dr.row_id = nr.row_id\n" + 
                "where\n" + 
                "   cr.id is null\n" + 
                "   and pv.id = '%3$s'" + 
                "union\n" + 
                "select\n" + 
                "   distinct cs.value\n" + 
                "   , nr.id\n" + 
                "from\n" + 
                "   %1$s_component_setting cs\n" + 
                "   inner join %1$s_component c\n" + 
                "      on cs.component_id = c.id\n" + 
                "   inner join %1$s_project_version pv\n" + 
                "      on c.project_version_id = pv.id\n" + 
                "   left outer join %1$s_resource r\n" + 
                "      on r.id = c.resource_id\n" + 
                "      and r.project_version_id = pv.id\n" + 
                "   inner join %1$s_project_version_dependency pvd\n" + 
                "      on pvd.project_version_id = pv.id\n" + 
                "   inner join %1$s_resource dr\n" + 
                "      on dr.project_version_id = pvd.target_project_version_id\n" + 
                "      and dr.id = cs.value\n" + 
                "   inner join %1$s_resource nr\n" + 
                "      on nr.project_version_id = '%2$s'" + 
                "      and dr.row_id = nr.row_id\n" + 
                "where\n" + 
                "   cs.name in ('target.resource','source.resource')\n" + 
                "   and r.id is null\n" + 
                "   and pv.id = '%3$s'";
//TODO: come up with every uuid and then check against metl_resource to  ensure that it is a resource or not       
        
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        List<Row> ids = template.query(String.format(RESOURCES_USED_FROM_DEPENDENT_PROJECTS, tablePrefix, newTargetProjectVersionId, dependency.getProjectVersionId()));
        for (Row row : ids) {
            oldToNewResourceIdMap.put(row.getString("resource_id"), row.getString("id"));
        }        
        return oldToNewResourceIdMap;
    }

    private Map<String, String> getOldToNewModelIdMap(ProjectVersionDependency dependency, String newTargetProjectVersionId) {
        Map<String, String> oldToNewModelIdMap = new HashMap<String, String>();
        final String MODELS_USED_FROM_DEPENDENT_PROJECTS =
                "select \n" + 
                "   distinct c.input_model_id\n" + 
                "   ,nm.id\n" + 
                "from \n" + 
                "   %1$s_component c\n" + 
                "   inner join %1$s_project_version pv\n" + 
                "      on c.project_version_id = pv.id\n" + 
                "   left outer join %1$s_model cm\n" + 
                "      on cm.id = c.input_model_id\n" + 
                "      and cm.project_version_id = pv.id\n" + 
                "   inner join %1$s_project_version_dependency pvd\n" + 
                "      on pvd.project_version_id = pv.id\n" + 
                "   inner join %1$s_model dm\n" + 
                "      on dm.project_version_id = pvd.target_project_version_id\n" + 
                "      and dm.id = c.input_model_id\n" + 
                "   inner join %1$s_model nm\n" + 
                "      on nm.project_version_id = '%2$s'" + 
                "      and dm.row_id = nm.row_id\n" + 
                "where\n" + 
                "   cm.id is null\n" + 
                "   and pv.id = '%3$s'" + 
                "union\n" + 
                "select \n" + 
                "   distinct c.output_model_id\n" + 
                "   ,nm.id\n" + 
                "from \n" + 
                "   %1$s_component c\n" + 
                "   inner join %1$s_project_version pv\n" + 
                "      on c.project_version_id = pv.id\n" + 
                "   left outer join %1$s_model cm\n" + 
                "      on cm.id = c.output_model_id\n" + 
                "      and cm.project_version_id = pv.id\n" + 
                "   inner join %1$s_project_version_dependency pvd\n" + 
                "      on pvd.project_version_id = pv.id\n" + 
                "   inner join %1$s_model dm\n" + 
                "      on dm.project_version_id = pvd.target_project_version_id\n" + 
                "      and dm.id = c.output_model_id\n" + 
                "   inner join %1$s_model nm\n" + 
                "      on nm.project_version_id = '%2$s'" + 
                "      and dm.row_id = nm.row_id\n" + 
                "where\n" + 
                "   cm.id is null\n" + 
                "   and pv.id = '%3$s' ";
        ISqlTemplate template = databasePlatform.getSqlTemplate();
        List<Row> ids = template.query(String.format(MODELS_USED_FROM_DEPENDENT_PROJECTS, tablePrefix, newTargetProjectVersionId, dependency.getProjectVersionId()));
        for (Row row : ids) {
            oldToNewModelIdMap.put(row.getString("input_model_id"), row.getString("id"));
        }        
        return oldToNewModelIdMap;
    }

    private Map<String, String> getOldToNewModelEntityIdMap(Map<String, String> oldToNewModelIdMap) {
        Map<String, String> oldToNewModelEntityIdMap = new HashMap<String, String>();
        final String MODEL_ENTITIES_USED_FROM_DEPENDENT_PROJECTS =
                "select \n" + 
                "   ome.id\n" + 
                "   ,nme.id\n" + 
                "from\n" + 
                "   %1$s_model_entity ome\n" + 
                "   left outer join %1$s_model_entity nme\n" + 
                "      on ome.name = nme.name\n" + 
                "where\n" + 
                "   ome.model_id = '%2$s'\n" + 
                "   and nme.model_id = '%3$s'";
        ISqlTemplate template = databasePlatform.getSqlTemplate();

        for (Map.Entry<String,String> entry : oldToNewModelIdMap.entrySet()) {
            List<Row> ids = template.query(String.format(MODEL_ENTITIES_USED_FROM_DEPENDENT_PROJECTS, tablePrefix, entry.getKey(),entry.getValue()));
            for (Row row : ids) {
                oldToNewModelEntityIdMap.put(row.getString("id"), row.getString("id_1"));
            }                    
        }
        return oldToNewModelEntityIdMap;
    }

    private Map<String, String> getOldToNewModelAttributeIdMap(Map<String, String> oldToNewModelEntityIdMap) {
        Map<String, String> oldToNewModelAttributeIdMap = new HashMap<String, String>();
        final String MODEL_ENTITIES_USED_FROM_DEPENDENT_PROJECTS =
                "select \n" + 
                "   oma.id\n" + 
                "   ,nma.id\n" + 
                "from\n" + 
                "   %1$s_model_attribute oma\n" + 
                "   left outer join %1$s_model_attribute nma\n" + 
                "      on oma.name = nma.name\n" + 
                "where\n" + 
                "   oma.model_id = '%2$s'\n" + 
                "   and nma.model_id = '%3$s'";
        ISqlTemplate template = databasePlatform.getSqlTemplate();

        for (Map.Entry<String,String> entry : oldToNewModelEntityIdMap.entrySet()) {
            List<Row> ids = template.query(String.format(MODEL_ENTITIES_USED_FROM_DEPENDENT_PROJECTS, tablePrefix, entry.getKey(),entry.getValue()));
            for (Row row : ids) {
                oldToNewModelAttributeIdMap.put(row.getString("id"), row.getString("id_1"));
            }                    
        }
        return oldToNewModelAttributeIdMap;
    }
    
    private void updateProjectVersionWithNewDependencyGUIDs(Map<String,String> oldToNewResourceIdMap, Map<String,String> oldToNewModelIdMap,
                Map<String,String> oldToNewModelEntityIdMap, Map<String,String> oldToNewModelAttributeIdMap,
                String targetProjectVersionId, ISqlTransaction transaction) {

        updateProjectVersionWithNewResources(oldToNewResourceIdMap, targetProjectVersionId, transaction);
        updateProjectVersionWithNewModels(oldToNewModelIdMap, oldToNewModelEntityIdMap,
                oldToNewModelAttributeIdMap, targetProjectVersionId, transaction);
    }
    
    private void updateProjectVersionWithNewResources(Map<String, String> oldToNewResourceIdMap, String targetProjectVersionId,
            ISqlTransaction transaction) {

        final String UPDATE_RESOURCE_IN_COMPONENT_OLD_TO_NEW = 
                "update %1$s_component set resource_id='%2$s' where resource_id='%3$s' and project_version_id='%4$s'";
        final String UPDATE_RESOURCE_IN_COMPONENT_SETTING_OLD_TO_NEW = 
                "update %1$s_component_setting as cs\n" + 
                "   set cs.value='%2$s'\n" + 
                "where \n" + 
                "   cs.value='%3$s'\n" + 
                "   and cs.name in ('source.resource','target.resource')\n" + 
                "   and cs.component_id in \n" + 
                "   (\n" + 
                "      select \n" + 
                "         c.id\n" + 
                "      from \n" + 
                "         %1$s_component c\n" + 
                "      where \n" + 
                "         cs.component_id = c.id         \n" + 
                "         and c.project_version_id='%4$s'\n" + 
                "   )\n";
//TODO same comment here as above - look for uuid and then compare against metl_resource              
        for (Map.Entry<String, String> entry : oldToNewResourceIdMap.entrySet()) {
            transaction.execute(String.format(UPDATE_RESOURCE_IN_COMPONENT_OLD_TO_NEW, tablePrefix, entry.getValue(), entry.getKey(), targetProjectVersionId));    
            transaction.execute(String.format(UPDATE_RESOURCE_IN_COMPONENT_SETTING_OLD_TO_NEW, tablePrefix, entry.getValue(), entry.getKey(), targetProjectVersionId));                
        }                
    }
    
    private void updateProjectVersionWithNewModels(Map<String, String> oldToNewModelIdMap, 
            Map<String, String> oldToNewModelEntityIdMap, Map<String, String> oldToNewModelAttributeIdMap,
            String targetProjectVersionId, ISqlTransaction transaction) {
        
        final String UPDATE_INPUT_MODEL_IN_COMPONENT_OLD_TO_NEW = 
                "update %1$s_component set input_model_id='%2$s' where input_model_id='%3$s' and project_version_id='%4$s'";
        final String UPDATE_OUTPUT_MODEL_IN_COMPONENT_OLD_TO_NEW = 
                "update %1$s_component set output_model_id='%2$s' where output_model_id='%3$s' and project_version_id='%4$s'";
              
        for (Map.Entry<String, String> entry : oldToNewModelIdMap.entrySet()) {        
            transaction.execute(String.format(UPDATE_INPUT_MODEL_IN_COMPONENT_OLD_TO_NEW, tablePrefix, entry.getValue(), entry.getKey(), targetProjectVersionId));
            transaction.execute(String.format(UPDATE_OUTPUT_MODEL_IN_COMPONENT_OLD_TO_NEW, tablePrefix, entry.getValue(), entry.getKey(), targetProjectVersionId));            
        }        
        updateProjectVersionWithNewModelEntityIds(oldToNewModelEntityIdMap, targetProjectVersionId, transaction);
        updateProjectVersionWithNewModelAttributeIds(oldToNewModelAttributeIdMap, targetProjectVersionId, transaction);        
    }

    private void updateProjectVersionWithNewModelEntityIds(
            Map<String, String> oldToNewModelEntityIdMap, String targetProjectVersionId, ISqlTransaction transaction) {

        final String UPDATE_COMPONENT_ENTITY_SETTING_OLD_TO_NEW = 
                "update %1$s_component_entity_setting as ces\n" + 
                "   set ces.entity_id='%2$s'\n" + 
                "where \n" + 
                "   ces.entity_id='%3$s'\n" + 
                "   and ces.component_id in \n" + 
                "   (\n" + 
                "      select \n" + 
                "         c.id\n" + 
                "      from \n" + 
                "         %1$s_component c\n" + 
                "      where \n" + 
                "         ces.component_id = c.id         \n" + 
                "         and c.project_version_id='%4$s'\n" + 
                "   )\n";
     
        for (Map.Entry<String, String> entry : oldToNewModelEntityIdMap.entrySet()) {        
            transaction.execute(String.format(UPDATE_COMPONENT_ENTITY_SETTING_OLD_TO_NEW, tablePrefix, 
                    entry.getValue(), entry.getKey(), targetProjectVersionId));                    
        }                
    }   
        
    private void updateProjectVersionWithNewModelAttributeIds(
            Map<String, String> oldToNewModelAttributeIdMap, String targetProjectVersionId, ISqlTransaction transaction) {

        final String UPDATE_COMPONENT_ATTRIBUTE_SETTING_OLD_TO_NEW = 
                "update %1$s_component_attribute_setting as ces\n" + 
                "   set ces.entity_id='%2$s'\n" + 
                "where \n" + 
                "   ces.entity_id='%3$s'\n" + 
                "   and ces.component_id in \n" + 
                "   (\n" + 
                "      select \n" + 
                "         c.id\n" + 
                "      from \n" + 
                "         %1$s_component c\n" + 
                "      where \n" + 
                "         ces.component_id = c.id         \n" + 
                "         and c.project_version_id='%4$s'\n" + 
                "   )\n";

        final String UPDATE_MODEL_ATTRIBUTE_IN_COMPONENT_SETTING_OLD_TO_NEW = 
                "update %1$s_component_setting as cs\n" + 
                "   set cs.value='%2$s'\n" + 
                "where \n" + 
                "   cs.value='%3$s'\n" + 
                "   and cs.name in ('lookup.key.attribute','lookup.value.attribute','replacement.key.attribute','replacement.value.attribute','sequence.attribute')\n" + 
                "   and cs.component_id in \n" + 
                "   (\n" + 
                "      select \n" + 
                "         c.id\n" + 
                "      from \n" + 
                "         %1$s_component c\n" + 
                "      where \n" + 
                "         cs.component_id = c.id         \n" + 
                "         and c.project_version_id='%4$s'\n" + 
                "   )\n";
              
        for (Map.Entry<String, String> entry : oldToNewModelAttributeIdMap.entrySet()) {        
            transaction.execute(String.format(UPDATE_COMPONENT_ATTRIBUTE_SETTING_OLD_TO_NEW, tablePrefix, 
                    entry.getValue(), entry.getKey(), targetProjectVersionId));
            transaction.execute(String.format(UPDATE_MODEL_ATTRIBUTE_IN_COMPONENT_SETTING_OLD_TO_NEW, tablePrefix, 
                    entry.getValue(), entry.getKey(), targetProjectVersionId));            
        }                
    }
}
