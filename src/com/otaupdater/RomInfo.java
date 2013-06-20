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

import java.util.Date;

import android.content.Intent;

public class RomInfo {
    public String romName;
    public String version;
    public String incrementalSourceVersion;
    public String changelog;
    public String url;
    public String incrementalUrl;
    public String md5;
    public String incrementalMd5;
    public Date date;

    public RomInfo(String romName, String version, String changelog, String downurl, String md5, Date date) {
        this.romName = romName;
        this.version = version;
        this.changelog = changelog;
        this.url = downurl;
        this.md5 = md5;
        this.date = date;
    }

    public void setIncrementalInfo(String incrementalSourceVersion, String incrementalUrl, String incrementalMd5) {
        this.incrementalSourceVersion = incrementalSourceVersion;
        this.incrementalUrl = incrementalUrl;
        this.incrementalMd5 = incrementalMd5;
    }

    public static RomInfo fromIntent(Intent i) {
        RomInfo info = new RomInfo(
                i.getStringExtra("info_rom"),
                i.getStringExtra("info_version"),
                i.getStringExtra("info_changelog"),
                i.getStringExtra("info_url"),
                i.getStringExtra("info_md5"),
                Utils.parseDate(i.getStringExtra("info_date")));

        String incrementalSourceVersion = i.getStringExtra("info_incremental_source_version");
        String incrementalUrl = i.getStringExtra("info_incremental_url");
        String incrementalMd5 = i.getStringExtra("info_incremental_md5");

        if (incrementalSourceVersion != null && incrementalUrl != null && incrementalMd5 != null) {
            info.setIncrementalInfo(incrementalSourceVersion, incrementalUrl, incrementalMd5);
        }

        return info;
    }

    public void addToIntent(Intent i) {
        i.putExtra("info_rom", romName);
        i.putExtra("info_version", version);
        i.putExtra("info_changelog", changelog);
        i.putExtra("info_url", url);
        i.putExtra("info_md5", md5);
        i.putExtra("info_date", Utils.formatDate(date));

        i.putExtra("info_incremental_source_version_incremental", incrementalSourceVersion);
        i.putExtra("info_incremental_url", incrementalUrl);
        i.putExtra("info_incremental_md5", incrementalMd5);
    }

    public boolean hasIncrementalUpdate() {
        return incrementalSourceVersion != null && incrementalUrl != null && incrementalMd5 != null;
    }
}
