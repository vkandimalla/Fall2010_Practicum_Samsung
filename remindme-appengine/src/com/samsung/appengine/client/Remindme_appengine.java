package com.samsung.appengine.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.samsung.appengine.allshared.JsonRpcClient;
import com.samsung.appengine.allshared.RemindMeProtocol;
import com.samsung.appengine.allshared.JsonRpcClient.Call;
import com.samsung.appengine.allshared.JsonRpcException;
import com.samsung.appengine.client.screens.WelcomeScreen;
import com.samsung.appengine.jsonrpc.gwt.JsonRpcGwtClient;
import com.samsung.appengine.shared.FieldVerifier;


import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class Remindme_appengine implements EntryPoint {
	
private static final int TRANSIENT_MESSAGE_HIDE_DELAY = 5000;
    
    private final ScreenContainer mScreenContainer = new ScreenContainer();
    public static RootPanel sMessagePanel = null;

    public static String sLoginUrl = "";
    public static final JsonRpcGwtClient sJsonRpcClient = new JsonRpcGwtClient("/remindmerpc");
    public static Map<String, ModelJso.Alert> sAlerts = new HashMap<String, ModelJso.Alert>();
    public static ModelJso.UserInfo sUserInfo = null;
	/**
	 * The message displayed to the user when the server cannot be reached or
	 * returns an error.
	 */
	private static final String SERVER_ERROR = "An error occurred while "
			+ "attempting to contact the server. Please check your network "
			+ "connection and try again.";

	/**
	 * Create a remote service proxy to talk to the server-side Greeting service.
	 */
	private final GreetingServiceAsync greetingService = GWT
			.create(GreetingService.class);

	/**
	 * This is the entry point method.
	 */
	public void onModuleLoad() {
		 showMessage("Loading...", false);

	        // Create login/logout links
	        loadData(new Runnable() {
	            public void run() {
	                hideMessage();
	                if (sUserInfo == null) {
	                    RootPanel.get("screenPanel").add(new WelcomeScreen());
	                } else {
	                	RootPanel.get("screenPanel").add(new WelcomeScreen());
//	                    mScreenContainer.addScreen("home", new NotesList());
//	                    mScreenContainer.addScreen("note", new NoteEditor());
//	                    mScreenContainer.setDefault("home");
//	                    mScreenContainer.install(RootPanel.get("screenPanel"));
	                }
	            }
	        });
	}
	
	public void loadData(final Runnable callback) {
        List<Call> calls = new ArrayList<Call>();

        // Get and populate login information
        JSONObject userInfoParams = new JSONObject();
        userInfoParams.put(RemindMeProtocol.UserInfo.ARG_LOGIN_CONTINUE,
                new JSONString(Window.Location.getHref()));

        final RootPanel loginPanel = RootPanel.get("loginPanel");
        calls.add(new Call(RemindMeProtocol.UserInfo.METHOD, userInfoParams));
        calls.add(new Call(RemindMeProtocol.AlertsList.METHOD, null));

        sJsonRpcClient.callBatch(calls, new JsonRpcClient.BatchCallback() {
            public void onData(Object[] data) {
                // Process userInfo RPC call results
                JSONObject userInfoJson = (JSONObject) data[0];
                if (userInfoJson.containsKey(RemindMeProtocol.UserInfo.RET_USER)) {
                    Remindme_appengine.sUserInfo = (ModelJso.UserInfo) userInfoJson.get(
                            RemindMeProtocol.UserInfo.RET_USER).isObject().getJavaScriptObject();
                    InlineLabel label = new InlineLabel();
                    label.getElement().setId("userNameLabel");
                    label.setText(sUserInfo.getEmail());
                    loginPanel.add(label);

                    loginPanel.add(new InlineLabel(" | "));

                    Anchor anchor = new Anchor("Sign out",
                            userInfoJson.get(RemindMeProtocol.UserInfo.RET_LOGOUT_URL).isString()
                            .stringValue());
                    loginPanel.add(anchor);
                } else {
                    sLoginUrl = userInfoJson.get(RemindMeProtocol.UserInfo.RET_LOGIN_URL).isString().stringValue();
                    Anchor anchor = new Anchor("Sign in", sLoginUrl);
                    loginPanel.add(anchor);
                }

                // Process notesList RPC call results
                JSONObject notesListJson = (JSONObject) data[1];
                if (notesListJson != null) {
                    JSONArray notesJson = notesListJson.get(RemindMeProtocol.AlertsList.RET_NOTES).isArray();
                    for (int i = 0; i < notesJson.size(); i++) {
                        ModelJso.Alert alert = (ModelJso.Alert) notesJson.get(i).isObject()
                                .getJavaScriptObject();
                        sAlerts.put(alert.getId(), alert);
                    }
                }

                callback.run();
            }

            public void onError(int callIndex, JsonRpcException caught) {
                // Don't show an error if the notes.list call failed with 403 forbidden, since
                // that's normal in the case of a user not yet logging in.
                if (callIndex == 1 && caught.getHttpCode() == 403)
                    return;

                showMessage("Error: " + caught.getMessage(), false);
            }
        });
    }

    public static void showMessage(String message, boolean isTransient) {
        if (sMessagePanel == null) {
            sMessagePanel = RootPanel.get("messagePanel");
        }

        sMessagePanel.setVisible(true);
        sMessagePanel.getElement().setInnerText(message);
        if (isTransient) {
            new Timer() {
                @Override
                public void run() {
                    sMessagePanel.setVisible(false);
                }
            }.schedule(TRANSIENT_MESSAGE_HIDE_DELAY);
        }
    }

    public static void hideMessage() {
        sMessagePanel.setVisible(false);
    }
}
