/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.jumpnote.web.server;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.Transaction;
import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.example.jumpnote.allshared.AllConfig;
import com.example.jumpnote.allshared.JsonRpcException;
import com.example.jumpnote.allshared.JsonRpcMethod;
import com.example.jumpnote.allshared.JumpNoteProtocol;
import com.example.jumpnote.javashared.Util;
import com.example.jumpnote.web.jsonrpc.server.JsonRpcServlet;
import com.example.jumpnote.web.server.ModelImpl.DeviceRegistration;
import com.example.jumpnote.web.server.ModelImpl.Note;
import com.example.jumpnote.web.server.ModelImpl.UserInfo;
import com.google.android.c2dm.server.C2DMessaging;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.users.User;

/**
 * The server side implementation of the JumpNote JSON-RPC service.
 */
@SuppressWarnings("serial")
public class JumpNoteServlet extends JsonRpcServlet {

    public static final int DEBUG = -1; // 1 == yes, 0 == no, -1 == decide based on server info

    private static final Logger log = Logger.getLogger(JumpNoteServlet.class.getName());

    private static final String PROTOCOL_VERSION = "1";
    public static final String DEVICE_TYPE_ANDROID = "android";

    @Override
    @SuppressWarnings("all")
    protected boolean isDebug(HttpServletRequest req) {
        if (DEBUG == 1)
            return true;
        else if (DEBUG == 0)
            return false;
        else
            return this.getServletContext().getServerInfo().contains("Development");
    }

    @JsonRpcMethod(method = JumpNoteProtocol.UserInfo.METHOD)
    public JSONObject userInfo(final CallContext context) throws JSONException, JsonRpcException {
        String continueUrl = context.getParams().optString(
                JumpNoteProtocol.UserInfo.ARG_LOGIN_CONTINUE, "/");

        JSONObject data = new JSONObject();
        if (context.getUserService().isUserLoggedIn()) {
            UserInfo userInfo = new UserInfo(context.getUserService().getCurrentUser());
            data.put(JumpNoteProtocol.UserInfo.RET_USER, userInfo.toJSON());
            data.put(JumpNoteProtocol.UserInfo.RET_LOGOUT_URL, context.getUserService().createLogoutURL(continueUrl));
        } else {
            data.put(JumpNoteProtocol.UserInfo.RET_LOGIN_URL, context.getUserService().createLoginURL(continueUrl));
        }

        return data;
    }

    @JsonRpcMethod(method = JumpNoteProtocol.ServerInfo.METHOD)
    public JSONObject serverInfo(final CallContext context) throws JSONException, JsonRpcException {
        JSONObject responseJson = new JSONObject();
        responseJson.put(JumpNoteProtocol.ServerInfo.RET_PROTOCOL_VERSION, PROTOCOL_VERSION);
        return responseJson;
    }

    @JsonRpcMethod(method = JumpNoteProtocol.NotesList.METHOD, requires_login = true)
    public JSONObject notesList(final CallContext context) throws JSONException, JsonRpcException {
        UserInfo userInfo = getCurrentUserInfo(context);

        // Note: this would be inefficient for large note collections
        Query query = context.getPersistenceManager().newQuery(Note.class);
        query.setFilter("ownerKey == ownerKeyParam && pendingDelete == false");
        query.declareParameters(Key.class.getName() + " ownerKeyParam");
        @SuppressWarnings("unchecked")
        List<Note> notes = (List<Note>) query.execute(userInfo.getKey());

        JSONObject responseJson = new JSONObject();
        try {
            JSONArray notesJson = new JSONArray();
            for (Note note : notes) {
                notesJson.put(note.toJSON());
            }

            responseJson.put(JumpNoteProtocol.NotesList.RET_NOTES, notesJson);
        } catch (JSONException e) {
            throw new JsonRpcException(500, "Error serializing response.", e);
        }

        return responseJson;
    }

    @JsonRpcMethod(method = JumpNoteProtocol.NotesGet.METHOD, requires_login = true)
    public JSONObject notesGet(final CallContext context) throws JSONException, JsonRpcException {
        UserInfo userInfo = getCurrentUserInfo(context);

        String noteId = context.getParams().getString(JumpNoteProtocol.NotesGet.ARG_ID);
        Key noteKey = Note.makeKey(userInfo.getId(), noteId);
        try {
            Note note = context.getPersistenceManager().getObjectById(Note.class, noteKey);
            if (note.isPendingDelete()) {
                throw new JDOObjectNotFoundException();
            }
            if (!note.getOwnerId().equals(userInfo.getId())) {
                throw new JsonRpcException(403, "You do not have permission to access this note.");
            }
            return (JSONObject) note.toJSON();
        } catch (JDOObjectNotFoundException e) {
            throw new JsonRpcException(404, "Note with ID " + noteId + " does not exist.");
        }
    }

    @JsonRpcMethod(method = JumpNoteProtocol.NotesCreate.METHOD, requires_login = true)
    public JSONObject notesCreate(final CallContext context) throws JSONException, JsonRpcException {
        UserInfo userInfo = getCurrentUserInfo(context);

        String clientDeviceId = null;
        JSONObject noteJson;
        Note note;
        try {
            clientDeviceId = context.getParams().optString(JumpNoteProtocol.ARG_CLIENT_DEVICE_ID);
            noteJson = context.getParams().getJSONObject(JumpNoteProtocol.NotesCreate.ARG_NOTE);
            noteJson.put("owner_id", userInfo.getId());
            note = new Note(noteJson);
        } catch (JSONException e) {
            throw new JsonRpcException(400, "Invalid note parameter.", e);
        }

        context.getPersistenceManager().makePersistent(note);
        noteJson = (JSONObject) note.toJSON(); // get new parameters like ID, creation date, etc.

        enqueueDeviceMessage(context.getPersistenceManager(), userInfo, clientDeviceId);
        
        JSONObject responseJson = new JSONObject();
        responseJson.put(JumpNoteProtocol.NotesCreate.RET_NOTE, noteJson);
        return responseJson;
    }
    

    public void enqueueDeviceMessage(PersistenceManager pm,
            UserInfo userInfo, String clientDeviceId) {

        Query query = pm.newQuery(DeviceRegistration.class);
        query.setFilter("ownerKey == ownerKeyParam");
        query.declareParameters(Key.class.getName() + " ownerKeyParam");
        @SuppressWarnings("unchecked")
        List<DeviceRegistration> registrations = (List<DeviceRegistration>)
                query.execute(userInfo.getKey());

        int numDeviceMessages = 0;
        for (DeviceRegistration registration : registrations) {
            if (registration.getDeviceId().equals(clientDeviceId) ||
                registration.getRegistrationToken() == null)
                continue;
            if (DEVICE_TYPE_ANDROID.equals(registration.getDeviceType())) {
                ++numDeviceMessages;
                String email = userInfo.getEmail();
                
                String collapseKey = Long.toHexString(email.hashCode());
                
                try {
                    C2DMessaging.get(getServletContext()).sendWithRetry(
                        registration.getRegistrationToken(),
                        collapseKey,
                        AllConfig.C2DM_MESSAGE_EXTRA,
                        AllConfig.C2DM_MESSAGE_SYNC,
                        AllConfig.C2DM_ACCOUNT_EXTRA,
                        email);
                } catch (IOException ex) {
                    log.severe("Can't send C2DM message, next manual sync " +
                    		"will get the changes.");
                }
            }
        }

        log.info("Scheduled " + numDeviceMessages + " C2DM device messages for user " +
                userInfo.getEmail() + ".");
    }

    @JsonRpcMethod(method = JumpNoteProtocol.NotesEdit.METHOD, requires_login = true)
    public JSONObject notesEdit(final CallContext context) throws JSONException, JsonRpcException {
        UserInfo userInfo = getCurrentUserInfo(context);

        String clientDeviceId;
        JSONObject noteJson;
        Note note;
        String noteId = "n/a";
        Transaction tx = context.getPersistenceManager().currentTransaction();
        try {
            clientDeviceId = context.getParams().optString(JumpNoteProtocol.ARG_CLIENT_DEVICE_ID);
            noteJson = context.getParams().getJSONObject(JumpNoteProtocol.NotesEdit.ARG_NOTE);
            noteId = noteJson.getString("id");
            Key noteKey = Note.makeKey(userInfo.getId(), noteId);

            tx.begin();
            note = context.getPersistenceManager().getObjectById(Note.class, noteKey);
            if (!note.getOwnerId().equals(userInfo.getId())) {
                throw new JsonRpcException(403, "You do not have permission to modify this note.");
            }
            noteJson.put("owner_id", userInfo.getId());
            note.fromJSON(noteJson);
            tx.commit();
        } catch (JSONException e) {
            throw new JsonRpcException(400, "Invalid note parameter.", e);
        } catch (JDOObjectNotFoundException e) {
            throw new JsonRpcException(404, "Note with ID " + noteId + " does not exist.");
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }

        enqueueDeviceMessage(context.getPersistenceManager(), userInfo, clientDeviceId);

        noteJson = (JSONObject) note.toJSON(); // get more parameters like ID, creation date, etc.
        JSONObject responseJson = new JSONObject();
        responseJson.put(JumpNoteProtocol.NotesEdit.RET_NOTE, noteJson);
        return responseJson;
    }

    @JsonRpcMethod(method = JumpNoteProtocol.NotesDelete.METHOD, requires_login = true)
    public JSONObject notesDelete(final CallContext context) throws JSONException, JsonRpcException {
        UserInfo userInfo = getCurrentUserInfo(context);

        String clientDeviceId = null;
        Note note;
        String noteId;
        try {
            clientDeviceId = context.getParams().optString(JumpNoteProtocol.ARG_CLIENT_DEVICE_ID);
            noteId = context.getParams().getString(JumpNoteProtocol.NotesDelete.ARG_ID);
        } catch (JSONException e) {
            throw new JsonRpcException(400, "Invalid note ID.", e);
        }

        Transaction tx = context.getPersistenceManager().currentTransaction();
        try {
            tx.begin();
            note = context.getPersistenceManager().getObjectById(Note.class,
                    Note.makeKey(userInfo.getId(), noteId));
            if (note.isPendingDelete()) {
                throw new JDOObjectNotFoundException();
            }
            if (!note.getOwnerId().equals(userInfo.getId())) {
                throw new JsonRpcException(403, "You do not have permission to modify this note.");
            }

            note.markForDeletion();
            tx.commit();
        } catch (JDOObjectNotFoundException e) {
            throw new JsonRpcException(404, "Note with ID " + noteId + " does not exist.");
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }

        enqueueDeviceMessage(context.getPersistenceManager(), userInfo, clientDeviceId);
        return null;
    }

    @JsonRpcMethod(method = JumpNoteProtocol.NotesSync.METHOD, requires_login = true)
    public JSONObject notesSync(final CallContext context) throws JSONException, JsonRpcException {
        // This method should return a list of updated notes since a current
        // date, optionally reconciling/merging a set of a local notes.
        String clientDeviceId = null;
        UserInfo userInfo = getCurrentUserInfo(context);
        Date sinceDate;

        try {
            clientDeviceId = context.getParams().optString(JumpNoteProtocol.ARG_CLIENT_DEVICE_ID);
            sinceDate = Util.parseDateISO8601(context.getParams().getString(JumpNoteProtocol.NotesSync.ARG_SINCE_DATE));
        } catch (ParseException e) {
            throw new JsonRpcException(400, "Invalid since_date.", e);
        } catch (JSONException e) {
            throw new JsonRpcException(400, "Invalid since_date.", e);
        }

        JSONObject responseJson = new JSONObject();
        JSONArray notesJson = new JSONArray();
        Transaction tx = context.getPersistenceManager().currentTransaction();
        Date newSinceDate = new Date();
        try {
            tx.begin();
            List<Note> localNotes = new ArrayList<Note>();
            if (context.getParams().has(JumpNoteProtocol.NotesSync.ARG_LOCAL_NOTES)) {
                JSONArray localChangesJson = context.getParams().getJSONArray(JumpNoteProtocol.NotesSync.ARG_LOCAL_NOTES);
                for (int i = 0; i < localChangesJson.length(); i++) {
                    try {
                        JSONObject noteJson = localChangesJson.getJSONObject(i);

                        if (noteJson.has("id")) {
                            Key existingNoteKey = Note.makeKey(userInfo.getId(),
                                    noteJson.get("id").toString());
                            try {
                                Note existingNote = (Note) context.getPersistenceManager().getObjectById(
                                        Note.class, existingNoteKey);
                                if (!existingNote.getOwnerId().equals(userInfo.getId())) {
                                    // User doesn't have permission to edit this note. Instead of
                                    // throwing an error, just re-create it on the server side.
                                    //throw new JsonRpcException(403,
                                    //        "You do not have permission to modify this note.");
                                    noteJson.remove("id");
                                }
                            } catch (JDOObjectNotFoundException e) {
                                // Note doesn't exist, instead of throwing an error,
                                // just re-create the note on the server side (unassign its ID).
                                //throw new JsonRpcException(404, "Note with ID "
                                //        + noteJson.get("id").toString() + " does not exist.");
                                noteJson.remove("id");
                            }
                        }

                        noteJson.put("owner_id", userInfo.getId());
                        Note localNote = new Note(noteJson);
                        localNotes.add(localNote);
                    } catch (JSONException e) {
                        throw new JsonRpcException(400, "Invalid local note content.", e);
                    }
                }
            }

            // Query server-side note changes.
            Query query = context.getPersistenceManager().newQuery(Note.class);
            query.setFilter("ownerKey == ownerKeyParam && modifiedDate > sinceDate");
            query.setOrdering("modifiedDate desc");
            query.declareParameters(Key.class.getName() + " ownerKeyParam, java.util.Date sinceDate");
            @SuppressWarnings("unchecked")
            List<Note> notes = (List<Note>) query.execute(userInfo.getKey(), sinceDate);

            // Now merge the lists and conflicting objects.
            Reconciler<Note> reconciler = new Reconciler<Note>() {
                @Override
                public Note reconcile(Note o1, Note o2) {
                    boolean pick1 = o1.getModifiedDate().after(o2.getModifiedDate());

                    // Make sure only the chosen version of the note is persisted
                    context.getPersistenceManager().makeTransient(pick1 ? o2 : o1);

                    return pick1 ? o1 : o2;
                }
            };

            Collection<Note> reconciledNotes = reconciler.reconcileLists(notes, localNotes);

            for (Note note : reconciledNotes) {
                // Save the note.
                context.getPersistenceManager().makePersistent(note);

                // Put it in the response output.
                notesJson.put(note.toJSON());
            }
            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }

        enqueueDeviceMessage(context.getPersistenceManager(), userInfo, clientDeviceId);

        responseJson.put(JumpNoteProtocol.NotesSync.RET_NOTES, notesJson);
        responseJson.put(JumpNoteProtocol.NotesSync.RET_NEW_SINCE_DATE,
                Util.formatDateISO8601(newSinceDate));
        return responseJson;
    }

    @JsonRpcMethod(method = JumpNoteProtocol.DevicesRegister.METHOD, requires_login = true)
    public JSONObject devicesRegister(final CallContext context) throws JSONException, JsonRpcException {
        UserInfo userInfo = getCurrentUserInfo(context);

        JSONObject registrationJson;
        DeviceRegistration registrationParam;
        try {
            registrationJson = context.getParams().getJSONObject(JumpNoteProtocol.DevicesRegister.ARG_DEVICE);
            registrationJson.put("owner_id", userInfo.getId());
            registrationParam = new DeviceRegistration(registrationJson);
        } catch (JSONException e) {
            throw new JsonRpcException(400, "Invalid device parameter.", e);
        }

        Transaction tx = context.getPersistenceManager().currentTransaction();
        try {
            tx.begin();
            Query query = context.getPersistenceManager().newQuery(DeviceRegistration.class);
            query.setFilter("ownerKey == ownerKeyParam && deviceId == deviceIdParam");
            query.declareParameters(Key.class.getName() + " ownerKeyParam, String deviceIdParam");
            @SuppressWarnings("unchecked")
            List<DeviceRegistration> registrations = (List<DeviceRegistration>)
                    query.execute(userInfo.getKey(), registrationParam.getDeviceId());

            // Update all existing registration tokens.
            boolean registeredForUser = false;
            for (DeviceRegistration registration : registrations) {
                if (registration.getOwnerId().equals(userInfo.getId()))
                    registeredForUser = true;
                registration.setRegistrationToken(registrationParam.getRegistrationToken());
            }

            // Register the device for the logged in user if not already registered.
            if (!registeredForUser) {
                context.getPersistenceManager().makePersistent(registrationParam);
            }
            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }

        registrationJson = (JSONObject) registrationParam.toJSON();

        JSONObject responseJson = new JSONObject();
        responseJson.put(JumpNoteProtocol.DevicesRegister.RET_DEVICE, registrationJson);
        return responseJson;
    }

    @JsonRpcMethod(method = JumpNoteProtocol.DevicesUnregister.METHOD, requires_login = true)
    public JSONObject devicesUnregister(final CallContext context) throws JSONException, JsonRpcException {
        UserInfo userInfo = getCurrentUserInfo(context);

        String deviceId;
        try {
            deviceId = context.getParams().getString(JumpNoteProtocol.DevicesUnregister.ARG_DEVICE_ID);
        } catch (JSONException e) {
            throw new JsonRpcException(400, "Invalid device ID parameter.", e);
        }

        Transaction tx = context.getPersistenceManager().currentTransaction();
        try {
            tx.begin();
            Query query = context.getPersistenceManager().newQuery(DeviceRegistration.class);
            query.setFilter("ownerKey == ownerKeyParam && deviceId == deviceIdParam");
            query.declareParameters(Key.class.getName() + " ownerKeyParam, String deviceIdParam");
            @SuppressWarnings("unchecked")
            List<DeviceRegistration> registrations = (List<DeviceRegistration>)
                    query.execute(userInfo.getKey(), deviceId);

            if (registrations.size() == 0) {
                throw new JsonRpcException(404, "Device with provided ID is not registered.");
            }

            for (DeviceRegistration registration : registrations) {
                context.getPersistenceManager().deletePersistent(registration);
            }
            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }

        return null;
    }

    @JsonRpcMethod(method = JumpNoteProtocol.DevicesClear.METHOD, requires_login = true)
    public JSONObject devicesClear(final CallContext context) throws JSONException, JsonRpcException {
        UserInfo userInfo = getCurrentUserInfo(context);

        Transaction tx = context.getPersistenceManager().currentTransaction();
        try {
            tx.begin();
            Query query = context.getPersistenceManager().newQuery(DeviceRegistration.class);
            query.setFilter("ownerKey == ownerKeyParam");
            query.declareParameters(Key.class.getName() + " ownerKeyParam");
            @SuppressWarnings("unchecked")
            List<DeviceRegistration> registrations = (List<DeviceRegistration>)
                    query.execute(userInfo.getKey());

            for (DeviceRegistration registration : registrations) {
                context.getPersistenceManager().deletePersistent(registration);
            }
            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }

        return null;
    }

    public UserInfo getCurrentUserInfo(final CallContext context) {
        if (!context.getUserService().isUserLoggedIn())
            return null;

        User user = context.getUserService().getCurrentUser();

        try {
            UserInfo userInfo = context.getPersistenceManager().getObjectById(UserInfo.class,
                    user.getUserId());
            return userInfo;
        } catch (JDOObjectNotFoundException e) {
            UserInfo userInfo = new UserInfo(user);
            context.getPersistenceManager().makePersistent(userInfo);
            return userInfo;
        }
    }
}
