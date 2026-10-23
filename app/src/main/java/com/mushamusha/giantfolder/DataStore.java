package com.mushamusha.giantfolder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class DataStore {
    private static final String PREF = "giant_folder_data";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_PAGES=200;
    private final SharedPreferences prefs;
    private final List<ItemModel> items = new ArrayList<>();
    private String loadedItemsRaw=null;
    private boolean itemsLoaded=false;

    public DataStore(Context c) { this(c,true); }

    /** Lightweight mode is for services/setup screens that only read scalar preferences/history. */
    DataStore(Context c, boolean loadItems) {
        prefs = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if(loadItems) load();
    }

    private void load() {
        items.clear();
        itemsLoaded=true;
        String raw = prefs.getString(KEY_ITEMS, "[]");
        loadedItemsRaw=raw;
        try {
            JSONArray a = new JSONArray(raw);
            boolean skippedInvalid=false;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o=a.optJSONObject(i);
                if(o==null){skippedInvalid=true;continue;}
                items.add(ItemModel.fromJson(o));
            }
            if (skippedInvalid||sanitizeLoadedItems()) save();
        } catch (JSONException ignored) {
            // SharedPreferences writes are atomic; if legacy/corrupt text is nevertheless unreadable,
            // keep the app usable and never expose a partially parsed tree.
            items.clear();
        }
    }

    /** Repairs malformed legacy/imported data once and persists the repair so IDs stay stable across reloads. */
    private boolean sanitizeLoadedItems(){
        boolean changed=false;
        Set<String> ids=new HashSet<>();
        for(ItemModel m:items){
            if(m.id==null||m.id.isEmpty()||!ids.add(m.id)){
                m.id=UUID.randomUUID().toString();
                ids.add(m.id);changed=true;
            }
            if(!ItemModel.TYPE_APP.equals(m.type)&&!ItemModel.TYPE_FOLDER.equals(m.type)&&!ItemModel.TYPE_SMART.equals(m.type)){
                m.type=ItemModel.TYPE_APP;changed=true;
            }
            if(m.parentId==null||m.parentId.isEmpty()||m.id.equals(m.parentId)){
                m.parentId=ItemModel.ROOT;changed=true;
            }
            int page=Math.max(0,Math.min(MAX_PAGES-1,m.page)),order=Math.max(0,m.order);
            if(page!=m.page){m.page=page;changed=true;}
            if(order!=m.order){m.order=order;changed=true;}
        }
        Map<String,ItemModel> byId=new HashMap<>();
        Set<String> folders=new HashSet<>();
        for(ItemModel m:items){byId.put(m.id,m);if(ItemModel.TYPE_FOLDER.equals(m.type))folders.add(m.id);}
        for(ItemModel m:items){
            if(ItemModel.DOCK.equals(m.parentId)&&!ItemModel.TYPE_APP.equals(m.type)){
                m.parentId=ItemModel.ROOT;m.page=0;changed=true;
            }else if(!ItemModel.ROOT.equals(m.parentId)&&!ItemModel.DOCK.equals(m.parentId)&&!folders.contains(m.parentId)){
                m.parentId=ItemModel.ROOT;changed=true;
            }
            if(ItemModel.DOCK.equals(m.parentId)&&m.page!=0){m.page=0;changed=true;}
            Set<String> chain=new HashSet<>();String parent=m.parentId;
            while(!ItemModel.ROOT.equals(parent)&&!ItemModel.DOCK.equals(parent)){
                if(!chain.add(parent)||m.id.equals(parent)){
                    m.parentId=ItemModel.ROOT;changed=true;break;
                }
                ItemModel parentItem=byId.get(parent);
                if(parentItem==null){
                    m.parentId=ItemModel.ROOT;changed=true;break;
                }
                parent=parentItem.parentId;
            }
        }
        return changed;
    }

    /** Reloads the item tree only when another DataStore instance actually changed its serialized data. */
    public void reloadIfChanged() {
        String current=prefs.getString(KEY_ITEMS,"[]");
        if(loadedItemsRaw==null||!loadedItemsRaw.equals(current)) load();
    }

    public void save() {
        if(!itemsLoaded){ load(); return; }
        JSONArray a = new JSONArray();
        for (ItemModel m : items) {
            try { a.put(m.toJson()); } catch (JSONException ignored) {}
        }
        String raw=a.toString();
        loadedItemsRaw=raw;
        prefs.edit().putString(KEY_ITEMS, raw).apply();
    }

    public ItemModel find(String id) {
        for (ItemModel m : items) if (m.id.equals(id)) return m;
        return null;
    }

    public List<ItemModel> children(String parentId, int page) {
        ArrayList<ItemModel> out = new ArrayList<>();
        for (ItemModel m : items) {
            if (!m.hidden && m.parentId.equals(parentId) && m.page == page) out.add(m);
        }
        Collections.sort(out, (a,b) -> {
            if (a.favorite != b.favorite) return a.favorite ? -1 : 1;
            return Integer.compare(a.order, b.order);
        });
        return out;
    }

    public List<ItemModel> childrenAllPages(String parentId) {
        ArrayList<ItemModel> out = new ArrayList<>();
        for (ItemModel m : items) if (!m.hidden && m.parentId.equals(parentId)) out.add(m);
        Collections.sort(out, (a,b) -> {
            if (a.page != b.page) return Integer.compare(a.page,b.page);
            if (a.favorite != b.favorite) return a.favorite ? -1 : 1;
            return Integer.compare(a.order,b.order);
        });
        return out;
    }

    public List<ItemModel> hiddenItems() {
        ArrayList<ItemModel> out = new ArrayList<>();
        for (ItemModel m : items) if (m.hidden) out.add(m);
        Collections.sort(out, Comparator.comparing(x -> x.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    public List<ItemModel> searchVisible(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        ArrayList<ItemModel> out = new ArrayList<>();
        Map<String,ItemModel> byId=new HashMap<>();
        for(ItemModel m:items) byId.put(m.id,m);
        for (ItemModel m : items) {
            if (m.hidden || ItemModel.DOCK.equals(m.parentId) || isInsideHiddenOrLockedFolder(m,byId)) continue;
            if (q.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(q) || m.packageName.toLowerCase(Locale.ROOT).contains(q)) out.add(m);
        }
        Collections.sort(out, (a,b) -> {
            if (a.favorite != b.favorite) return a.favorite ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });
        return out;
    }

    private boolean isInsideHiddenOrLockedFolder(ItemModel item,Map<String,ItemModel> byId){
        String parent=item.parentId;
        Set<String> seen=new HashSet<>();
        while(parent!=null&&!ItemModel.ROOT.equals(parent)&&!ItemModel.DOCK.equals(parent)&&seen.add(parent)){
            ItemModel p=byId.get(parent);
            if(p==null)return false;
            if(p.hidden||(ItemModel.TYPE_FOLDER.equals(p.type)&&p.locked))return true;
            parent=p.parentId;
        }
        return false;
    }

    /** True when this item/folder is protected by a locked folder at any level. */
    public boolean isLockedPath(String id){
        if(id==null||id.isEmpty()||ItemModel.ROOT.equals(id)||ItemModel.DOCK.equals(id))return false;
        Map<String,ItemModel> byId=new HashMap<>();
        for(ItemModel m:items)byId.put(m.id,m);
        String current=id;Set<String> seen=new HashSet<>();
        while(current!=null&&!ItemModel.ROOT.equals(current)&&!ItemModel.DOCK.equals(current)&&seen.add(current)){
            ItemModel m=byId.get(current);
            if(m==null)return false;
            if(ItemModel.TYPE_FOLDER.equals(m.type)&&m.locked)return true;
            current=m.parentId;
        }
        return false;
    }

    public List<ItemModel> dockItems() {
        ArrayList<ItemModel> out = new ArrayList<>();
        for (ItemModel m : items) if (!m.hidden && ItemModel.DOCK.equals(m.parentId)) out.add(m);
        Collections.sort(out, Comparator.comparingInt(x -> x.order));
        return out;
    }

    public int getPageCount(String parentId) {
        int saved = prefs.getInt("pages_" + parentId, 1);
        int max = 0;
        for (ItemModel m : items) if (!m.hidden && m.parentId.equals(parentId)) max = Math.max(max, m.page);
        return Math.max(1,Math.min(MAX_PAGES,Math.max(saved, max + 1)));
    }

    public void setPageCount(String parentId, int count) {
        prefs.edit().putInt("pages_" + parentId, Math.max(1,Math.min(MAX_PAGES,count))).apply();
    }

    public int nextOrder(String parentId, int page) {
        int max = -1;
        for (ItemModel m : items) if (m.parentId.equals(parentId) && m.page == page) max = Math.max(max, m.order);
        return max + 1;
    }

    public void add(ItemModel m) {
        items.add(m);
        if (!ItemModel.DOCK.equals(m.parentId)) setPageCount(m.parentId, Math.max(getPageCount(m.parentId), m.page + 1));
        save();
    }

    public void addAll(List<ItemModel> added) {
        if(added==null||added.isEmpty())return;
        Set<String> parents=new HashSet<>();
        for(ItemModel m:added){
            if(m==null)continue;
            items.add(m);
            if(!ItemModel.DOCK.equals(m.parentId))parents.add(m.parentId);
        }
        SharedPreferences.Editor ed=prefs.edit();
        for(String parent:parents)ed.putInt("pages_"+parent,getPageCount(parent));
        ed.apply();
        save();
    }

    public ItemModel copyTo(ItemModel original, String parentId, int page) {
        ItemModel n = original.shallowCopy();
        n.parentId = parentId;
        n.page = page;
        n.order = nextOrder(parentId, page);
        n.hidden = false;
        n.createdAt = System.currentTimeMillis();
        items.add(n);
        save();
        return n;
    }

    public void duplicatePage(String parentId, int sourcePage) {
        int targetPage = getPageCount(parentId);
        setPageCount(parentId, targetPage + 1);
        List<ItemModel> originals = new ArrayList<>();
        for (ItemModel m : items) if (!m.hidden && m.parentId.equals(parentId) && m.page == sourcePage) originals.add(m);
        originals.sort(Comparator.comparingInt(x -> x.order));
        Set<String> visited=new HashSet<>();
        for (ItemModel m : originals) cloneTree(m, parentId, targetPage,visited);
        save();
    }

    private ItemModel cloneTree(ItemModel src, String newParent, int newPage,Set<String> visited) {
        if(src==null||!visited.add(src.id))return null;
        ItemModel copy = src.shallowCopy();
        copy.parentId = newParent;
        copy.page = newPage;
        copy.order = nextOrder(newParent, newPage);
        copy.createdAt = System.currentTimeMillis();
        items.add(copy);
        if (ItemModel.TYPE_FOLDER.equals(src.type)) {
            List<ItemModel> kids = new ArrayList<>();
            for (ItemModel m : items) if (!m.id.equals(copy.id) && m.parentId.equals(src.id)) kids.add(m);
            kids.sort((a,b) -> a.page == b.page ? Integer.compare(a.order,b.order) : Integer.compare(a.page,b.page));
            int childPages = getPageCount(src.id);
            setPageCount(copy.id, childPages);
            for (ItemModel kid : kids) cloneTree(kid, copy.id, kid.page,visited);
        }
        return copy;
    }

    public void deleteRecursive(String id) {
        if(id==null||id.isEmpty())return;
        ArrayList<String> one=new ArrayList<>();one.add(id);deleteRecursiveAll(one);
    }

    /** Removes multiple roots and their descendants in one scan/save instead of repeating full-tree work. */
    public void deleteRecursiveAll(Iterable<String> rootIds) {
        Set<String> doomed = new HashSet<>();
        if(rootIds!=null)for(String id:rootIds)if(id!=null&&!id.isEmpty())doomed.add(id);
        if(doomed.isEmpty())return;
        boolean changed;
        do {
            changed = false;
            for (ItemModel m : items) {
                if (doomed.contains(m.parentId) && doomed.add(m.id)) changed = true;
            }
        } while (changed);
        items.removeIf(m -> doomed.contains(m.id));

        SharedPreferences.Editor meta = prefs.edit();
        Set<String> keys = new HashSet<>(prefs.getAll().keySet());
        for (String dead : doomed) {
            meta.remove("pages_" + dead);
            String pageColorPrefix = "page_color_" + dead + "_";
            for (String key : keys) if (key.startsWith(pageColorPrefix)) meta.remove(key);
        }
        meta.apply();
        save();
    }

    public void normalize(String parentId) {
        int pages = getPageCount(parentId);
        for (int p = 0; p < pages; p++) {
            List<ItemModel> list = new ArrayList<>();
            for (ItemModel m : items) if (m.parentId.equals(parentId) && m.page == p) list.add(m);
            list.sort(Comparator.comparingInt(x -> x.order));
            for (int i = 0; i < list.size(); i++) list.get(i).order = i;
        }
    }

    public int keyColor() { return prefs.getInt("key_color", 0xFF7C6FF2); }
    public void setKeyColor(int color) { prefs.edit().putInt("key_color", color).apply(); }
    public int panelAlpha() { return Math.max(40,Math.min(95,prefs.getInt("panel_alpha", 70))); }
    public void setPanelAlpha(int v) { prefs.edit().putInt("panel_alpha", Math.max(40,Math.min(95,v))).apply(); }
    public int cornerRadius() { return Math.max(10,Math.min(44,prefs.getInt("corner_radius", 26))); }
    public void setCornerRadius(int v) { prefs.edit().putInt("corner_radius", Math.max(10,Math.min(44,v))).apply(); }

    public int pageColor(String parentId, int page) { return prefs.getInt("page_color_"+parentId+"_"+page, keyColor()); }
    public void setPageColor(String parentId, int page, int color) { prefs.edit().putInt("page_color_"+parentId+"_"+page, color).apply(); }

    public int columnsPortrait() { return Math.max(4,Math.min(7,prefs.getInt("columns_portrait", 5))); }
    public void setColumnsPortrait(int v) { prefs.edit().putInt("columns_portrait", Math.max(4,Math.min(7,v))).apply(); }
    public int columnsLandscape() { return Math.max(4,Math.min(9,prefs.getInt("columns_landscape", 7))); }
    public void setColumnsLandscape(int v) { prefs.edit().putInt("columns_landscape", Math.max(4,Math.min(9,v))).apply(); }
    public int iconSize() { return Math.max(36,Math.min(68,prefs.getInt("icon_size", 48))); }
    public void setIconSize(int v) { prefs.edit().putInt("icon_size", Math.max(36,Math.min(68,v))).apply(); }

    public boolean layoutLocked() { return prefs.getBoolean("layout_locked", true); }
    public void setLayoutLocked(boolean v) { prefs.edit().putBoolean("layout_locked", v).apply(); }
    public boolean appLock() { return prefs.getBoolean("app_lock", false); }
    public void setAppLock(boolean v) { prefs.edit().putBoolean("app_lock", v).apply(); }

    public String backgroundUri() { return prefs.getString("background_uri", ""); }
    public void setBackgroundUri(String uri) { prefs.edit().putString("background_uri", uri == null ? "" : uri).apply(); }

    public String overlaySide() { return "left".equals(prefs.getString("overlay_side", "right"))?"left":"right"; }
    public void setOverlaySide(String side) { prefs.edit().putString("overlay_side", "left".equals(side)?"left":"right").apply(); }
    public int overlayAlpha() { return Math.max(35,Math.min(100,prefs.getInt("overlay_alpha", 88))); }
    public void setOverlayAlpha(int v) { prefs.edit().putInt("overlay_alpha", Math.max(35,Math.min(100,v))).apply(); }
    public int overlayWidth() { return Math.max(26,Math.min(60,prefs.getInt("overlay_width", 36))); }
    public void setOverlayWidth(int v) { prefs.edit().putInt("overlay_width", Math.max(26,Math.min(60,v))).apply(); }
    public int overlayHeight() { return Math.max(64,Math.min(150,prefs.getInt("overlay_height", 96))); }
    public void setOverlayHeight(int v) { prefs.edit().putInt("overlay_height", Math.max(64,Math.min(150,v))).apply(); }
    public int overlayY() { return prefs.getInt("overlay_y", 220); }
    public void setOverlayY(int v) { prefs.edit().putInt("overlay_y", Math.max(0,v)).apply(); }

    /** Default is a simple dot; numeric mode means active notification count, not unread count. */
    public boolean badgeCountEnabled() { return prefs.getBoolean("badge_count_enabled", false); }
    public void setBadgeCountEnabled(boolean v) { prefs.edit().putBoolean("badge_count_enabled", v).apply(); }

    public boolean returnReminderEnabled() { return prefs.getBoolean("return_reminder_enabled", true); }
    public static boolean returnReminderEnabled(Context c) {
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean("return_reminder_enabled",true);
    }
    public void setReturnReminderEnabled(boolean v) { prefs.edit().putBoolean("return_reminder_enabled", v).apply(); }
    public int returnReminderSnoozeMinutes() { return normalizeSnoozeMinutes(prefs.getInt("return_reminder_snooze_minutes",30)); }
    public static int returnReminderSnoozeMinutes(Context c) {
        return normalizeSnoozeMinutes(c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getInt("return_reminder_snooze_minutes",30));
    }
    private static int normalizeSnoozeMinutes(int v){ return v==15||v==60?v:30; }
    public void setReturnReminderSnoozeMinutes(int v) {
        if(v!=15&&v!=30&&v!=60)v=30;
        prefs.edit().putInt("return_reminder_snooze_minutes",v).apply();
    }

    public void recordLaunch(ItemModel m) {
        if (m == null || m.packageName.isEmpty()) return;
        String key = "launch_count_" + m.packageName;
        SharedPreferences.Editor ed=prefs.edit()
                .putInt(key,prefs.getInt(key,0)+1)
                .putLong("last_launch_"+m.packageName,System.currentTimeMillis());
        try {
            JSONArray old = new JSONArray(prefs.getString("recent_launches","[]"));
            JSONArray out = new JSONArray();
            JSONObject now = new JSONObject();
            now.put("package",m.packageName); now.put("activity",m.activityName); now.put("name",m.name);
            out.put(now);
            for (int i=0;i<old.length() && out.length()<10;i++) {
                JSONObject o=old.optJSONObject(i);
                if (o!=null&&!m.packageName.equals(o.optString("package"))) out.put(o);
            }
            ed.putString("recent_launches",out.toString());
        } catch(Exception ignored) {
            try{
                JSONArray out=new JSONArray();JSONObject now=new JSONObject();
                now.put("package",m.packageName);now.put("activity",m.activityName);now.put("name",m.name);out.put(now);
                ed.putString("recent_launches",out.toString());
            }catch(Exception ignoredAgain){}
        }
        ed.apply();
    }

    public JSONArray recentLaunches() {
        try { return new JSONArray(prefs.getString("recent_launches","[]")); }
        catch(Exception e) { prefs.edit().remove("recent_launches").apply(); return new JSONArray(); }
    }

    public int ownLaunchCount(String pkg) { return prefs.getInt("launch_count_"+pkg,0); }
    public long ownLastLaunch(String pkg) { return prefs.getLong("last_launch_"+pkg,0L); }

    public boolean permissionSetupDone() { return prefs.getBoolean("permission_setup_done", false); }
    public void setPermissionSetupDone(boolean v) { prefs.edit().putBoolean("permission_setup_done", v).apply(); }

    public String exportJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", "GiantFolderBackup");
        root.put("version", 2);
        root.put("items", new JSONArray(prefs.getString(KEY_ITEMS,"[]")));
        JSONObject p = new JSONObject();
        for (Map.Entry<String,?> e : prefs.getAll().entrySet()) {
            String key=e.getKey();
            if(!isBackupPreferenceKey(key))continue;
            Object v=e.getValue();
            if (v instanceof String || v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof Float) p.put(key,v);
        }
        root.put("prefs",p);
        return root.toString(2);
    }

    public void importJson(String raw) throws JSONException {
        JSONObject root=new JSONObject(raw);
        if (!"GiantFolderBackup".equals(root.optString("format"))) throw new JSONException("Invalid backup");
        JSONArray arr=root.optJSONArray("items");
        if(arr==null)throw new JSONException("Missing items");
        for(int i=0;i<arr.length();i++)if(arr.optJSONObject(i)==null)throw new JSONException("Invalid item entry");
        JSONObject p=root.optJSONObject("prefs");
        SharedPreferences.Editor ed=prefs.edit().clear();
        ed.putString(KEY_ITEMS, arr.toString());
        if (p!=null) {
            JSONArray names=p.names();
            if(names!=null) for(int i=0;i<names.length();i++) {
                String k=names.getString(i);
                if(!isBackupPreferenceKey(k))continue;
                Object v=p.get(k);
                if(!putBackupPreference(ed,k,v))throw new JSONException("Invalid preference type: "+k);
            }
        }
        ed.putBoolean("permission_setup_done", false);
        ed.apply(); load();
    }

    private static boolean putBackupPreference(SharedPreferences.Editor ed,String key,Object value){
        if(key==null||ed==null)return false;
        if(key.startsWith("pages_")){
            if(!(value instanceof Number))return false;
            ed.putInt(key,Math.max(1,Math.min(MAX_PAGES,((Number)value).intValue())));return true;
        }
        if(key.startsWith("page_color_")){
            if(!(value instanceof Number))return false;
            ed.putInt(key,((Number)value).intValue());return true;
        }
        switch(key){
            case "key_color":
                if(!(value instanceof Number))return false;ed.putInt(key,((Number)value).intValue());return true;
            case "panel_alpha":
                if(!(value instanceof Number))return false;ed.putInt(key,Math.max(40,Math.min(95,((Number)value).intValue())));return true;
            case "corner_radius":
                if(!(value instanceof Number))return false;ed.putInt(key,Math.max(10,Math.min(44,((Number)value).intValue())));return true;
            case "columns_portrait":
                if(!(value instanceof Number))return false;ed.putInt(key,Math.max(4,Math.min(7,((Number)value).intValue())));return true;
            case "columns_landscape":
                if(!(value instanceof Number))return false;ed.putInt(key,Math.max(4,Math.min(9,((Number)value).intValue())));return true;
            case "icon_size":
                if(!(value instanceof Number))return false;ed.putInt(key,Math.max(36,Math.min(68,((Number)value).intValue())));return true;
            case "overlay_alpha":
                if(!(value instanceof Number))return false;ed.putInt(key,Math.max(35,Math.min(100,((Number)value).intValue())));return true;
            case "overlay_width":
                if(!(value instanceof Number))return false;ed.putInt(key,Math.max(26,Math.min(60,((Number)value).intValue())));return true;
            case "overlay_height":
                if(!(value instanceof Number))return false;ed.putInt(key,Math.max(64,Math.min(150,((Number)value).intValue())));return true;
            case "overlay_y":
                if(!(value instanceof Number))return false;ed.putInt(key,Math.max(0,((Number)value).intValue()));return true;
            case "return_reminder_snooze_minutes":
                if(!(value instanceof Number))return false;ed.putInt(key,normalizeSnoozeMinutes(((Number)value).intValue()));return true;
            case "layout_locked": case "app_lock": case "badge_count_enabled": case "return_reminder_enabled":
                if(!(value instanceof Boolean))return false;ed.putBoolean(key,(Boolean)value);return true;
            case "background_uri":
                if(!(value instanceof String))return false;ed.putString(key,(String)value);return true;
            case "overlay_side":
                if(!(value instanceof String))return false;ed.putString(key,"left".equals(value)?"left":"right");return true;
            default:return false;
        }
    }
    private static boolean isBackupPreferenceKey(String key){
        if(key==null||key.isEmpty())return false;
        if(key.startsWith("pages_")||key.startsWith("page_color_"))return true;
        switch(key){
            case "key_color": case "panel_alpha": case "corner_radius":
            case "columns_portrait": case "columns_landscape": case "icon_size":
            case "layout_locked": case "app_lock": case "background_uri":
            case "overlay_side": case "overlay_alpha": case "overlay_width": case "overlay_height": case "overlay_y":
            case "badge_count_enabled": case "return_reminder_enabled": case "return_reminder_snooze_minutes":
                return true;
            default:return false;
        }
    }

}
