package ca.pkay.rcloneexplorer.Database.json;


import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;

import ca.pkay.rcloneexplorer.Database.DatabaseHandler;
import ca.pkay.rcloneexplorer.Items.Filter;
import ca.pkay.rcloneexplorer.Items.Task;
import ca.pkay.rcloneexplorer.Items.Trigger;

public class Importer {


    public static void importJson(String json, Context context) throws JSONException {
        DatabaseHandler dbHandler = new DatabaseHandler(context);
        dbHandler.deleteEveryting();
        for(Trigger trigger : createTriggerlist(json)){
            dbHandler.createTrigger(trigger, true);
        }
        for(Filter filter : createFilterList(json)){
            dbHandler.createFilter(filter, true);
        }
        for(Task task : createTasklist(json)){
            dbHandler.createTask(task, true);
        }
    }

    public static ArrayList<Trigger> createTriggerlist(String content) throws JSONException {
        ArrayList<Trigger> result = new ArrayList<>();
        JSONObject reader = new JSONObject(content);
        JSONArray array = reader.getJSONArray("trigger");
        for (int i = 0; i < array.length(); i++) {
            JSONObject triggerObject = array.getJSONObject(i);
            result.add(Trigger.Companion.fromString(triggerObject.toString()));
        }
        return result;
    }

    public static ArrayList<Task> createTasklist(String content) throws JSONException {
        ArrayList<Task> result = new ArrayList<>();
        JSONObject reader = new JSONObject(content);
        JSONArray array = reader.getJSONArray("tasks");
        for (int i = 0; i < array.length(); i++) {
            JSONObject taskObject = array.getJSONObject(i);
            result.add(Task.Companion.fromString(taskObject.toString()));
        }
        return result;
    }
    public static ArrayList<Filter> createFilterList(String content) throws JSONException {
        ArrayList<Filter> result = new ArrayList<>();
        JSONObject reader = new JSONObject(content);
        JSONArray array = reader.getJSONArray("filters");
        for (int i = 0; i < array.length(); i++) {
            JSONObject filterObject = array.getJSONObject(i);
            result.add(Filter.Companion.fromString(filterObject.toString()));
        }
        return result;
    }
}
