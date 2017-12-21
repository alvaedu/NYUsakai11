/**********************************************************************************
 *
 * Original developers:
 *
 *   New York University
 *   Bhavesh Vasandani
 *   Eric Lin
 *   Steven Adam
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       https://opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/
package org.sakaiproject.stealth.api;

public class StealthTool implements Comparable<StealthTool> {
    private final String toolId;
    private final String toolName;

    public StealthTool(String toolId, String toolName) {
        this.toolId  = toolId;
        this.toolName = toolName;
    }

    public String getToolId() {
        return this.toolId;
    }

    public String getToolName() {
        return this.toolName;
    }

    @Override
    public String toString() {
        return getToolId() + " " + getToolName();
    }

    @Override
    public int compareTo(StealthTool other) {
        return this.toolId.compareTo(other.toolId);
    }
}