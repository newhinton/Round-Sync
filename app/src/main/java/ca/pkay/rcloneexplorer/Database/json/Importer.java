package ca.pkay.rcloneexplorer.Database.json;


import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;

import ca.pkay.rcloneexplorer.Database.DatabaseHandler;
import ca.pkay.rcloneexplorer.Items.Filter;
import ca.pkay.rcloneexplorer.Items.Task;
import ca.pkay.rcloneexplorer.Items.Trigger;

public class Importer {


    public static void importJson(String json, Context context) throws JSONException {
        validateJson(json);
        ArrayList<Trigger> triggers = createTriggerlist(json);
        ArrayList<Filter> filters = createFilterList(json);
        ArrayList<Task> tasks = createTasklist(json);
        String internalDataDir = null;
        try {
            internalDataDir = new java.io.File(context.getApplicationInfo().dataDir).getCanonicalPath();
        } catch (java.io.IOException ignored) {
        }
        ArrayList<Task> safeTasks = new ArrayList<>();
        for (Task task : tasks) {
            if (task.getRemoteId() != null && task.getRemoteId().startsWith("-")) continue;
            if (task.getLocalPath() != null && !task.getLocalPath().isEmpty() && internalDataDir != null) {
                try {
                    String canonicalLocal = new java.io.File(task.getLocalPath()).getCanonicalPath();
                    if (canonicalLocal.equals(internalDataDir) || canonicalLocal.startsWith(internalDataDir + java.io.File.separator)) continue;
                } catch (java.io.IOException e) {
                    continue;
                }
            }
            safeTasks.add(task);
        }
        new DatabaseHandler(context).replaceAll(triggers, filters, safeTasks);
    }

    public static void validateJson(String json) throws JSONException {
        validateRoot(json);
        HashSet<Long> ids = new HashSet<>();
        for (Trigger item : createTriggerlist(json)) {
            if (!ids.add(item.getId())) throw new JSONException("Duplicate trigger ID");
        }
        ids.clear();
        for (Filter item : createFilterList(json)) {
            if (!ids.add(item.getId())) throw new JSONException("Duplicate filter ID");
        }
        ids.clear();
        for (Task item : createTasklist(json)) {
            if (!ids.add(item.getId())) throw new JSONException("Duplicate task ID");
        }
    }

    private static void validateRoot(String json) throws JSONException {
        JSONObject reader = new JSONObject(json);
        if (!reader.has("trigger") && !reader.has("filters") && !reader.has("tasks")) {
            throw new JSONException("No backup sections found");
        }
    }

    public static ArrayList<Trigger> createTriggerlist(String content) throws JSONException {
        ArrayList<Trigger> result = new ArrayList<>();
        JSONObject reader = new JSONObject(content);
        JSONArray array = reader.has("trigger") ? reader.getJSONArray("trigger") : new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            JSONObject triggerObject = array.getJSONObject(i);
            result.add(Trigger.Companion.fromString(triggerObject.toString()));
        }
        return result;
    }

    public static ArrayList<Task> createTasklist(String content) throws JSONException {
        ArrayList<Task> result = new ArrayList<>();
        JSONObject reader = new JSONObject(content);
        JSONArray array = reader.has("tasks") ? reader.getJSONArray("tasks") : new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            JSONObject taskObject = array.getJSONObject(i);
            result.add(Task.Companion.fromString(taskObject.toString()));
        }
        return result;
    }
    public static ArrayList<Filter> createFilterList(String content) throws JSONException {
        ArrayList<Filter> result = new ArrayList<>();
        JSONObject reader = new JSONObject(content);
        JSONArray array = reader.has("filters") ? reader.getJSONArray("filters") : new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            JSONObject filterObject = array.getJSONObject(i);
            result.add(Filter.Companion.fromString(filterObject.toString()));
        }
        return result;
    }
}
