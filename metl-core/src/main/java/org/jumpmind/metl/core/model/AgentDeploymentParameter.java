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
package org.jumpmind.metl.core.model;

public class AgentDeploymentParameter extends AbstractNamedObject {

    private static final long serialVersionUID = 1L;

    String name;
    String value;
    String flowParameterId;
    String agentDeploymentId;
    
    public AgentDeploymentParameter() {
    }
    
    public AgentDeploymentParameter(String name, String value, 
            String agentDeploymentId, String flowParameterId) {
        this.name = name;
        this.value = value;
        this.agentDeploymentId = agentDeploymentId;
        this.flowParameterId = flowParameterId;
    }
    
    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setAgentDeploymentId(String agentDeploymentId) {
        this.agentDeploymentId = agentDeploymentId;
    }
    
    public String getAgentDeploymentId() {
        return agentDeploymentId;
    }
    
    public void setFlowParameterId(String flowParameterId) {
        this.flowParameterId = flowParameterId;
    }
    
    public String getFlowParameterId() {
        return flowParameterId;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }

}
