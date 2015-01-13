/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.chemistry.opencmis.mongodb;

import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.Properties;
import org.apache.chemistry.opencmis.commons.data.PropertyData;
import org.apache.chemistry.opencmis.commons.data.PropertyDateTime;
import org.apache.chemistry.opencmis.commons.data.PropertyId;
import org.apache.chemistry.opencmis.commons.data.PropertyString;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.bson.BSON;
import org.bson.BasicBSONObject;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.WriteResult;

public final class MongodbUtils {

	private static final String PATH_SEPARATOR = "/";
	DB db;
	private static String COLLECTION_CONTENT = "content";
	private static String COLLECTION_TEMPLATES = "templates";
	
    private MongodbUtils(DB db) {
    	this.db = db;
    }
   

    /**
     * Returns the boolean value of the given value or the default value if the
     * given value is <code>null</code>.
     */
    public static boolean getBooleanParameter(Boolean value, boolean def) {
        if (value == null) {
            return def;
        }

        return value.booleanValue();
    }
    
    
    public BasicDBObject addNode(BasicDBObject node, BasicDBObject parent) {
    	// Update all affected right and left values which are greater or equal
    	// to the parents right value - we are incrementing to 'make room' for the 
    	// new node
    	db.getCollection(COLLECTION_CONTENT).update(
    			new BasicDBObject()
    				.append("right", new BasicDBObject().append("$gte", parent.getLong("right"))),
    			new BasicDBObject()
    				.append("$inc", new BasicDBObject().append("right", 2)), false, true);
    	
    	db.getCollection(COLLECTION_CONTENT).update(
    			new BasicDBObject()
    				.append("left", new BasicDBObject().append("$gte", parent.getLong("right"))),
    			new BasicDBObject()
    				.append("$inc", new BasicDBObject().append("left", 2)), false, true);
  
    	// Finally insert the node into the created space in the tree, under the parent
    	node.append("left", parent.getLong("right"))
			.append("right",  parent.getLong("right") + 1)
			.append("level",  parent.getLong("level") + 1);
    	
    	WriteResult result = db.getCollection(COLLECTION_CONTENT).insert(node);
    	if (result.getN() != 1) {
    		throw new MongoException("Error while inserting the node into the database.");
    	} else {
    		return node.append("_id", result.getUpsertedId());
    	}
    }
    
    public String getPathToNode(BasicDBObject node, DBCollection collection) {
    	StringBuilder path = new StringBuilder();
    	BasicDBList ancestorsList = this.getNodeAncestors(node, collection);
    	for (int i = 0; i < ancestorsList.size(); i++) {
    		
    	}
    	int i = 0;
    	while(ancestorsList.get(i) != null) {
    		DBObject ancestor = (DBObject) ancestorsList.get(i);
    		path.append(PATH_SEPARATOR).append(ancestor.get("title").toString());
    	}
    	DBCursor ancestors = collection.find(new BasicDBObject()
    		.append("left", new BasicDBObject().append("$lt", node.getLong("left"))))
    		.sort(new BasicDBObject().append("left", 1));
    	while(ancestors.hasNext()) {
    		DBObject ancestor = ancestors.next();
    		path.append(PATH_SEPARATOR).append(ancestor.get("name").toString());
    	}
    	return path.toString();
    }
    
    public void removeNode(BasicDBObject node) {
    	// Update all affected right and left values which are greater or equal
    	// to the parents right value - we are incrementing to compress the void 
    	// left by the removal of the node
    	db.getCollection(COLLECTION_CONTENT).update(
    			new BasicDBObject()
    				.append("right", new BasicDBObject().append("$gt", node.getLong("right"))),
    			new BasicDBObject()
    				.append("$inc", new BasicDBObject().append("right", -2)), false, true);
    	
    	db.getCollection(COLLECTION_CONTENT).update(
    			new BasicDBObject()
    				.append("left", new BasicDBObject().append("$gt", node.getLong("right"))),
    			new BasicDBObject()
    				.append("$inc", new BasicDBObject().append("left", -2)), false, true);
  
    	// Finally remove the node 
    	
    	WriteResult result = db.getCollection(COLLECTION_CONTENT).remove(node);
    	if (result.getN() != 1) {
    		throw new MongoException("Error while removing the node from the database.");
    	} 
    }
    
    public void moveNode(BasicDBObject node, BasicDBObject newParent, DBCollection collection) {

    	// Get the left and right values
    	Long originalLeft = node.getLong("left");
    	Long originalRight = node.getLong("right");
    	Long subtreeWidth = originalRight - originalLeft;
    	
    	// Compute the new left and right values for the nodeToMove
    	Long newLeft = newParent.getLong("right");
    	Long newRight = newParent.getLong("right") + subtreeWidth;
    	
    	// Make space for the new subtree under the new parent
    	collection.update(
    		new BasicDBObject()
    			.append("right", new BasicDBObject().append("$gte", newParent.get("right"))),
    		new BasicDBObject()
    			.append("$inc", new BasicDBObject().append("right", subtreeWidth + 1)), false, true);

    	collection.update(
    		new BasicDBObject()
    			.append("left", new BasicDBObject().append("$gte", newParent.get("right"))),
    		new BasicDBObject()
    			.append("$inc", new BasicDBObject().append("left", subtreeWidth + 1)), false, true);
    	
    	// Re-fetch the node to move, since the left and right values may have changed
    	node = (BasicDBObject) collection.findOne(
    		new BasicDBObject().append("_id", node.get("_id")));
    	
    	Long difference = node.getLong("left") - newLeft;
    	// Move the old subtree into a new location
    	collection.update(
    			new BasicDBObject()
    				.append("left", new BasicDBObject().append("$gte", node.getLong("left")))
    				.append("right", new BasicDBObject().append("$lte", node.getLong("right"))),
    			new BasicDBObject()
    				.append("$inc", 
    					new BasicDBObject()
    						.append("left", 0-difference)
    						.append("right", 0-difference)), false, true);

    	// Remove empty space from the parent
    	//db.test.update({left:nodeToMove.left-1, right:nodeToMove.right+1}, {right:nodeToMove.left});
    	collection.update(
        		new BasicDBObject()
        			.append("right", new BasicDBObject().append("$gte", node.get("left"))),
        		new BasicDBObject()
        			.append("$inc", new BasicDBObject().append("right", 0-subtreeWidth-1)), false, true);
    	collection.update(
        		new BasicDBObject()
        			.append("left", new BasicDBObject().append("$gte", node.get("left"))),
        		new BasicDBObject()
        			.append("$inc", new BasicDBObject().append("left", 0-subtreeWidth-1)), false, true);
    }
    
    public BasicDBObject getNodeByPath(String path, DBCollection collection) {

    	String[] pathSplit = path.split(PATH_SEPARATOR);
    	int pathDepth = pathSplit.length - 1;

    	DBCursor candidates = collection.find(
    		new BasicDBObject()
    			.append("title", pathSplit[pathDepth])
    			.append("level", "pathDepth"));

    	// if number of candidates is one, then we have found our content item - return it
    	if (candidates.size() == 1) {
    		return (BasicDBObject) candidates.next();
    	}
    	// if number of candidates is greater than the path depth, then just follow the depth instead

    	if (candidates.size() >= pathSplit.length) {
    		int currentLevel = 1;
    		DBObject currentNode = collection.findOne(
    			new BasicDBObject().append("title", "root"));
    		
    		while (currentLevel <= pathDepth && currentNode != null) {
    			currentNode = collection.findOne(
    					new BasicDBObject()
    						.append("left", new BasicDBObject("$gt", currentNode.get("left")))
    						.append("right", new BasicDBObject("$gt", currentNode.get("right")))
    						.append("title", pathSplit[currentLevel])
    						.append("level", currentLevel));
    			currentLevel++;
    		}
    		return (BasicDBObject) currentNode;
    	} else {
    		int pathLevel = pathDepth - 1;
    		DBObject candidate = null;
    		
    		while (candidates.hasNext() && pathLevel > 0) {
    			candidate = candidates.next();
    			DBCursor ancestors = collection.find(
    				new BasicDBObject("left", new BasicDBObject().append("$lt", candidate.get("left")))
    					.append("right", new BasicDBObject().append("$gt", candidate.get("right"))))
    					.sort(new BasicDBObject("left", 1));
    			
    			DBObject ancestor = ancestors.next();
    			while(ancestor != null && ancestor.get("title").equals(pathSplit[pathLevel]) && pathLevel > -1) {
    				ancestor = ancestors.next();
    				pathLevel--;
    			}
    		}
    		return (BasicDBObject) candidate;
    	}
    }
    
    public BasicDBList getNodeAncestors(BasicDBObject node, DBCollection collection) {
    	DBCursor ancestors = collection.find(new BasicDBObject()
			.append("left", new BasicDBObject().append("$lt", node.getLong("left"))))
			.sort(new BasicDBObject().append("left", 1));
    	BasicDBList ancestorsList = new BasicDBList();
    	while (ancestors.hasNext()) {
    		ancestorsList.add(ancestors.next());
    	}
		return ancestorsList;
    }
    
    public BasicDBList getNodeDescendents(BasicDBObject node, DBCollection collection) {
    	BasicDBList descendantsArray = new BasicDBList();
    	DBCursor descendants = collection.find(
    		new BasicDBObject()
    			.append("left", new BasicDBObject().append("$gt", node.getLong("left")))
    			.append("right", new BasicDBObject().append("$lt", node.getLong("right"))))
    			.sort(new BasicDBObject().append("left", 1));

    	while(descendants.hasNext()) {
    		descendantsArray.add(descendants.next());

    	}
    	return descendantsArray;
    }
    
    private BasicDBList getNodeChildren(BasicDBObject node, DBCollection collection) {
    	BasicDBList childrenArray = new BasicDBList();
    	DBCursor children = collection.find(
    		new BasicDBObject()
    			.append("left", new BasicDBObject().append("$gt", node.getLong("left")))
    			.append("right", new BasicDBObject().append("$lt", node.getLong("right")))
    			.append("level", node.getLong("level") + 1))
    			.sort(new BasicDBObject().append("left", 1));

    	while(children.hasNext()) {
    		childrenArray.add(children.next());

    	}
    	return childrenArray;
    }

    /**
     * Converts milliseconds into a {@link GregorianCalendar} object, setting
     * the timezone to GMT and cutting milliseconds off.
     */
    public static GregorianCalendar millisToCalendar(long millis) {
        GregorianCalendar result = new GregorianCalendar();
        result.setTimeZone(TimeZone.getTimeZone("GMT"));
        result.setTimeInMillis((long) (Math.ceil((double) millis / 1000) * 1000));

        return result;
    }

    /**
     * Splits a filter statement into a collection of properties. If
     * <code>filter</code> is <code>null</code>, empty or one of the properties
     * is '*' , an empty collection will be returned.
     */
    public static Set<String> splitFilter(String filter) {
        if (filter == null) {
            return null;
        }

        if (filter.trim().length() == 0) {
            return null;
        }

        Set<String> result = new HashSet<String>();
        for (String s : filter.split(",")) {
            s = s.trim();
            if (s.equals("*")) {
                return null;
            } else if (s.length() > 0) {
                result.add(s);
            }
        }

        // set a few base properties
        // query name == id (for base type properties)
        result.add(PropertyIds.OBJECT_ID);
        result.add(PropertyIds.OBJECT_TYPE_ID);
        result.add(PropertyIds.BASE_TYPE_ID);

        return result;
    }

    /**
     * Gets the type id from a set of properties.
     */
    public static String getObjectTypeId(Properties properties) {
        PropertyData<?> typeProperty = properties.getProperties().get(PropertyIds.OBJECT_TYPE_ID);
        if (!(typeProperty instanceof PropertyId)) {
            throw new CmisInvalidArgumentException("Type Id must be set!");
        }

        String typeId = ((PropertyId) typeProperty).getFirstValue();
        if (typeId == null) {
            throw new CmisInvalidArgumentException("Type Id must be set!");
        }

        return typeId;
    }

    /**
     * Returns the first value of an id property.
     */
    public static String getIdProperty(Properties properties, String name) {
        PropertyData<?> property = properties.getProperties().get(name);
        if (!(property instanceof PropertyId)) {
            return null;
        }

        return ((PropertyId) property).getFirstValue();
    }

    /**
     * Returns the first value of a string property.
     */
    public static String getStringProperty(Properties properties, String name) {
        PropertyData<?> property = properties.getProperties().get(name);
        if (!(property instanceof PropertyString)) {
            return null;
        }

        return ((PropertyString) property).getFirstValue();
    }

    /**
     * Returns the first value of a datetime property.
     */
    public static GregorianCalendar getDateTimeProperty(Properties properties, String name) {
        PropertyData<?> property = properties.getProperties().get(name);
        if (!(property instanceof PropertyDateTime)) {
            return null;
        }

        return ((PropertyDateTime) property).getFirstValue();
    }
}
