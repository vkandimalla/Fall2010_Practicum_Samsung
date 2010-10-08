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

package com.example.jumpnote.web.jsonrpc.gwt;

import com.example.jumpnote.allshared.JsonRpcClient;
import com.example.jumpnote.allshared.JsonRpcException;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;

import java.util.Arrays;
import java.util.List;

public class JsonRpcGwtClient implements JsonRpcClient {
    private final String mRpcUrl;

    public JsonRpcGwtClient(String rpcUrl) {
        mRpcUrl = rpcUrl;
    }

    public void call(String methodName, Object params, final Callback callback) {
        callBatch(Arrays.asList(new JsonRpcClient.Call[] {
            new JsonRpcClient.Call(methodName, params)
        }), new JsonRpcClient.BatchCallback() {
            public void onError(int callIndex, JsonRpcException caught) {
                callback.onError(caught);
            }

            public void onData(Object[] data) {
                if (data[0] != null)
                    callback.onSuccess(data[0]);
            }
        });
    }

    public void callBatch(final List<JsonRpcClient.Call> calls, final BatchCallback callback) {
        RequestBuilder builder = new RequestBuilder(RequestBuilder.POST, mRpcUrl);

        JSONObject requestJson = new JSONObject();
        JSONArray callsJson = new JSONArray();
        for (int i = 0; i < calls.size(); i++) {
            JsonRpcClient.Call call = calls.get(i);

            JSONObject callJson = new JSONObject();
            callJson.put("method", new JSONString(call.getMethodName()));

            if (call.getParams() != null) {
                JSONObject callParams = (JSONObject) call.getParams();
                for (String key : callParams.keySet()) {
                    callJson.put(key, callParams.get(key));
                }
            }

            callsJson.set(i, callJson);
        }

        requestJson.put("calls", callsJson);

        try {
            builder.sendRequest(requestJson.toString(), new RequestCallback() {
                public void onError(Request request, Throwable e) {
                    callback.onError(-1, new JsonRpcException(-1, e.getMessage()));
                }

                public void onResponseReceived(Request request, Response response) {
                    if (200 == response.getStatusCode()) {
                        JSONObject responseJson = JSONParser.parse(response.getText()).isObject();
                        JSONArray resultsJson = responseJson.get("results").isArray();
                        Object[] resultData = new Object[calls.size()];

                        for (int i = 0; i < calls.size(); i++) {
                            JSONObject result = resultsJson.get(i).isObject();
                            if (result.containsKey("error")) {
                                callback.onError(i, new JsonRpcException(
                                        (int) result.get("error").isNumber().doubleValue(),
                                        calls.get(i).getMethodName(),
                                        result.get("message").isString().stringValue(),
                                        null));
                                resultData[i] = null;
                            } else {
                                resultData[i] = result.get("data");
                            }
                        }

                        callback.onData(resultData);
                    } else {
                        callback.onError(-1, new JsonRpcException(-1,
                                "Received HTTP status code other than 200: "
                                        + response.getStatusText()));
                    }
                }
            });
        } catch (RequestException e) {
            // Couldn't connect to server
            callback.onError(-1, new JsonRpcException(-1, e.getMessage()));
        }
    }
}
