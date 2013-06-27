/*
 * Copyright (C) 2012 OTA Update Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may only use this file in compliance with the license and provided you are not associated with or are in co-operation anyone by the name 'X Vanderpoel'.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.otaupdater;

import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

public class FetchRomInfoTask extends AsyncTask<Void, Void, RomInfo> {
    private RomInfoListener callback = null;
    private Context context = null;
    private String error = null;
    private RomInfo fullUpdate;
    private boolean checkForIncrementalUpdateOnly = false;

    public FetchRomInfoTask(Context ctx) {
        this(ctx, null);
    }

    public FetchRomInfoTask(Context ctx, RomInfoListener callback) {
        this(ctx, callback, null);
    }

    public FetchRomInfoTask(Context ctx, RomInfoListener callback, RomInfo fullUpdate) {
        this.context = ctx;
        this.callback = callback;
        if (fullUpdate != null) {
            this.fullUpdate = fullUpdate;
            this.checkForIncrementalUpdateOnly = true;
        }
    }

    @Override
    public void onPreExecute() {
        if (callback != null) callback.onStartLoading();
    }

    @Override
    protected RomInfo doInBackground(Void... notused) {
        if (!Utils.isROMSupported()) {
            error = context.getString(R.string.alert_unsupported_title);
            return null;
        }
        if (!Utils.dataAvailable(context)) {
            error = context.getString(R.string.alert_nodata_title);
            return null;
        }

        ArrayList<BasicNameValuePair> params = null;
        RomInfo romInfo = null;
        RomInfo incrementalRomInfo = null;

        if (!checkForIncrementalUpdateOnly) {
            params = new ArrayList<BasicNameValuePair>();
            params.add(new BasicNameValuePair("device", android.os.Build.DEVICE.toLowerCase()));
            params.add(new BasicNameValuePair("rom", Utils.getRomID()));
    
            try {
                error = null;
                romInfo = query(params);
                if (romInfo == null) {
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                error = e.getMessage();
            }
        } else {
            if (fullUpdate == null) {
                Log.w("OTA::Fetch", "Full update is null, not checking for incremental update.");
                return null;
            } else {
                romInfo = fullUpdate;
            }
        }

        params = new ArrayList<BasicNameValuePair>();
        params.add(new BasicNameValuePair("device", android.os.Build.DEVICE.toLowerCase()));
        params.add(new BasicNameValuePair("rom", Utils.getVersionSpecificRomID()));

        try {
            error = null;
            incrementalRomInfo = query(params);
            if (incrementalRomInfo != null && romInfo != null) {
                if (incrementalRomInfo.version.equals(romInfo.version)) {
                    romInfo.setIncrementalInfo(
                            Utils.getOtaVersion(),
                            incrementalRomInfo.url,
                            incrementalRomInfo.md5);
                    return romInfo;
                } else {
                    Log.w("OTA::Fetch", "Incremental update version does not match full update, ignoring.");
                    if (checkForIncrementalUpdateOnly)
                        return null;
                    else 
                        return romInfo;
                }
            } else if (checkForIncrementalUpdateOnly) {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            error = e.getMessage();
        }

        return romInfo;
    }

    private RomInfo query(ArrayList<BasicNameValuePair> params) throws java.io.IOException, org.json.JSONException {
        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(Config.PULL_URL + "?" + URLEncodedUtils.format(params, "UTF-8"));
        HttpResponse r = client.execute(get);
        int status = r.getStatusLine().getStatusCode();
        HttpEntity e = r.getEntity();
        if (status == 200) {
            String data = EntityUtils.toString(e);
            JSONObject json = new JSONObject(data);

            if (json.has("error")) {
                Log.e("OTA::Fetch", json.getString("error"));
                error = json.getString("error");
                return null;
            }

            return new RomInfo(
                    json.getString("rom"),
                    json.getString("version"),
                    json.getString("changelog"),
                    json.getString("url"),
                    json.getString("md5"),
                    Utils.parseDate(json.getString("date")));
        } else {
            if (e != null) e.consumeContent();
            error = "Server responded with error " + status;
            return null;
        }
    }

    @Override
    public void onPostExecute(RomInfo result) {
        if (callback != null) {
            if (result != null) callback.onLoaded(result);
            else callback.onError(error);
        }
    }

    public static interface RomInfoListener {
        void onStartLoading();
        void onLoaded(RomInfo info);
        void onError(String err);
    }
}
