/*
 *     MCEF (Minecraft Chromium Embedded Framework)
 *     Copyright (C) 2023 CinemaMod Group
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 */

package ccetl.mcef;

import java.util.HashMap;
import java.util.Map;

public class MCEFSettings {
    private String downloadMirror;
    private String userAgent;
    private Map<MCEFPlatform, String> checkSums;
    private boolean checkDownloads;


    public MCEFSettings() {
        downloadMirror = "https://dl.ccbluex.net/resources";
        userAgent = null;
        checkSums = new HashMap<>();
        checkSums.put(MCEFPlatform.WINDOWS_AMD64, "2fd5fde6738a293c732cf7b97925a2e4ea34e33da3404a0548e6356830691a13");
    }

    public String getDownloadMirror() {
        return downloadMirror;
    }

    public void setDownloadMirror(String downloadMirror) {
        this.downloadMirror = downloadMirror;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Map<MCEFPlatform, String> getCheckSums() {
        return checkSums;
    }

    public void setCheckSums(Map<MCEFPlatform, String> checkSums) {
        this.checkSums = checkSums;
    }

    public boolean isCheckDownloads() {
        return checkDownloads;
    }

    public void setCheckDownloads(boolean checkDownloads) {
        this.checkDownloads = checkDownloads;
    }
}
