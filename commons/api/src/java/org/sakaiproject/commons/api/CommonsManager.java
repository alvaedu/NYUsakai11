package org.sakaiproject.commons.api;

import java.util.*;

import org.sakaiproject.commons.api.datamodel.Comment;
import org.sakaiproject.commons.api.datamodel.Post;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityProducer;

/**
 * @author Adrian Fish (adrian.r.fish@gmail.com)
 */
public interface CommonsManager extends EntityProducer {

    public static final String ENTITY_PREFIX = "commons";
    public static final String REFERENCE_ROOT = Entity.SEPARATOR + ENTITY_PREFIX;

    public static final String POST_CACHE = "org.sakaiproject.commons.sortedPostCache";

    public Post getPost(String postId, boolean loadComments);

    public List<Post> getPosts(QueryBean query) throws Exception;

    public Post savePost(Post post);

    public boolean deletePost(String postId);

    public Comment saveComment(String commonsId, Comment comment);

    public boolean deleteComment(String siteId, String commonsId, String embedder, String commentId, String commentCreatorId, String postCreatorId);
}
