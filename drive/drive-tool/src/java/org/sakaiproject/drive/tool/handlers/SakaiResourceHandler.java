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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Comparator;
import java.text.DateFormat;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.util.ResourceLoader;
import org.sakaiproject.time.cover.TimeService;
import org.sakaiproject.time.api.Time;

import java.util.stream.Collectors;
import java.util.Collections;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import java.net.URL;

import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentEntity;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentCollection;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.content.api.ContentTypeImageService;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.EntityPropertyNotDefinedException;
import org.sakaiproject.entity.api.EntityPropertyTypeException;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.content.api.GroupAwareEntity.AccessMode;

public class SakaiResourceHandler implements Handler {

    ContentHostingService contentHostingService = (ContentHostingService) ComponentManager.get("org.sakaiproject.content.api.ContentHostingService");

    private String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) {
        String siteId = (String) context.get("siteId");
        String requestedPath = "/group/" + siteId + "/";

        if (request.getPathInfo() != null) {
            String[] bits = request.getPathInfo().split("/", 3);

            // path info starts with /sakai-drive/
            if (bits.length == 3) {
                requestedPath = "/" + bits[2];
            }
        }

        if (!requestedPath.endsWith("/")) {
            requestedPath += "/";
        }

        try {
            // THINKME: Maybe context should be smrter!
            ContentCollection siteResources = contentHostingService.getCollection(requestedPath);

            context.put("resource", new ResourceTree(siteResources, contentHostingService));
            context.put("collectionId", siteResources.getId());

            if ("true".equals(request.getParameter("inline"))) {
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

    private interface Resource {
        public String getLabel();
        public boolean isFolder();
        public Collection<Resource> getChildren();
        public String getTypeClass();
        public String getPath();
        public List<Breadcrumb> getBreadcrumbs();
        public String getSize();
        public String getLastModified();
        public String getOwner();
        public String getAccessSummary();
    }

    private class ResourceTree implements Resource {
        private ContentCollection root;
        private ContentHostingService contentHostingService;
        private ResourceProperties properties;

        public ResourceTree(ContentCollection root, ContentHostingService contentHostingService) {
            this.root = root;
            this.properties = root.getProperties();
            this.contentHostingService = contentHostingService;
        }

        public boolean isFolder() {
            return true;
        }

        public String getTypeClass() {
            return "drive-folder";
        }

        public String getPath() {
            return root.getId();
        }

        public Collection<Resource> getChildren() {
            Collection<Resource> result = new ArrayList<>();

            List<ContentEntity> children = root.getMemberResources();
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

            while (current != null && !"/group/".equals(current.getId())) {
                result.add(0, new Breadcrumb(current.getId(), getLabel(current)));
                current = current.getContainingCollection();
            }
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

    private static class Breadcrumb {
        private String id;
        private String label;

        public Breadcrumb(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String getId() { return id; }
        public String getLabel() { return label; }
    }

    private class ResourceItem implements Resource {
        private ContentResource resource;
        private ContentTypeImageService contentTypeImageService;
        private ResourceProperties properties;
        
        public ResourceItem(ContentResource resource) {
            this.resource = resource;
            this.properties = resource.getProperties();
            this.contentTypeImageService = (ContentTypeImageService) ComponentManager.get("org.sakaiproject.content.api.ContentTypeImageService");
            
        }

        public boolean isFolder() {
            return false;
        }

        public String getLabel() {
            String label = (String)resource.getProperties().get(ResourceProperties.PROP_DISPLAY_NAME);
            // FIXME label shouldn't be null, but sometimes it is??
            if (label == null) {
                return "Error: no label";
            } else {
                return label;
            }
        }

        public String getPath() {
            return resource.getId();
        }

        public Collection<Resource> getChildren() {
            return Collections.EMPTY_LIST;
        }

        public List<Breadcrumb> getBreadcrumbs() {
            return Collections.EMPTY_LIST;
        }

        public String getSize() {
            long bytes = resource.getContentLength();
            if (bytes < 1024) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1024));
            String pre = ("KMGTPE").charAt(exp-1) + "";
            return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
        }

        public String getTypeClass() {
            String icon = contentTypeImageService.getContentTypeImage(((String) resource.getProperties().get(ResourceProperties.PROP_CONTENT_TYPE)));
            return icon.replaceAll("/?sakai/(.*)\\..*", "$1");
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
            if (AccessMode.GROUPED.equals(resource.getAccess())) {
                return "Select group(s)";
            } else if (resource.getRoleAccessIds().size() > 0) {
                if (resource.getRoleAccessIds().contains(".anon")) {
                    return "Public";
                } else {
                    //return resource.getRoleAccessIds().stream().collect(Collectors.joining(", "));
                    return "Select role(s)";
                }
            } else {
                return "Entire site";
            }
        }
    }


}



