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
import org.sakaiproject.content.api.*;
import org.sakaiproject.content.api.GroupAwareEntity.AccessMode;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.util.ResourceLoader;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.DateFormat;
import java.util.*;

public class ViewTrashHandler extends SakaiResourceHandler {

    public void prepareListing(Map<String, Object> context, String requestedPath, boolean inline) {
        try {
            // THINKME: Maybe context should be smrter!
            ContentCollection siteResources = contentHostingService.getCollection(requestedPath);

            context.put("resource", new Trash(siteResources, contentHostingService));
            context.put("collectionId", siteResources.getId());

            if (inline) {
                context.put("layout", "false");
                context.put("subpage", "sakai_resources");
            } else {
                context.put("subpage", "resources");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, List<String>> getFlashMessages() {
        return new HashMap<String, List<String>>();
    }

    public Errors getErrors() {
        return null;
    }

    public boolean hasRedirect() {
        return false;
    }

    public String getRedirect() {
        return "";
    }

    private class Trash implements Resource {
        private ContentCollection root;
        private ContentHostingService contentHostingService;
        private ResourceProperties properties;

        public Trash(ContentCollection root, ContentHostingService contentHostingService) {
            this.root = root;
            this.properties = root.getProperties();
            this.contentHostingService = contentHostingService;
        }

        public boolean isFolder() {
            return true;
        }

        public String getTypeClass() {
            return "trash";
        }

        public String getPath() {
            return "trash";
        }

        public Collection<Resource> getChildren() {
            Collection<Resource> result = new ArrayList<>();

            List<ContentEntity> children = contentHostingService.getAllDeletedResources(root.getId());
            Collections.sort(children, contentHostingService.newContentHostingComparator(ResourceProperties.PROP_DISPLAY_NAME, true));

            for (ContentEntity entity : children) {
                if (entity.isCollection()) {
                    result.add(new ResourceTree((ContentCollection) entity, contentHostingService));
                } else {
                    result.add(new ResourceItem((ContentResource) entity));
                }
            }

            return result;
        }

        public List<Breadcrumb> getBreadcrumbs() {
            List<Breadcrumb> result = new ArrayList<>();

            ContentCollection current = root;

            result.add(new Breadcrumb(root.getId(), getLabel(root)));
            result.add(new Breadcrumb("trash", "Trash"));

            return result;
        }

        public String getLabel() {
            return getLabel(root);
        }

        private String getLabel(ContentCollection other) {
            String label = (String)other.getProperties().get(ResourceProperties.PROP_DISPLAY_NAME);
            // FIXME label shouldn't be null, but sometimes it is??
            if (label == null) {
                return "Error: no label";
            } else {
                return label;
            }
        }

        public String getSize() {
            return String.format("%s items", root.getMemberCount());
        }

        public String getLastModified() {
            try {
                long modifiedTime = properties.getTimeProperty(ResourceProperties.PROP_MODIFIED_DATE).getTime();
                final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, new ResourceLoader().getLocale());
                final TimeZone tz = TimeService.getLocalTimeZone();
                df.setTimeZone(tz);
                return df.format(modifiedTime);
            } catch (EntityPropertyNotDefinedException e) {
                return "";
            } catch (EntityPropertyTypeException e) {
                return properties.getProperty(ResourceProperties.PROP_MODIFIED_DATE);
            }
        }

        public String getOwner() {
            String userId = properties.getProperty(ResourceProperties.PROP_CREATOR);

            if (userId != null) {
                try {
                    return UserDirectoryService.getUser(userId).getDisplayName();
                } catch (UserNotDefinedException e) {
                }
            }

            return "";
        }

        public String getAccessSummary() {
            if (AccessMode.GROUPED.equals(root.getAccess())) {
                return "Select group(s)";
            } else if (root.getRoleAccessIds().size() > 0) {
                if (root.getRoleAccessIds().contains(".anon")) {
                    return "Public";
                } else {
                    //return root.getRoleAccessIds().stream().collect(Collectors.joining(", "));
                    return "Select role(s)";
                }
            } else {
                return "Entire site";
            }
        }
    }

}



