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
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.TypeException;
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
            context.put("trash", true);

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

    private class Trash extends ResourceTree {

        public Trash(ContentCollection root, ContentHostingService contentHostingService) {
            super(root, contentHostingService);
        }

        @Override
        public Collection<Resource> getChildren() {
            Collection<Resource> result = new ArrayList<>();

            List<ContentEntity> children = contentHostingService.getAllDeletedResources(root.getId());
            Collections.sort(children, contentHostingService.newContentHostingComparator(ResourceProperties.PROP_DISPLAY_NAME, true));

            for (ContentEntity entity : children) {
                if (entity.isCollection()) {
                    throw new RuntimeException("No collections please");
                } else {
                    result.add(new TrashItem((ContentResource) entity));
                }
            }

            return result;
        }

        @Override
        public List<Breadcrumb> getBreadcrumbs() {
            List<Breadcrumb> result = new ArrayList<>();

            ContentCollection current = root;

            result.add(new Breadcrumb(root.getId(), getLabel(root)));
            result.add(new Breadcrumb("trash", "Trash"));

            return result;
        }
    }

    private class TrashItem extends ResourceItem {

        public TrashItem(ContentResource resource) {
            super(resource);
        }

        public List<Breadcrumb> getBreadcrumbs() {
            List<Breadcrumb> crumbs = new ArrayList<>();

            String[] parts = resource.getId().split("/");
            String path = "";
            for (String part : parts) {
                if (part == parts[parts.length - 1]) {
                    continue;
                }

                path += part + "/";
                if (path.equals("/") || path.equals("/group/") || path.equals("/user/")) {
                    continue;
                }


                try {
                    ContentCollection collection = contentHostingService.getCollection(path);
                    crumbs.add(new Breadcrumb(collection.getId(), getLabel(collection)));
                } catch (IdUnusedException e) {
                    crumbs.add(new Breadcrumb(path, part));
                } catch (TypeException e) {
                    continue;
                } catch (PermissionException e) {
                    continue;
                }
            }

            crumbs.add(new Breadcrumb(resource.getId(), getLabel()));

            return crumbs;
        }

        private String getLabel(ContentCollection collection) {
            String label = (String)collection.getProperties().get(ResourceProperties.PROP_DISPLAY_NAME);
            // FIXME label shouldn't be null, but sometimes it is??
            if (label == null) {
                return "Error: no label";
            } else {
                return label;
            }
        }

    }

}



