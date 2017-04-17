package jetbrains.buildServer.tinywebhook;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.vcs.SVcsModification;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsFileModification;
import jetbrains.buildServer.vcs.VcsRootInstanceEntry;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class TinyWebhook extends BuildServerAdapter implements MainConfigProcessor {
    private static final Logger LOG = Logger.getInstance(TinyWebhook.class.getName());

    private SBuildServer buildServer;
    private List<String> urls = new ArrayList<String>();

    public TinyWebhook(SBuildServer server) {
        server.addListener(this);
        buildServer = server;
    }

    private JSONObject buildPayload(SBuild build, boolean includePrevious) {
        JSONObject obj = new JSONObject();

        if (includePrevious)
            obj.put("previousFinished", buildPayload(build.getPreviousFinished(), false));

        obj.put("agentName", build.getAgentName());
        obj.put("buildComment", build.getBuildComment());
        obj.put("buildDescription", build.getBuildDescription());
        obj.put("buildId", build.getBuildId());
        obj.put("buildNumber", build.getBuildNumber());
        obj.put("buildStatus", build.getBuildStatus().toString());
        obj.put("buildIsSuccessful", build.getBuildStatus().isSuccessful());
        obj.put("buildIsFailed", build.getBuildStatus().isFailed());
        obj.put("rawBuildNumber", build.getRawBuildNumber());
        obj.put("startDate", build.getStartDate());
        obj.put("finishDate", build.getFinishDate());
        obj.put("duration", build.getDuration());
        obj.put("branch", build.getBranch());
        obj.put("projectId", build.getProjectId());
        obj.put("projectExternalId", build.getProjectExternalId());
        obj.put("fullName", build.getFullName());
        obj.put("status", build.getBuildType().getStatusDescriptor().getStatusDescriptor().getText().toLowerCase());
        obj.put("rootUrl", buildServer.getRootUrl());

        JSONArray containingChanges = new JSONArray();
        for (SVcsModification vcsMod : build.getContainingChanges()) {
            JSONObject mod = new JSONObject();
            mod.put("userName", vcsMod.getUserName());
            mod.put("changeCount", vcsMod.getChangeCount());
            mod.put("description", vcsMod.getDescription());
            mod.put("id", vcsMod.getId());
            mod.put("version", vcsMod.getVersion());
            mod.put("versionControlName", vcsMod.getVersionControlName());
            mod.put("displayVersion", vcsMod.getDisplayVersion());

            JSONArray changes = new JSONArray();
            for (VcsFileModification fm : vcsMod.getChanges()) {
                JSONObject fileMod = new JSONObject();
                fileMod.put("filename", fm.getFileName());
                fileMod.put("changeTypeName", fm.getChangeTypeName());
                changes.add(fileMod);
            }
            mod.put("changes", changes);
            containingChanges.add(mod);
        }
        obj.put("containingChanges", containingChanges);

        JSONArray vcsRoots = new JSONArray();
        for (VcsRootInstanceEntry vcsEntry : build.getVcsRootEntries()) {
            JSONObject vcs = new JSONObject();
            vcs.put("id", vcsEntry.getVcsRoot().getId());
            vcs.put("name", vcsEntry.getVcsRoot().getName());
            vcs.put("vcsName", vcsEntry.getVcsRoot().getVcsName());
            vcs.put("branch", vcsEntry.getVcsRoot().getProperty("branch"));

            try {
                vcs.put("currentRevision", vcsEntry.getVcsRoot().getCurrentRevision().getVersion());
            } catch (VcsException e) {
                LOG.error("Unable to get VCS revision for " + build.getFullName(), e);
            }

            vcsRoots.add(vcs);
        }
        obj.put("vcsRoots", vcsRoots);

        return obj;
    }

    @Override
    public void buildFinished(@NotNull SRunningBuild build) {
        for (String url : urls) {
            try {
                httpPostAsJson(url, buildPayload(build, true).toJSONString().getBytes());
            } catch (IOException ioe) {
                LOG.error("Unable to send webhook to " + url, ioe);
            }
        }
    }

    // Read config from main-config.xml
    @Override
    public void readFrom(Element rootElement) {
        Element r = rootElement.getChild("tiny-webhook");
        for (Object o : r.getChildren("target")) {
            Element e = (Element)o;
            Attribute a = e.getAttribute("url");
            if (a != null && a.getValue() != null) {
                this.urls.add(a.getValue());
            }
        }
    }

    @Override
    public void writeTo(Element parentElement) {}

    public static void httpPostAsJson(String url, byte[] jsonData) throws IOException {
        URL urlObj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) urlObj.openConnection();

        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Content-Length", Integer.toString(jsonData.length));
        con.setDoOutput(true);
        con.setInstanceFollowRedirects(false);
        con.getOutputStream().write(jsonData);

        int status = con.getResponseCode(); // Send the request
        if (status < 200 || status > 299) {
            throw new IOException("Bad status code when sending to " + url + ". Code: " + status);
        }
    }
}
