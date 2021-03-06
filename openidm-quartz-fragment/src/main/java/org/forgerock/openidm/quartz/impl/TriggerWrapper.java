/**
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
*
* Copyright (c) 2012 ForgeRock AS. All Rights Reserved
*
* The contents of this file are subject to the terms
* of the Common Development and Distribution License
* (the License). You may not use this file except in
* compliance with the License.
*
* You can obtain a copy of the License at
* http://forgerock.org/license/CDDLv1.0.html
* See the License for the specific language governing
* permission and limitations under the License.
*
* When distributing Covered Code, include this CDDL
* Header Notice in each file and include the License file
* at http://forgerock.org/license/CDDLv1.0.html
* If applicable, add the following below the CDDL Header,
* with the fields enclosed by brackets [] replaced by
* your own identifying information:
* "Portions Copyrighted [year] [name of copyright owner]"
*
*/

package org.forgerock.openidm.quartz.impl;

import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import org.forgerock.json.JsonValue;
import org.quartz.JobPersistenceException;
import org.quartz.Trigger;

/**
 * A wrapper that contains all necessary information for a Trigger.
 */
public class TriggerWrapper {
    
    private String serialized;
    private String name;
    private String group;
    private String revision;
    private boolean acquired;
    private int state;
    private int previous_state = Trigger.STATE_NONE;
    private String nodeId;

    /**
     * Create a new TriggerWrapper from the specified trigger and specifying the trigger pause state.
     * 
     * @param trigger the {@link Trigger} object
     * @param paused if the trigger is paused
     * @throws JobPersistenceException if unable to serialize trigger
     */
    public TriggerWrapper(final Trigger trigger, final boolean paused) throws JobPersistenceException {
        this(trigger.getName(), trigger.getGroup(), RepoJobStoreUtils.serialize(trigger), paused);
    }

    /**
     * Create a new TriggerWrapper from the specified trigger and specifying the pause and acquired state.
     *
     * @param trigger the {@link Trigger} object
     * @param paused if the trigger is paused
     * @param acquired if the trigger is acquired
     * @throws JobPersistenceException if unable to serialize trigger
     */
    public TriggerWrapper(final Trigger trigger, final boolean paused, final boolean acquired)
            throws JobPersistenceException {
        this(trigger.getName(), trigger.getGroup(), RepoJobStoreUtils.serialize(trigger), paused, acquired, null);
    }

    /**
     * Create a new TriggerWrapper from the specified trigger and specifying the pause state, aquired state, and the
     * nodeId.
     *
     * @param trigger the {@link Trigger} object
     * @param paused if the trigger is paused
     * @param acquired if the trigger is paused
     * @param nodeId the nodeId that acquired the trigger
     * @throws JobPersistenceException if unable to serialize trigger
     */
    public TriggerWrapper(final Trigger trigger, final boolean paused, final boolean acquired, final String nodeId)
            throws JobPersistenceException {
        this(trigger.getName(), trigger.getGroup(), RepoJobStoreUtils.serialize(trigger), paused, acquired, nodeId);
    }
    
    /**
     * Create a new TriggerWrapper from parameters.
     * 
     * @param name the name of the trigger
     * @param group the trigger group
     * @param serializedValue a string representing the serialized trigger
     * @param paused if the trigger is paused
     */
    public TriggerWrapper(final String name, final String group, final String serializedValue, final boolean paused) {
        this(name, group, serializedValue, paused, false, null);
    }

    /**
     * Create a new TriggerWrapper from parameters.
     *
     * @param name the name of the trigger
     * @param group the trigger group
     * @param serializedValue a string representing the serialized trigger
     * @param paused if the trigger is paused
     */
    public TriggerWrapper(final String name, final String group, final String serializedValue, boolean paused,
            final boolean acquired, final String nodeId) {
        this.name = name;
        this.group = group;
        this.acquired = acquired;
        this.serialized = serializedValue;
        if (paused) {
            state = Trigger.STATE_PAUSED;
        } else {
            state = Trigger.STATE_NORMAL;
        }
        this.nodeId = nodeId;
    }
    
    /**
     * Create a new TriggerWrapper from a JsonValue object representing the trigger.
     * 
     * @param value the JsonValue object
     * @param paused if the trigger is paused
     */
    public TriggerWrapper(final JsonValue value, final boolean paused) {
        //this(value.asMap(), paused);
        serialized = value.get("serialized").asString();
        name = value.get("name").asString();
        group = value.get("group").asString();
        previous_state = value.get("previous_state").asInteger();
        acquired = value.get("acquired").asBoolean();
        revision = value.get("_rev").asString();
        if (paused) {
            state = Trigger.STATE_PAUSED;
        } else {
            state = Trigger.STATE_NORMAL;
        }
        nodeId = value.get("nodeId").asString();
    }
    
    /**
     * Create a new TriggerWrapper from a repo Map object.
     * 
     * @param map repo Map object
     */
    public TriggerWrapper(final JsonValue map) {
        serialized = map.get("serialized").asString();
        name = map.get("name").asString();
        group = map.get("group").asString();
        state = map.get("state").asInteger();
        previous_state = map.get("previous_state").asInteger();
        acquired = map.get("acquired").asBoolean();
        revision = map.get("_rev").asString();
        nodeId = map.get("nodeId").asString();
    }

    /**
     * Sets the TriggerWrapper in the "paused" state.
     * 
     * @return the paused TriggerWrapper
     */
    public TriggerWrapper pause() {
        // It doesn't make sense to pause a completed trigger
        if (state != Trigger.STATE_COMPLETE) {
            previous_state = state;
            state = Trigger.STATE_PAUSED;
        }
        return this;
    }

    /**
     * Resumes the TriggerWrapper from the paused state
     * 
     * @return the resumed TriggerWrapper
     */
    public TriggerWrapper resume() {
        return reState(Trigger.STATE_PAUSED);
    }
    
    /**
     * Returns true if the TriggerWrapper is paused, false otherwise.
     * 
     * @return  true if the TriggerWrapper is paused, false otherwise
     */
    public boolean isPaused() {
        return state == Trigger.STATE_PAUSED;
    }

    /**
     * Sets the TriggerWrapper in the "blocked" state
     * 
     * @return the blocked TriggerWrapper
     */
    public TriggerWrapper block() {
        // It doesn't make sense to pause a completed trigger
        if (state != Trigger.STATE_COMPLETE) {
            previous_state = state;
            state = Trigger.STATE_BLOCKED;
        }
        return this;
    }

    /**
     * Unblocks the TriggerWrapper
     * 
     * @return the unblocked TriggerWrapper
     */
    public TriggerWrapper unblock() {
        return reState(Trigger.STATE_BLOCKED);
    }
    
    private TriggerWrapper reState(int fromState) {
        if (state == fromState) {
            if (previous_state != Trigger.STATE_NONE) {
                state = previous_state;
                previous_state = Trigger.STATE_NONE;
            } else {
                state = Trigger.STATE_NORMAL;
            }
        }
        return this;
    }
    
    /**
     * Updates the TriggerWrappers serialized Trigger object
     * 
     * @param trigger   The trigger update
     * @throws JobPersistenceException
     */
    public void updateTrigger(Trigger trigger) throws JobPersistenceException {
        serialized = RepoJobStoreUtils.serialize(trigger);
    }
    
    /**
     * Deserializes and returns the Trigger object for this TriggerWrapper
     * 
     * @return  the deserialized Trigger object
     * @throws JobPersistenceException
     */
    public Trigger getTrigger() throws JobPersistenceException {
        return (Trigger) RepoJobStoreUtils.deserialize(serialized);
    }
    
    /**
     * Gets the seriailized Trigger object.
     * 
     * @return  the serialized Trigger object
     */
    public String getSerialized() {
        return serialized;
    }

    /**
     * Sets the serialized Trigger object.
     * 
     * @param serialized    the serialized Trigger object
     */
    public void setSerialized(String serialized) {
        this.serialized = serialized;
    }

    /**
     * Returns the state of the Trigger.
     * 
     * @return the state of the Trigger
     */
    public int getState() {
        return state;
    }

    /**
     * Sets the state of the Trigger.
     * 
     * @param state the state of the Trigger
     */
    public void setState(int state) {
        this.state = state;
    }

    /**
     * Returns a JsonValue object wrapper around the object map for the TriggerWrapper.
     * 
     * @return a JsonValue object
     */
    public JsonValue getValue() {
        return json(object(
                field("serialized", serialized),
                field("name", name),
                field("group", group),
                field("previous_state", previous_state),
                field("state", state),
                field("acquired", acquired),
                field("nodeId", nodeId)
        ));
    }

    /**
     * Returns a String representing the details of the TriggerWrapper.
     * 
     * @return  a String representing the TriggerWrapper details
     */
    public String toDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append("name:     ").append(name).append("\n");
        sb.append("group:    ").append(group).append("\n");
        sb.append("state:    ").append(state).append("\n");
        sb.append("p-state:  ").append(previous_state).append("\n");
        sb.append("acquired: ").append(acquired).append("\n");
        sb.append("nodeId:   ").append(nodeId).append("\n");
        return sb.toString();
    }
    
    /**
     * Returns the name of the Trigger.
     * 
     * @return  the name of the Trigger
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the group name that the Trigger belongs to.
     * 
     * @return  the group name
     */
    public String getGroup() {
        return group;
    }

    /**
     * Return true if the Trigger is in the "acquired" state, false otherwise.
     * 
     * @return  true if the Trigger is acquired, false otherwise
     */
    public boolean isAcquired() {
        return acquired;
    }
    
    /**
     * Sets the TriggerWrapper's "acquired" state.
     * 
     * @param acquired  true if "acquired", false otherwise
     */
    public void setAcquired(boolean acquired) {
        this.acquired = acquired;
    }

    /**
     * Returns the revision, as set by the repo
     * 
     * @return the repo revision
     */
    public String getRevision() {
        return revision;
    }

    /**
     * Gets the node id that has acquired this trigger.
     * @return the nodeId.
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Sets the nodeId that has acquired this trigger.
     * @param nodeId the nodeId.
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
}
