package com.mushamusha.giantfolder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import jp.mushamusha.mushinglass.MushinGlass;

public class MainActivity extends Activity {
    private static final int REQ_PICK=1201, REQ_ICON=1203;
    private DataStore store;
    private String currentParent=ItemModel.ROOT;
    private final ArrayList<String> folderStack=new ArrayList<>();
    private int currentPage=0;
    private GridLayout grid;
    private ScrollView gridScroll;
    private FrameLayout panel, screen;
    private LinearLayout dock, batchBar;
    private ImageView wallpaper;
    private TextView title,pageText,editButton;
    private EditText searchBox;
    private Typeface tf;
    private boolean editMode=false;
    private final Set<String> selectedIds=new HashSet<>();
    private String pendingIconId=null;
    private boolean authenticatedThisSession=false,authInProgress=false;
    private boolean badgeAccess=false,badgeReceiverRegistered=false;
    private final Map<String,Boolean> newBadgeCache=new HashMap<>();
    private final Map<String,Drawable.ConstantState> appIconCache=new HashMap<>();
    private String backgroundCacheUri="";
    private Drawable backgroundCacheDrawable=null;
    private long edgeSince=0; private int edgeDir=0;
    private GestureDetector gestures;
    private final BroadcastReceiver badgeReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(NotificationBadgeStore.ACTION_BADGES_UPDATED.equals(intent==null?null:intent.getAction())){
                badgeAccess=NotificationBadgeStore.hasAccess(MainActivity.this);
                refreshGrid();
            }
        }
    };

    static class DragPayload { ArrayList<String> ids=new ArrayList<>(); DragPayload(List<String> v){ids.addAll(v);} }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        store=new DataStore(this);
        tf=Ui.appTypeface(this);
        if(b!=null){
            currentParent=b.getString("currentParent",ItemModel.ROOT);
            currentPage=b.getInt("currentPage",0);
            ArrayList<String> stack=b.getStringArrayList("folderStack");
            if(stack!=null){folderStack.clear();folderStack.addAll(stack);}
            editMode=b.getBoolean("editMode",false);
            pendingIconId=b.getString("pendingIconId");
            ArrayList<String> sel=b.getStringArrayList("selectedIds");
            if(sel!=null)selectedIds.addAll(sel);
        }
        if(!ItemModel.ROOT.equals(currentParent)){
            ItemModel restoredParent=store.find(currentParent);
            // Do not restore directly inside a locked folder (including a locked ancestor) after recreation/process death.
            if(restoredParent==null||store.isLockedPath(currentParent)){currentParent=ItemModel.ROOT;currentPage=0;folderStack.clear();}
        }
        selectedIds.removeIf(id->{ItemModel m=store.find(id);return m==null||store.isLockedPath(id);});
        buildUi();
        if(b!=null){String q=b.getString("search","");if(!q.isEmpty())searchBox.setText(q);}
        if(store.appLock()&&!authenticatedThisSession&&screen!=null)screen.setVisibility(View.INVISIBLE);
        if(!store.permissionSetupDone()) startActivity(new Intent(this,PermissionSetupActivity.class));
    }

    @Override protected void onStart(){
        super.onStart();
        if(!badgeReceiverRegistered){
            IntentFilter f=new IntentFilter(NotificationBadgeStore.ACTION_BADGES_UPDATED);
            if(Build.VERSION.SDK_INT>=33)registerReceiver(badgeReceiver,f,Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(badgeReceiver,f);
            badgeReceiverRegistered=true;
        }
    }

    @Override protected void onStop(){
        if(badgeReceiverRegistered){try{unregisterReceiver(badgeReceiver);}catch(Exception ignored){}badgeReceiverRegistered=false;}
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);
        out.putString("currentParent",currentParent);
        out.putInt("currentPage",currentPage);
        out.putStringArrayList("folderStack",new ArrayList<>(folderStack));
        out.putBoolean("editMode",editMode);
        out.putString("pendingIconId",pendingIconId);
        out.putStringArrayList("selectedIds",new ArrayList<>(selectedIds));
        if(searchBox!=null)out.putString("search",searchBox.getText().toString());
    }

    @Override protected void onResume(){
        super.onResume();
        ReturnReminderManager.endSession(this);
        store.reloadIfChanged();
        // Keep icon/NEW caches across app returns; their keys remain stable and avoid repeated PackageManager work.
        refreshBackground();
        if(store.appLock()&&!SecurityHelper.canAuthenticate(this)){
            store.setAppLock(false);
            Toast.makeText(this,"端末で本人確認を利用できないため、アプリロックを解除しました",Toast.LENGTH_LONG).show();
        }
        if(store.appLock()&&!authenticatedThisSession){
            if(screen!=null)screen.setVisibility(View.INVISIBLE);
            requestAppAuthentication();
            return;
        }
        if(screen!=null)screen.setVisibility(View.VISIBLE);
        resumeUnlocked();
    }

    private void requestAppAuthentication(){
        if(authInProgress)return;
        authInProgress=true;
        SecurityHelper.authenticate(this,"巨大フォルダのロック",()->{
            authInProgress=false;
            authenticatedThisSession=true;
            if(screen!=null)screen.setVisibility(View.VISIBLE);
            resumeUnlocked();
        },()->{authInProgress=false;if(!isFinishing())finish();});
    }

    private void resumeUnlocked(){
        // Main is visible again, so no return overlay/session should remain over this activity.
        hideOverlay();
        refreshAll();
    }

    private void buildUi(){
        final boolean compact=compactUi();
        final int topH=compact?40:44, searchH=compact?40:44, navH=compact?40:44, dockH=compact?54:62;
        screen=new FrameLayout(this);screen.setBackgroundColor(0xFF18181B);
        wallpaper=new ImageView(this);wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);screen.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,compact?6:8),Ui.dp(this,compact?4:6),Ui.dp(this,compact?6:8),Ui.dp(this,compact?4:6));
        screen.addView(root,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=smallButton("‹");back.setOnClickListener(v->goBackFolder());top.addView(back,new LinearLayout.LayoutParams(Ui.dp(this,topH),Ui.dp(this,topH)));
        title=text("巨大フォルダ",compact?19:21,Color.WHITE);title.setSingleLine(true);title.setGravity(Gravity.CENTER_VERTICAL);top.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,topH),1));
        Button settings=smallButton("⚙");settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));top.addView(settings,new LinearLayout.LayoutParams(Ui.dp(this,topH),Ui.dp(this,topH)));
        root.addView(top);

        LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchBox=new EditText(this);searchBox.setSingleLine(true);searchBox.setHint("アプリを検索");searchBox.setTextColor(Color.WHITE);searchBox.setHintTextColor(0xFFBDBDC5);searchBox.setBackground(Ui.rounded(0xAA2B2B31,18,this));searchBox.setPadding(Ui.dp(this,compact?10:12),0,Ui.dp(this,compact?10:12),0);searchBox.setTextSize(compact?13:14);
        searchRow.addView(searchBox,new LinearLayout.LayoutParams(0,Ui.dp(this,searchH),1));
        Button add=smallButton("＋");LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(Ui.dp(this,compact?42:46),Ui.dp(this,searchH));ap.setMargins(Ui.dp(this,compact?4:5),0,0,0);searchRow.addView(add,ap);add.setOnClickListener(v->showAddMenu(add));
        editButton=text("編集",compact?12:13,Color.WHITE);editButton.setGravity(Gravity.CENTER);editButton.setBackground(Ui.rounded(0x992B2B31,16,this));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(Ui.dp(this,compact?46:50),Ui.dp(this,searchH));ep.setMargins(Ui.dp(this,compact?4:5),0,0,0);searchRow.addView(editButton,ep);editButton.setOnClickListener(v->toggleEditMode());
        root.addView(searchRow);

        batchBar=new LinearLayout(this);batchBar.setGravity(Gravity.CENTER);batchBar.setVisibility(View.GONE);batchBar.setPadding(0,Ui.dp(this,compact?2:4),0,0);
        addBatchButton("ページ移動",v->moveSelectedPage()); addBatchButton("フォルダへ",v->moveSelectedFolder()); addBatchButton("非表示",v->hideSelected()); addBatchButton("削除",v->deleteSelected());
        root.addView(batchBar,new LinearLayout.LayoutParams(-1,Ui.dp(this,compact?44:48)));

        panel=new FrameLayout(this);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,0,1);pp.setMargins(0,Ui.dp(this,compact?3:5),0,Ui.dp(this,compact?3:5));root.addView(panel,pp);
        gridScroll=new ScrollView(this);gridScroll.setFillViewport(true);gridScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        panel.addView(gridScroll,new FrameLayout.LayoutParams(-1,-1));
        grid=new GridLayout(this);grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);grid.setUseDefaultMargins(false);grid.setPadding(Ui.dp(this,compact?3:5),Ui.dp(this,compact?3:6),Ui.dp(this,compact?3:5),Ui.dp(this,compact?3:6));
        gridScroll.addView(grid,new ScrollView.LayoutParams(-1,-2));grid.setOnDragListener(this::onGridDrag);

        gestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onFling(MotionEvent e1,MotionEvent e2,float vx,float vy){
                if(e1==null||e2==null||!insidePanel(e1)||!insidePanel(e2))return false;
                float dx=e2.getX()-e1.getX(),dy=e2.getY()-e1.getY();
                if(Math.abs(dx)>Math.abs(dy)&&Math.abs(dx)>Ui.dp(MainActivity.this,80)){
                    if(dx<0)nextPage();else prevPage();
                    return true;
                }
                return false;
            }
        });
        dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER);dock.setPadding(Ui.dp(this,compact?3:4),Ui.dp(this,compact?2:3),Ui.dp(this,compact?3:4),Ui.dp(this,compact?2:3));root.addView(dock,new LinearLayout.LayoutParams(-1,Ui.dp(this,dockH)));

        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);
        Button prev=smallButton("‹");prev.setOnClickListener(v->prevPage());
        pageText=text("1 / 1",14,Color.WHITE);pageText.setGravity(Gravity.CENTER);
        Button pageMenu=smallButton("⋯");pageMenu.setOnClickListener(v->showPageMenu(pageMenu));
        Button next=smallButton("›");next.setOnClickListener(v->nextPage());
        nav.addView(prev,new LinearLayout.LayoutParams(Ui.dp(this,navH),Ui.dp(this,navH)));nav.addView(pageText,new LinearLayout.LayoutParams(0,Ui.dp(this,navH),1));nav.addView(pageMenu,new LinearLayout.LayoutParams(Ui.dp(this,navH),Ui.dp(this,navH)));nav.addView(next,new LinearLayout.LayoutParams(Ui.dp(this,navH),Ui.dp(this,navH)));root.addView(nav);

        searchBox.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){if(!s.toString().trim().isEmpty()&&editMode){editMode=false;selectedIds.clear();batchBar.setVisibility(View.GONE);editButton.setText("編集");}refreshGrid();}public void afterTextChanged(android.text.Editable e){}});
        Ui.applySafeInsets(root);setContentView(screen);Ui.applyFont(screen,tf);refreshBackground();refreshAll();
    }

    private boolean compactUi(){Configuration c=getResources().getConfiguration();return c.screenWidthDp<=360||c.screenHeightDp<=700;}
    private boolean insidePanel(MotionEvent e){if(panel==null||e==null)return false;int[] loc=new int[2];panel.getLocationOnScreen(loc);float x=e.getRawX(),y=e.getRawY();return x>=loc[0]&&x<=loc[0]+panel.getWidth()&&y>=loc[1]&&y<=loc[1]+panel.getHeight();}
    private TextView text(String s,float sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setTypeface(tf);return t;}
    private Button smallButton(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTypeface(tf);b.setTextSize(compactUi()?13:14);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(2,0,2,0);return b;}
    private void addBatchButton(String s,View.OnClickListener l){Button b=smallButton(s);b.setTextSize(compactUi()?11:12);b.setOnClickListener(l);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,compactUi()?40:44),1);lp.setMargins(Ui.dp(this,2),0,Ui.dp(this,2),0);batchBar.addView(b,lp);}

    private void refreshBackground(){
        if(wallpaper==null)return;
        String u=store.backgroundUri();
        if(u==null)u="";
        if(u.isEmpty()){
            backgroundCacheUri="";backgroundCacheDrawable=null;wallpaper.setImageDrawable(null);return;
        }
        if(u.equals(backgroundCacheUri)&&backgroundCacheDrawable!=null){wallpaper.setImageDrawable(backgroundCacheDrawable);return;}
        int w=Math.max(1,getResources().getDisplayMetrics().widthPixels);
        int h=Math.max(1,getResources().getDisplayMetrics().heightPixels);
        Drawable d=Ui.loadSampledDrawable(this,Uri.parse(u),w,h);
        backgroundCacheUri=d==null?"":u;backgroundCacheDrawable=d;
        wallpaper.setImageDrawable(d);
    }

    private void refreshAll(){
        ensureValidLocation();
        badgeAccess=NotificationBadgeStore.hasAccess(this);
        int pages=store.getPageCount(currentParent);if(currentPage>=pages)currentPage=pages-1;if(currentPage<0)currentPage=0;
        int key=store.pageColor(currentParent,currentPage);panel.setBackground(Ui.rounded(Ui.withAlpha(key,store.panelAlpha()),store.cornerRadius(),this));
        if(ItemModel.ROOT.equals(currentParent))title.setText("巨大フォルダ");else{ItemModel f=store.find(currentParent);title.setText(f==null?"フォルダ":f.name);}
        String q=searchBox==null?"":searchBox.getText().toString().trim();pageText.setText(q.isEmpty()?((currentPage+1)+" / "+pages):"検索結果");
        int cols=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?store.columnsLandscape():store.columnsPortrait();grid.setColumnCount(cols);
        batchBar.setVisibility(editMode?View.VISIBLE:View.GONE);editButton.setText(editMode?"完了":"編集");
        refreshGrid();refreshDock();
    }

    private void ensureValidLocation(){
        if(ItemModel.ROOT.equals(currentParent))return;
        ItemModel current=store.find(currentParent);
        if(current==null||!ItemModel.TYPE_FOLDER.equals(current.type)){
            currentParent=ItemModel.ROOT;currentPage=0;folderStack.clear();selectedIds.clear();editMode=false;
            if(searchBox!=null&&!searchBox.getText().toString().isEmpty())searchBox.setText("");
        }
    }

    private void refreshGrid(){
        if(grid==null)return;grid.removeAllViews();String q=searchBox==null?"":searchBox.getText().toString().trim();List<ItemModel> list=q.isEmpty()?store.children(currentParent,currentPage):store.searchVisible(q);
        for(ItemModel m:list)addCell(m);
        if(list.isEmpty()){
            TextView empty=text(q.isEmpty()?"ここは空っぽです\n＋ から追加できます":"見つかりませんでした",16,0xFFEFEFF5);empty.setGravity(Gravity.CENTER);
            int cols=Math.max(1,grid.getColumnCount());GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(0,1f),GridLayout.spec(0,cols,1f));lp.width=0;lp.height=Ui.dp(this,150);grid.addView(empty,lp);
        }
        MushinGlass.applyToView(this,grid);
    }

    private void addCell(ItemModel m){
        FrameLayout cell=new FrameLayout(this);cell.setTag(m.id);int bg=selectedIds.contains(m.id)?Ui.withAlpha(Color.WHITE,34):Ui.withAlpha(Color.WHITE,12);cell.setBackground(Ui.rounded(bg,18,this));
        boolean compact=compactUi();
        LinearLayout inner=new LinearLayout(this);inner.setOrientation(LinearLayout.VERTICAL);inner.setGravity(Gravity.CENTER);inner.setPadding(Ui.dp(this,compact?2:3),Ui.dp(this,compact?3:5),Ui.dp(this,compact?2:3),Ui.dp(this,compact?2:3));
        ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setImageDrawable(iconFor(m));int configured=store.iconSize();int is=compact?Math.min(configured,44):configured;
        TextView label=text(m.name,compact?11:12,Color.WHITE);label.setGravity(Gravity.CENTER);label.setMaxLines(2);inner.addView(iv,new LinearLayout.LayoutParams(Ui.dp(this,is),Ui.dp(this,is)));inner.addView(label,new LinearLayout.LayoutParams(-1,Ui.dp(this,compact?30:34)));cell.addView(inner,new FrameLayout.LayoutParams(-1,-1));
        if(m.favorite){TextView star=text("★",15,0xFFFFE082);star.setTag("keep_text_color");star.setGravity(Gravity.CENTER);cell.addView(star,new FrameLayout.LayoutParams(Ui.dp(this,22),Ui.dp(this,22),Gravity.START|Gravity.TOP));}
        if(ItemModel.TYPE_APP.equals(m.type)&&isNewApp(m.packageName)){TextView n=text("NEW",9,Color.WHITE);n.setTag("keep_text_color");n.setGravity(Gravity.CENTER);n.setBackground(Ui.rounded(Ui.withAlpha(store.keyColor(),95),9,this));FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(Ui.dp(this,31),Ui.dp(this,18),Gravity.START|Gravity.TOP);np.topMargin=Ui.dp(this,3);np.leftMargin=Ui.dp(this,3);cell.addView(n,np);}
        if(ItemModel.TYPE_APP.equals(m.type)&&badgeAccess){
            int count=NotificationBadgeStore.get(m.packageName);
            if(count>0){
                boolean numeric=store.badgeCountEnabled();
                TextView badge=text(numeric?(count>99?"99+":String.valueOf(count)):"●",numeric?10:11,Color.WHITE);badge.setTag("keep_text_color");
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(Ui.rounded(0xFFE64A4A,20,this));
                int bw=numeric?27:18,bh=numeric?22:18;
                FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(Ui.dp(this,bw),Ui.dp(this,bh),Gravity.END|Gravity.TOP);
                bp.topMargin=Ui.dp(this,2);bp.rightMargin=Ui.dp(this,2);cell.addView(badge,bp);
            }
        }
        TextView menu=text("⋮",compact?18:20,Color.WHITE);menu.setGravity(Gravity.CENTER);FrameLayout.LayoutParams mlp=new FrameLayout.LayoutParams(Ui.dp(this,compact?26:28),Ui.dp(this,compact?28:30),Gravity.END|Gravity.BOTTOM);cell.addView(menu,mlp);menu.setOnClickListener(v->showItemMenu(m,menu));
        inner.setOnClickListener(v->{
            if(editMode){
                if(ItemModel.TYPE_FOLDER.equals(m.type)&&m.locked)SecurityHelper.authenticate(this,m.name,()->toggleSelection(m.id));
                else toggleSelection(m.id);
                return;
            }
            if(ItemModel.TYPE_FOLDER.equals(m.type)){if(m.locked)SecurityHelper.authenticate(this,m.name,()->openFolder(m));else openFolder(m);}
            else if(ItemModel.TYPE_SMART.equals(m.type))openSmart(m.smartKind,m.name);else requestLaunch(m);
        });
        inner.setOnLongClickListener(v->{
            if(ItemModel.TYPE_FOLDER.equals(m.type)&&m.locked){SecurityHelper.authenticate(this,m.name,()->handleItemLongPress(m,cell));return true;}
            handleItemLongPress(m,cell);return true;
        });
        if(ItemModel.TYPE_FOLDER.equals(m.type)){cell.setOnDragListener((v,e)->{if(!(e.getLocalState() instanceof DragPayload))return false;if(e.getAction()==DragEvent.ACTION_DRAG_STARTED)return true;if(e.getAction()==DragEvent.ACTION_DROP){if(m.locked){Toast.makeText(this,"ロック中のフォルダへは移動できません",Toast.LENGTH_SHORT).show();return true;}moveIdsIntoFolder(((DragPayload)e.getLocalState()).ids,m.id);return true;}return true;});}
        int cols=Math.max(1,grid.getColumnCount());int h=Math.max(compact?84:96,is+(compact?40:50));GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=Ui.dp(this,h);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);int margin=Ui.dp(this,compact?2:3);lp.setMargins(margin,margin,margin,margin);grid.addView(cell,lp);
    }

    private void handleItemLongPress(ItemModel m,View cell){
        if(!searchBox.getText().toString().trim().isEmpty()){showItemMenuUnlocked(m,cell);return;}
        if(!editMode&&store.layoutLocked()){showItemMenuUnlocked(m,cell);return;}
        if(!editMode){editMode=true;selectedIds.clear();selectedIds.add(m.id);batchBar.setVisibility(View.VISIBLE);editButton.setText("完了");}
        startItemDrag(m,cell);
    }

    private boolean isNewApp(String pkg){
        Boolean cached=newBadgeCache.get(pkg);
        if(cached!=null)return cached;
        boolean value=AppCatalog.isNew(this,pkg);
        newBadgeCache.put(pkg,value);
        return value;
    }

    private void invalidateIconCache(String itemId){
        if(itemId==null||itemId.isEmpty())return;
        String prefix=itemId+"|";
        appIconCache.keySet().removeIf(k->k!=null&&k.startsWith(prefix));
    }

    private Drawable iconFor(ItemModel m){
        if(ItemModel.TYPE_FOLDER.equals(m.type)||ItemModel.TYPE_SMART.equals(m.type)){GradientDrawable g=new GradientDrawable();g.setColor(Ui.withAlpha(store.pageColor(currentParent,currentPage),92));g.setCornerRadius(Ui.dp(this,15));return g;}
        String iconKey=m.id+"|"+m.packageName+"|"+m.activityName+"|"+(m.customIconUri==null?"":m.customIconUri);
        Drawable.ConstantState cached=appIconCache.get(iconKey);
        if(cached!=null)return cached.newDrawable(getResources());
        Drawable loaded=null;
        if(m.customIconUri!=null&&!m.customIconUri.isEmpty())loaded=Ui.loadSampledDrawable(this,Uri.parse(m.customIconUri),Ui.dp(this,96),Ui.dp(this,96));
        if(loaded==null)try{loaded=getPackageManager().getActivityIcon(new ComponentName(m.packageName,m.activityName));}catch(Exception e){try{loaded=getPackageManager().getApplicationIcon(m.packageName);}catch(Exception ignored){loaded=getDrawable(android.R.drawable.sym_def_app_icon);}}
        if(loaded!=null&&loaded.getConstantState()!=null)appIconCache.put(iconKey,loaded.getConstantState());
        return loaded;
    }

    private void refreshDock(){
        dock.removeAllViews();List<ItemModel> list=store.dockItems();if(list.isEmpty()){dock.setVisibility(View.GONE);return;}dock.setVisibility(View.VISIBLE);dock.setBackground(Ui.rounded(Ui.withAlpha(store.keyColor(),35),18,this));
        boolean compact=compactUi();for(ItemModel m:list){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);ImageView iv=new ImageView(this);iv.setImageDrawable(iconFor(m));TextView tv=text(m.name,compact?9:10,Color.WHITE);tv.setGravity(Gravity.CENTER);tv.setSingleLine(true);int icon=compact?30:34;c.addView(iv,new LinearLayout.LayoutParams(Ui.dp(this,icon),Ui.dp(this,icon)));c.addView(tv,new LinearLayout.LayoutParams(-1,Ui.dp(this,compact?16:18)));c.setOnClickListener(v->requestLaunch(m));c.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("ドックから外しますか？").setMessage(m.name).setPositiveButton("外す",(d,w)->{store.deleteRecursive(m.id);refreshDock();}).setNegativeButton("キャンセル",null).show();return true;});dock.addView(c,new LinearLayout.LayoutParams(0,-1,1));}
        MushinGlass.applyToView(this,dock);
    }

    private void toggleEditMode(){if(searchBox!=null&&!searchBox.getText().toString().trim().isEmpty()){Toast.makeText(this,"検索中は編集できません",Toast.LENGTH_SHORT).show();return;}editMode=!editMode;if(!editMode)selectedIds.clear();refreshAll();}
    private void toggleSelection(String id){if(selectedIds.contains(id))selectedIds.remove(id);else selectedIds.add(id);refreshGrid();}
    private void startItemDrag(ItemModel m,View source){
        if(!editMode)return;ArrayList<String> ids=new ArrayList<>();if(selectedIds.contains(m.id)&&selectedIds.size()>1)ids.addAll(selectedIds);else{ids.add(m.id);selectedIds.clear();selectedIds.add(m.id);}TextView shadow=text(ids.size()>1?ids.size()+"個":"移動",16,Color.WHITE);shadow.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14),Ui.dp(this,8));shadow.setBackground(Ui.rounded(Ui.withAlpha(store.keyColor(),95),20,this));shadow.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED);shadow.layout(0,0,shadow.getMeasuredWidth(),shadow.getMeasuredHeight());source.startDragAndDrop(null,new View.DragShadowBuilder(shadow),new DragPayload(ids),0);
    }

    private boolean onGridDrag(View v,DragEvent e){
        if(!(e.getLocalState() instanceof DragPayload))return false;DragPayload p=(DragPayload)e.getLocalState();String q=searchBox.getText().toString().trim();if(!q.isEmpty())return false;
        switch(e.getAction()){
            case DragEvent.ACTION_DRAG_STARTED:edgeSince=0;edgeDir=0;return true;
            case DragEvent.ACTION_DRAG_LOCATION:int dir=0;if(e.getX()<grid.getWidth()*0.1f)dir=-1;else if(e.getX()>grid.getWidth()*0.9f)dir=1;if(dir!=0){long now=System.currentTimeMillis();if(edgeDir!=dir){edgeDir=dir;edgeSince=now;}else if(now-edgeSince>650){if(dir<0)prevPage();else nextPage();edgeSince=now+300;}}else{edgeDir=0;edgeSince=0;}return true;
            case DragEvent.ACTION_DROP:dropItems(p.ids,e.getX(),e.getY());return true;
            case DragEvent.ACTION_DRAG_ENDED:edgeDir=0;edgeSince=0;selectedIds.clear();refreshGrid();return true;
        }return true;
    }

    private void dropItems(List<String> ids,float x,float y){
        List<ItemModel> dest=store.children(currentParent,currentPage);dest.removeIf(m->ids.contains(m.id));int target=dest.size();
        for(int i=0;i<grid.getChildCount();i++){View c=grid.getChildAt(i);if(c.getTag()==null)continue;float cx=c.getX()+c.getWidth()/2f,cy=c.getY()+c.getHeight()/2f;if(y<cy||(Math.abs(y-cy)<c.getHeight()/2f&&x<cx)){String tid=String.valueOf(c.getTag());for(int j=0;j<dest.size();j++)if(dest.get(j).id.equals(tid)){target=j;break;}break;}}
        ArrayList<ItemModel> moving=new ArrayList<>();for(String id:ids){ItemModel m=store.find(id);if(m!=null)moving.add(m);}Collections.sort(moving,(a,b)->Integer.compare(a.order,b.order));for(ItemModel m:moving){m.parentId=currentParent;m.page=currentPage;}target=Math.max(0,Math.min(target,dest.size()));dest.addAll(target,moving);for(int i=0;i<dest.size();i++)dest.get(i).order=i;store.normalize(currentParent);store.save();refreshAll();
    }

    private void moveIdsIntoFolder(List<String> ids,String folderId){for(String id:ids){ItemModel m=store.find(id);if(m!=null&&!m.id.equals(folderId)){m.parentId=folderId;m.page=0;m.order=store.nextOrder(folderId,0);}}store.normalize(currentParent);store.save();selectedIds.clear();refreshAll();}

    private void showAddMenu(View anchor){
        PopupMenu pm=new PopupMenu(this,anchor);pm.getMenu().add("アプリを追加");pm.getMenu().add("フォルダを作成");pm.getMenu().add("スマートフォルダを作成");pm.getMenu().add("新しいページ");pm.setOnMenuItemClickListener(it->{String s=String.valueOf(it.getTitle());if(s.startsWith("アプリ"))startActivityForResult(new Intent(this,AppPickerActivity.class),REQ_PICK);else if(s.startsWith("フォルダ"))createFolder();else if(s.startsWith("スマート"))createSmartFolder();else addPage();return true;});pm.show();
    }

    private void showItemMenu(ItemModel m,View anchor){
        if(ItemModel.TYPE_FOLDER.equals(m.type)&&m.locked){
            SecurityHelper.authenticate(this,m.name,()->showItemMenuUnlocked(m,anchor));
            return;
        }
        showItemMenuUnlocked(m,anchor);
    }

    private void showItemMenuUnlocked(ItemModel m,View anchor){
        PopupMenu pm=new PopupMenu(this,anchor);pm.getMenu().add(m.favorite?"お気に入り解除":"お気に入りに固定");pm.getMenu().add("名前を変更");
        if(ItemModel.TYPE_APP.equals(m.type)){pm.getMenu().add("アイコンを変更");pm.getMenu().add("このページにコピー");pm.getMenu().add("ドックに追加");pm.getMenu().add("使用状況");pm.getMenu().add("アプリ情報");pm.getMenu().add("アンインストール");}
        if(ItemModel.TYPE_FOLDER.equals(m.type))pm.getMenu().add(m.locked?"フォルダロック解除":"フォルダをロック");
        pm.getMenu().add("ページを移動");pm.getMenu().add("非表示");pm.getMenu().add("巨大フォルダから削除");
        pm.setOnMenuItemClickListener(it->{String s=String.valueOf(it.getTitle());if(s.startsWith("お気に入り")){m.favorite=!m.favorite;store.save();refreshAll();}else if(s.startsWith("名前"))renameItem(m);else if(s.startsWith("アイコン"))pickCustomIcon(m);else if(s.startsWith("このページ")){store.copyTo(m,currentParent,currentPage);refreshAll();}else if(s.startsWith("ドック"))addToDock(m);else if(s.startsWith("使用状況"))showUsage(m);else if(s.equals("アプリ情報"))openAppInfo(m);else if(s.startsWith("アンインストール"))uninstall(m);else if(s.contains("ロック")){if(m.locked){m.locked=false;store.save();refreshAll();}else if(SecurityHelper.canAuthenticate(this)){m.locked=true;store.save();refreshAll();}else Toast.makeText(this,"本人確認を利用できないためロックできません",Toast.LENGTH_SHORT).show();}else if(s.startsWith("ページ"))choosePage(m);else if(s.startsWith("非表示")){m.hidden=true;store.save();refreshAll();}else confirmDelete(m);return true;});pm.show();
    }

    private void renameItem(ItemModel m){EditText e=new EditText(this);e.setText(m.name);e.setSelectAllOnFocus(true);new AlertDialog.Builder(this).setTitle("名前を変更").setView(e).setPositiveButton("保存",(d,w)->{String n=e.getText().toString().trim();if(!n.isEmpty()){m.name=n;store.save();refreshAll();}}).setNegativeButton("キャンセル",null).show();}
    private void pickCustomIcon(ItemModel m){pendingIconId=m.id;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_ICON);}
    private void addToDock(ItemModel m){if(!ItemModel.TYPE_APP.equals(m.type))return;if(store.dockItems().size()>=6){Toast.makeText(this,"ドックは6個までです",Toast.LENGTH_SHORT).show();return;}store.copyTo(m,ItemModel.DOCK,0);refreshDock();}

    private void showUsage(ItemModel m){
        UsageTools.Snapshot s=UsageTools.snapshot(this);
        long ownLast=store.ownLastLaunch(m.packageName);
        String msg="巨大フォルダからの起動: "+store.ownLaunchCount(m.packageName)+"回";
        if(s.available){
            int count=s.launches30d.getOrDefault(m.packageName,0);
            long last=s.lastUsed.getOrDefault(m.packageName,ownLast);
            String lastText=last==0?"記録なし":DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(last));
            msg+="\n端末全体の直近30日前面表示: "+count+"回（目安）\n最後に使った日時: "+lastText;
        }else{
            String ownLastText=ownLast==0?"記録なし":DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(ownLast));
            msg+="\n最後に巨大フォルダから起動: "+ownLastText+"\n\n※ 端末全体の使用状況は未許可のため表示していません。";
        }
        new AlertDialog.Builder(this).setTitle(m.name+" の使用状況").setMessage(msg).setPositiveButton("OK",null).show();
    }

    private void openAppInfo(ItemModel m){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+m.packageName)));}catch(Exception ignored){}}
    private void uninstall(ItemModel m){try{startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+m.packageName)));}catch(Exception ignored){}}
    private void choosePage(ItemModel m){String parent=m.parentId;int pages=store.getPageCount(parent);String[] labels=new String[pages];for(int i=0;i<pages;i++)labels[i]=(i+1)+"ページ";new AlertDialog.Builder(this).setTitle("移動先").setItems(labels,(d,w)->{m.page=w;m.order=store.nextOrder(parent,w);store.normalize(parent);store.save();if(parent.equals(currentParent))currentPage=w;refreshAll();}).show();}
    private void confirmDelete(ItemModel m){new AlertDialog.Builder(this).setTitle("削除しますか？").setMessage(ItemModel.TYPE_FOLDER.equals(m.type)?"フォルダ内の項目も一緒に巨大フォルダから削除します。":"元のアプリはアンインストールされません。").setPositiveButton("削除",(d,w)->{store.deleteRecursive(m.id);refreshAll();}).setNegativeButton("キャンセル",null).show();}

    private void createFolder(){EditText e=new EditText(this);e.setHint("例：仕事");new AlertDialog.Builder(this).setTitle("新しいフォルダ").setView(e).setPositiveButton("作成",(d,w)->{String n=e.getText().toString().trim();if(n.isEmpty())n="フォルダ";ItemModel m=new ItemModel();m.type=ItemModel.TYPE_FOLDER;m.name=n;m.parentId=currentParent;m.page=currentPage;m.order=store.nextOrder(currentParent,currentPage);store.add(m);refreshAll();}).setNegativeButton("キャンセル",null).show();}
    private void createSmartFolder(){String[] names={"最近使ったアプリ","よく使うアプリ","新しく入れたアプリ","ゲーム","SNS（推定）","Google系"};String[] kinds={"RECENT","FREQUENT","NEW","GAMES","SNS","GOOGLE"};new AlertDialog.Builder(this).setTitle("スマートフォルダ").setItems(names,(d,w)->{ItemModel m=new ItemModel();m.type=ItemModel.TYPE_SMART;m.name=names[w];m.smartKind=kinds[w];m.parentId=currentParent;m.page=currentPage;m.order=store.nextOrder(currentParent,currentPage);store.add(m);refreshAll();}).show();}
    private void openSmart(String kind,String name){startActivity(new Intent(this,SmartFolderActivity.class).putExtra("kind",kind).putExtra("name",name));}

    private void openFolder(ItemModel m){folderStack.add(currentParent);currentParent=m.id;currentPage=0;selectedIds.clear();searchBox.setText("");refreshAll();}
    private void goBackFolder(){if(!searchBox.getText().toString().isEmpty()){searchBox.setText("");return;}if(ItemModel.ROOT.equals(currentParent)){finish();return;}String next=ItemModel.ROOT;while(!folderStack.isEmpty()){String candidate=folderStack.remove(folderStack.size()-1);if(ItemModel.ROOT.equals(candidate)||store.find(candidate)!=null){next=candidate;break;}}currentParent=next;currentPage=0;selectedIds.clear();refreshAll();}
    @Override public void onBackPressed(){if(editMode){editMode=false;selectedIds.clear();refreshAll();}else if(!ItemModel.ROOT.equals(currentParent)||!searchBox.getText().toString().isEmpty())goBackFolder();else super.onBackPressed();}

    private void prevPage(){if(!searchBox.getText().toString().isEmpty())return;if(currentPage>0){currentPage--;refreshAll();}}
    private void nextPage(){if(!searchBox.getText().toString().isEmpty())return;int n=store.getPageCount(currentParent);if(currentPage<n-1){currentPage++;refreshAll();}}
    private void addPage(){int n=store.getPageCount(currentParent)+1;store.setPageCount(currentParent,n);currentPage=n-1;refreshAll();}

    private void showPageMenu(View anchor){PopupMenu pm=new PopupMenu(this,anchor);pm.getMenu().add("ページを追加");pm.getMenu().add("このページを複製");pm.getMenu().add("ページの色");pm.getMenu().add("整理アシスト");pm.setOnMenuItemClickListener(it->{String s=String.valueOf(it.getTitle());if(s.startsWith("ページを追加"))addPage();else if(s.startsWith("このページ")){store.duplicatePage(currentParent,currentPage);currentPage=store.getPageCount(currentParent)-1;refreshAll();}else if(s.startsWith("ページの色"))showPageColorPicker();else showOrganizeAssist();return true;});pm.show();}
    private void showPageColorPicker(){final int[] colors={0xFFE57373,0xFFFF8A65,0xFFFFB74D,0xFFFFD54F,0xFFAED581,0xFF66BB6A,0xFF4DB6AC,0xFF4DD0E1,0xFF64B5F6,0xFF7986CB,0xFF9575CD,0xFFBA68C8,0xFFF06292,0xFFA1887F,0xFF90A4AE,0xFF7C6FF2};GridLayout g=new GridLayout(this);g.setColumnCount(4);g.setPadding(20,20,20,20);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("ページの色").setView(g).setNegativeButton("閉じる",null).create();for(int c:colors){View sw=new View(this);sw.setBackground(Ui.rounded(c,14,this));GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=Ui.dp(this,46);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);lp.setMargins(8,8,8,8);g.addView(sw,lp);sw.setOnClickListener(v->{store.setPageColor(currentParent,currentPage,c);refreshAll();dialog.dismiss();});}dialog.show();}

    private void showOrganizeAssist(){
        UsageTools.Snapshot usage=UsageTools.snapshot(this);
        int unused=0,newCount=0;
        for(AppCatalog.Entry e:AppCatalog.listLaunchable(this)){
            if(AppCatalog.matchesSmart(e,"UNUSED30",usage))unused++;
            if(AppCatalog.matchesSmart(e,"NEW",usage))newCount++;
        }
        if(!usage.available){
            String msg="新しく入れたアプリ: "+newCount+"個\n\n30日未使用の判定には『使用状況へのアクセス』が必要です。未許可のときに全アプリを未使用扱いにはしません。";
            new AlertDialog.Builder(this).setTitle("整理アシスト").setMessage(msg).setPositiveButton("新しいアプリ",(d,w)->openSmart("NEW","新しく入れたアプリ")).setNeutralButton("権限を確認",(d,w)->startActivity(new Intent(this,PermissionSetupActivity.class))).setNegativeButton("閉じる",null).show();
            return;
        }
        String msg="新しく入れたアプリ: "+newCount+"個\n30日以上使っていない可能性のあるアプリ: "+unused+"個\n\n使用状況はAndroidの記録を使うため目安です。";
        new AlertDialog.Builder(this).setTitle("整理アシスト").setMessage(msg).setPositiveButton("新しいアプリ",(d,w)->openSmart("NEW","新しく入れたアプリ")).setNeutralButton("30日未使用",(d,w)->openSmart("UNUSED30","30日以上未使用")).setNegativeButton("閉じる",null).show();
    }

    private void moveSelectedPage(){if(selectedIds.isEmpty()){Toast.makeText(this,"移動する項目を選んでください",Toast.LENGTH_SHORT).show();return;}int pages=store.getPageCount(currentParent);String[] labels=new String[pages];for(int i=0;i<pages;i++)labels[i]=(i+1)+"ページ";new AlertDialog.Builder(this).setTitle("まとめてページ移動").setItems(labels,(d,w)->{for(String id:selectedIds){ItemModel m=store.find(id);if(m!=null){m.parentId=currentParent;m.page=w;m.order=store.nextOrder(currentParent,w);}}store.normalize(currentParent);store.save();selectedIds.clear();currentPage=w;refreshAll();}).show();}
    private void moveSelectedFolder(){if(selectedIds.isEmpty()){Toast.makeText(this,"項目を選んでください",Toast.LENGTH_SHORT).show();return;}ArrayList<ItemModel> fs=new ArrayList<>();for(ItemModel m:store.childrenAllPages(currentParent))if(ItemModel.TYPE_FOLDER.equals(m.type)&&!m.locked&&!selectedIds.contains(m.id))fs.add(m);if(fs.isEmpty()){Toast.makeText(this,"移動先フォルダがありません",Toast.LENGTH_SHORT).show();return;}String[] n=new String[fs.size()];for(int i=0;i<n.length;i++)n[i]=fs.get(i).name;new AlertDialog.Builder(this).setTitle("移動先フォルダ").setItems(n,(d,w)->moveIdsIntoFolder(new ArrayList<>(selectedIds),fs.get(w).id)).show();}
    private void hideSelected(){for(String id:new ArrayList<>(selectedIds)){ItemModel m=store.find(id);if(m!=null)m.hidden=true;}store.save();selectedIds.clear();refreshAll();}
    private void deleteSelected(){if(selectedIds.isEmpty())return;new AlertDialog.Builder(this).setTitle(selectedIds.size()+"個を削除しますか？").setMessage("巨大フォルダから外すだけです。元のアプリは削除されません。").setPositiveButton("削除",(d,w)->{store.deleteRecursiveAll(new ArrayList<>(selectedIds));selectedIds.clear();refreshAll();}).setNegativeButton("キャンセル",null).show();}

    private void requestLaunch(ItemModel m){
        // オーバーレイ未許可でも毎回設定画面を出さず、補助通知へ自動フォールバックする。
        boolean launched=AppLaunchManager.launch(this,store,m,Settings.canDrawOverlays(this));
        if(!launched)Toast.makeText(this,"アプリを起動できませんでした",Toast.LENGTH_SHORT).show();
    }
    private void hideOverlay(){try{stopService(new Intent(this,OverlayService.class));}catch(Exception ignored){}}

    @Override public boolean dispatchTouchEvent(MotionEvent ev){if(gestures!=null)gestures.onTouchEvent(ev);return super.dispatchTouchEvent(ev);}

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==REQ_PICK&&result==RESULT_OK&&data!=null){String raw=data.getStringExtra("apps_json");try{JSONArray a=new JSONArray(raw==null?"[]":raw);ArrayList<ItemModel> added=new ArrayList<>();int order=store.nextOrder(currentParent,currentPage);for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);ItemModel m=new ItemModel();m.type=ItemModel.TYPE_APP;m.name=o.optString("label","アプリ");m.packageName=o.optString("package","");m.activityName=o.optString("activity","");m.parentId=currentParent;m.page=currentPage;m.order=order++;added.add(m);}store.addAll(added);}catch(Exception e){Toast.makeText(this,"追加に失敗しました",Toast.LENGTH_SHORT).show();}}
        if(request==REQ_ICON){
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingIconId!=null){
                Uri u=data.getData();
                try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
                ItemModel m=store.find(pendingIconId);
                if(m!=null){m.customIconUri=u.toString();store.save();invalidateIconCache(m.id);}
            }
            // A cancelled picker must not leave a stale target ID around for a later result.
            pendingIconId=null;
        }
    }
}
