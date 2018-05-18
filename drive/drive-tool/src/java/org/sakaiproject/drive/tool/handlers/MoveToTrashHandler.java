/**********************************************************************************
 *
 * Copyright (c) 2015 The Sakai Foundation
 *
 * Original developers:
 *
 *   New York University
 *   Payten Giles
 *   Mark Triggs
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.drive.tool.handlers;

import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.tool.cover.ToolManager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoveToTrashHandler implements Handler {

    private boolean inlineTargetListing = false;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) {
        try {
            String[] resources = request.getParameterValues("resource[]");

            ContentHostingService chs = (ContentHostingService) ComponentManager.get("org.sakaiproject.content.api.ContentHostingService");

            for (String resource : resources) {
                if (chs.isCollection(resource)) {
                    chs.removeCollection(resource);
                } else {
                    chs.removeResource(resource);
                }
            }

            if ("true".equals(request.getParameter("inline_target_listing"))) {
                // If the caller asks, we can send back the listing for the
                // folder containing the moved file(s).  Saves a second request
                // to fetch it.
                String siteId = ToolManager.getCurrentPlacement().getContext();
                String rootCollectionId = chs.getSiteCollection(siteId);
                inlineTargetListing = true;
                new ViewTrashHandler().prepareListing(context, rootCollectionId, true);
            } else {
                context.put("layout", "false");
                context.put("subpage", "plaintext");
                context.put("plaintext_content", "OK");
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getContentType() {
        if (inlineTargetListing) {
            // We'll return the target folder's list view
            return "text/html";
        } else {
            return "text/plain";
        }
    }

    public boolean hasRedirect() {
        return false;
    }

    public String getRedirect() {
        return null;
    }

    public Errors getErrors() {
        return null;
    }

    public Map<String, List<String>> getFlashMessages() {
        return new HashMap<String, List<String>>();
    }

    public boolean hasTemplate() {
        return true;
    }

}
