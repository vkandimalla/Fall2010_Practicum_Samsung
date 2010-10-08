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

package com.example.jumpnote.web.jsonrpc.server;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.example.jumpnote.allshared.JsonRpcException;
import com.example.jumpnote.allshared.JsonRpcMethod;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;

/**
 * A base servlet class that handles JSON-RPC requests on Google App Engine. Extending classes
 * define RPC methods by adding {@link JsonRpcMethod} annotations to methods. User login via
 * Google Accounts is handled using App Engine's {@link UserService}.
 */
@SuppressWarnings("serial")
public class JsonRpcServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(JsonRpcServlet.class.getName());

    private static PersistenceManagerFactory pmfInstance;

    private Map<String, Method> mMethods = new HashMap<String, Method>();

    public JsonRpcServlet() {
        for (Method method : getClass().getMethods()) {
            JsonRpcMethod rpcMethodAnnotation = method.getAnnotation(JsonRpcMethod.class);
            if (rpcMethodAnnotation != null) {
                mMethods.put(rpcMethodAnnotation.method(), method);
            }
        }
    }

    public void init() {
        /**
         * Initialize PMF - we use a context attribute, so other servlets can
         * be share the same instance. This is similar with a shared static 
         * field, but avoids dependencies.
         */
        pmfInstance = 
            (PersistenceManagerFactory) getServletContext().getAttribute(
                    PersistenceManagerFactory.class.getName());
        if (pmfInstance == null) {
            pmfInstance = JDOHelper
                .getPersistenceManagerFactory("transactions-optional");
            getServletContext().setAttribute(
                    PersistenceManagerFactory.class.getName(),
                    pmfInstance);
        }
    } 
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        boolean debug = isDebug(req);
        boolean prettyPrint = false;

        JSONTokener tokener = new JSONTokener(req.getReader());
        JSONObject requestJson;
        JSONArray callsJson = null;
        JSONObject responseJson = new JSONObject();
        JSONArray resultsJson = new JSONArray();

        PersistenceManager pm = pmfInstance.getPersistenceManager();
        UserService userService = UserServiceFactory.getUserService();
        CallContext context = new CallContext(req, null, pm, userService);

        try {
            try {
                requestJson = new JSONObject(tokener);
                if (requestJson.has("pretty") && requestJson.getBoolean("pretty"))
                    prettyPrint = true;

                callsJson = requestJson.getJSONArray("calls");
            } catch (JSONException e) {
                responseJson.put("error", 400);
                responseJson.put("message", "Error parsing request object: " + e.getMessage());
            }

            if (callsJson != null) {
                for (int i = 0; i < callsJson.length(); i++) {
                    JSONObject callParamsJson = callsJson.getJSONObject(i);
                    context.setParams(callParamsJson);

                    JSONObject resultJson = new JSONObject();
                    try {
                        Object dataJson = performCall(context);
                        resultJson.put("data", (dataJson != null) ? dataJson : new JSONObject());
                    } catch (JsonRpcException e) {
                        if (debug && e.getHttpCode() != 403)
                            throw new RuntimeException(e);
                        resultJson.put("error", e.getHttpCode());
                        resultJson.put("message", e.getMessage());
                        log.log(Level.SEVERE,
                                "JsonRpcException (method: " + e.getMethodName() + ")", e);
                    }

                    resultsJson.put(resultJson);
                }
            }

            responseJson.put("results", resultsJson);

            resp.setContentType("application/json");

            if (prettyPrint)
                resp.getWriter().write(responseJson.toString(2) + "\n");
            else
                resp.getWriter().write(responseJson.toString() + "\n");

        } catch (JSONException e) {
            if (debug)
                throw new RuntimeException(e);
            resp.setStatus(500);
            resp.setContentType("text/plain");
            resp.getWriter().write("Internal JSON serialization error: " + e.getMessage());
            log.log(Level.SEVERE, "JSONException", e);
        } finally {
            pm.close();
        }
    }

    private Object performCall(CallContext context) throws JsonRpcException {
        if (!context.getParams().has("method")) {
            throw new JsonRpcException(400, "No method specified.");
        }

        Method method;
        try {
            String methodName = context.getParams().getString("method");
            if (!mMethods.containsKey(methodName)) {
                throw new JsonRpcException(400, "Unknown method.");
            }
            method = mMethods.get(methodName);
        } catch (JSONException e) {
            throw new JsonRpcException(400, "Invalid method.");
        }

        JsonRpcMethod rpcMethodAnnotation = method.getAnnotation(JsonRpcMethod.class);
        String methodName = rpcMethodAnnotation.method(); 

        if (rpcMethodAnnotation.requires_login() && !context.getUserService().isUserLoggedIn()) {
            throw new JsonRpcException(403, methodName,
                    "You must authenticate to run this RPC call.");
        }

        try {
            Object data = method.invoke(this, context);
            return data;

        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof JsonRpcException) {
                JsonRpcException e2 = (JsonRpcException) e.getCause();
                e2.setMethodName(methodName);
                throw e2;
            } else if (e.getCause() instanceof JSONException) {
                throw new JsonRpcException(500, methodName,
                        "Internal serialization error: " + e.getMessage(), e.getCause());
            } else {
                throw new JsonRpcException(500, methodName,
                        "Internal error: " + e.getMessage(), e.getCause());
            }
        } catch (IllegalArgumentException e) {
            throw new JsonRpcException(500, methodName,
                    "Internal error: Illegal RPC call arguments.");
        } catch (IllegalAccessException e) {
            throw new JsonRpcException(500, methodName,
                    "Internal error: Illegal RPC access exception.");
        }
    }

    protected boolean isDebug(HttpServletRequest req) {
        return false;
    }

    public class CallContext {
        private HttpServletRequest request;

        private JSONObject params;

        private PersistenceManager pm;

        private UserService userService;

        public CallContext(HttpServletRequest request, JSONObject params, PersistenceManager pm,
                UserService userService) {
            this.request = request;
            this.params = params;
            this.pm = pm;
            this.userService = userService;
        }

        public HttpServletRequest getRequest() {
            return request;
        }

        public JSONObject getParams() {
            return params;
        }

        public void setParams(JSONObject params) {
            this.params = params;
        }

        public PersistenceManager getPersistenceManager() {
            return pm;
        }

        public UserService getUserService() {
            return userService;
        }
    }
}
