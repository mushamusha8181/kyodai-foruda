package com.mushamusha.giantfolder;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class ItemModel {
    public static final String TYPE_APP = "app";
    public static final String TYPE_FOLDER = "folder";
    public static final String TYPE_SMART = "smart";
    public static final String ROOT = "ROOT";
    public static final String DOCK = "DOCK";

    public String id = UUID.randomUUID().toString();
    public String type = TYPE_APP;
    public String name = "";
    public String packageName = "";
    public String activityName = "";
    public String parentId = ROOT;
    public String customIconUri = "";
    public String smartKind = "";
    public int page = 0;
    public int order = 0;
    public boolean favorite = false;
    public boolean hidden = false;
    public boolean locked = false;
    public long createdAt = System.currentTimeMillis();

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("type", type);
        o.put("name", name);
        o.put("packageName", packageName);
        o.put("activityName", activityName);
        o.put("parentId", parentId);
        o.put("customIconUri", customIconUri);
        o.put("smartKind", smartKind);
        o.put("page", page);
        o.put("order", order);
        o.put("favorite", favorite);
        o.put("hidden", hidden);
        o.put("locked", locked);
        o.put("createdAt", createdAt);
        return o;
    }

    public static ItemModel fromJson(JSONObject o) {
        ItemModel m = new ItemModel();
        m.id = o.optString("id", UUID.randomUUID().toString());
        m.type = o.optString("type", TYPE_APP);
        m.name = o.optString("name", "");
        m.packageName = o.optString("packageName", "");
        m.activityName = o.optString("activityName", "");
        m.parentId = o.optString("parentId", ROOT);
        m.customIconUri = o.optString("customIconUri", "");
        m.smartKind = o.optString("smartKind", "");
        m.page = o.optInt("page", 0);
        m.order = o.optInt("order", 0);
        m.favorite = o.optBoolean("favorite", false);
        m.hidden = o.optBoolean("hidden", false);
        m.locked = o.optBoolean("locked", false);
        m.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        return m;
    }

    public ItemModel shallowCopy() {
        ItemModel n = new ItemModel();
        n.type = type;
        n.name = name;
        n.packageName = packageName;
        n.activityName = activityName;
        n.parentId = parentId;
        n.customIconUri = customIconUri;
        n.smartKind = smartKind;
        n.page = page;
        n.order = order;
        n.favorite = favorite;
        n.hidden = hidden;
        n.locked = locked;
        return n;
    }
}
