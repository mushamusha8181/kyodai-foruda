package com.mushamusha.giantfolder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SettingsActivity extends Activity {
    private static final int REQ_BG=2201,REQ_BACKUP=2202,REQ_RESTORE=2203;
    private DataStore store; private LinearLayout preview; private Typeface tf; private TextView permissionText;
    private final int[] colors={0xFFE57373,0xFFFF8A65,0xFFFFB74D,0xFFFFD54F,0xFFAED581,0xFF66BB6A,0xFF4DB6AC,0xFF4DD0E1,0xFF64B5F6,0xFF7986CB,0xFF9575CD,0xFFBA68C8,0xFFF06292,0xFFA1887F,0xFF90A4AE,0xFF7C6FF2};

    @Override public void onCreate(Bundle b){super.onCreate(b);store=new DataStore(this);tf=Ui.appTypeface(this);build();}
    @Override protected void onResume(){super.onResume();store.reloadIfChanged();if(permissionText!=null)permissionText.setText(permissionSummary());}

    private void build(){
        boolean compact=getResources().getConfiguration().screenWidthDp<=360||getResources().getConfiguration().screenHeightDp<=700;ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xFF18181B);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,compact?9:12),Ui.dp(this,compact?9:12),Ui.dp(this,compact?9:12),Ui.dp(this,compact?18:24));scroll.addView(root);
        root.addView(text("設定",compact?22:24,Color.WHITE));

        section(root,"見た目");
        root.addView(text("キーカラー",14,0xFFE6E6EC));GridLayout palette=new GridLayout(this);palette.setColumnCount(4);for(int c:colors){View sw=new View(this);android.graphics.drawable.GradientDrawable d=Ui.rounded(c,16,this);if(c==store.keyColor())d.setStroke(Ui.dp(this,3),Color.WHITE);sw.setBackground(d);GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=Ui.dp(this,46);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);lp.setMargins(5,5,5,5);palette.addView(sw,lp);sw.setOnClickListener(v->{store.setKeyColor(c);recreate();});}root.addView(palette);
        addSeek(root,"フォルダ背景の透明度",40,95,store.panelAlpha(),store::setPanelAlpha);
        addSeek(root,"角丸",10,44,store.cornerRadius(),store::setCornerRadius);
        Button bg=button("背景画像を選ぶ");bg.setOnClickListener(v->pickBackground());root.addView(bg,rowLp());
        if(!store.backgroundUri().isEmpty()){Button clear=button("背景画像を解除");clear.setOnClickListener(v->{store.setBackgroundUri("");Toast.makeText(this,"背景画像を解除しました",Toast.LENGTH_SHORT).show();recreate();});root.addView(clear,rowLp());}
        preview=new LinearLayout(this);preview.setGravity(Gravity.CENTER);preview.addView(text("巨大フォルダ",20,Color.WHITE));root.addView(preview,new LinearLayout.LayoutParams(-1,Ui.dp(this,92)));updatePreview();

        section(root,"並び方");
        addSeek(root,"縦画面の列数",4,7,store.columnsPortrait(),store::setColumnsPortrait);
        addSeek(root,"横画面の列数",4,9,store.columnsLandscape(),store::setColumnsLandscape);
        addSeek(root,"アイコンサイズ",36,68,store.iconSize(),store::setIconSize);
        CheckBox lock=check("レイアウトを固定する",store.layoutLocked());lock.setOnCheckedChangeListener((b,v)->store.setLayoutLocked(v));root.addView(lock,rowLp());
        CheckBox appLock=check("巨大フォルダ起動時にロック（起動中は再認証なし）",store.appLock());appLock.setOnCheckedChangeListener((b,v)->{
            if(v&&!SecurityHelper.canAuthenticate(this)){b.setChecked(false);Toast.makeText(this,"端末で本人確認を利用できないため有効にできません",Toast.LENGTH_SHORT).show();return;}
            store.setAppLock(v);
        });root.addView(appLock,rowLp());

        section(root,"戻るベロ");
        Button side=button("表示位置: "+("left".equals(store.overlaySide())?"左":"右"));side.setOnClickListener(v->{store.setOverlaySide("left".equals(store.overlaySide())?"right":"left");recreate();});root.addView(side,rowLp());
        addSeek(root,"ベロの透明度",35,100,store.overlayAlpha(),store::setOverlayAlpha);
        addSeek(root,"ベロの幅",26,60,store.overlayWidth(),store::setOverlayWidth);
        addSeek(root,"ベロの高さ",64,150,store.overlayHeight(),store::setOverlayHeight);
        root.addView(text("ベロをタップすると最近使った5個の簡易ランチャー、内側へスワイプすると巨大フォルダ本体を開きます。",13,0xFFBDBDC6));

        section(root,"戻る補助通知");
        CheckBox reminder=check("ベロを使えないとき補助通知を表示",store.returnReminderEnabled());
        reminder.setOnCheckedChangeListener((b,v)->{store.setReturnReminderEnabled(v);if(!v)ReturnReminderManager.endSession(this);});root.addView(reminder,rowLp());
        Button snooze=button("再通知: "+store.returnReminderSnoozeMinutes()+"分後");
        snooze.setOnClickListener(v->showReminderSnoozeDialog());root.addView(snooze,rowLp());
        root.addView(text("通知には『開く』『○分後』『今回は通知しない』を表示します。『今回は通知しない』は次に巨大フォルダから別アプリを開くまで有効です。",13,0xFFBDBDC6));

        section(root,"通知バッジ");
        CheckBox badgeCount=check("数字で現在の通知件数を表示",store.badgeCountEnabled());
        badgeCount.setOnCheckedChangeListener((b,v)->store.setBadgeCountEnabled(v));root.addView(badgeCount,rowLp());
        root.addView(text("OFFでは通知があるアプリに●だけ表示します。数字は未読数ではなく、現在Androidに出ている通知の件数です。常駐通知は数えません。",13,0xFFBDBDC6));

        section(root,"非表示項目");Button hidden=button("非表示項目を管理（"+store.hiddenItems().size()+"個）");hidden.setOnClickListener(v->showHiddenManager());root.addView(hidden,rowLp());

        section(root,"権限");permissionText=text(permissionSummary(),14,0xFFD0D0D8);root.addView(permissionText);Button permissions=button("権限を再確認する");permissions.setOnClickListener(v->startActivity(new Intent(this,PermissionSetupActivity.class)));root.addView(permissions,rowLp());

        section(root,"バックアップ");root.addView(text("配置と設定を保存します。権限状態・使用履歴は端末ごとなのでバックアップしません。別端末では背景やカスタム画像の再選択が必要な場合があります。",13,0xFFBDBDC6));Button backup=button("設定と配置をバックアップ");backup.setOnClickListener(v->createBackup());root.addView(backup,rowLp());Button restore=button("バックアップから復元");restore.setOnClickListener(v->restoreBackup());root.addView(restore,rowLp());

        section(root,"フォント");root.addView(text("りいてがきN R",17,0xFFE0E0E7));root.addView(text("assets/fonts/RiiTegakiNR.ttf または .otf を入れるとアプリ全体へ適用します。フォントファイル本体は同梱していません。",13,0xFFAFAFBA));
        Ui.applySafeInsets(root);setContentView(scroll);Ui.applyFont(scroll,tf);
    }

    private interface IntSetter{void set(int v);} private void addSeek(LinearLayout root,String label,int min,int max,int value,IntSetter setter){TextView t=text(label+": "+value,14,0xFFE6E6EC);root.addView(t);SeekBar s=new SeekBar(this);s.setMax(max-min);s.setProgress(Math.max(0,Math.min(max-min,value-min)));root.addView(s);final int[] pending={value};s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean f){pending[0]=min+p;t.setText(label+": "+pending[0]);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){setter.set(pending[0]);updatePreview();}});}
    private void section(LinearLayout root,String s){TextView t=text(s,19,Color.WHITE);t.setPadding(0,Ui.dp(this,18),0,Ui.dp(this,7));root.addView(t);}
    private TextView text(String s,float sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setTypeface(tf);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTypeface(tf);b.setTextSize(13);b.setMinHeight(0);b.setMinimumHeight(0);return b;}
    private CheckBox check(String s,boolean c){CheckBox b=new CheckBox(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setChecked(c);b.setTypeface(tf);return b;}
    private LinearLayout.LayoutParams rowLp(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,46));lp.setMargins(0,Ui.dp(this,4),0,0);return lp;}
    private void updatePreview(){if(preview!=null)preview.setBackground(Ui.rounded(Ui.withAlpha(store.keyColor(),store.panelAlpha()),store.cornerRadius(),this));}

    private void showReminderSnoozeDialog(){
        String[] labels={"15分後","30分後","1時間後"};int[] values={15,30,60};int current=store.returnReminderSnoozeMinutes();int checked=current==15?0:(current==60?2:1);
        new AlertDialog.Builder(this).setTitle("補助通知の再通知時間").setSingleChoiceItems(labels,checked,(d,w)->{store.setReturnReminderSnoozeMinutes(values[w]);d.dismiss();recreate();}).setNegativeButton("キャンセル",null).show();
    }

    private void pickBackground(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,REQ_BG);}
    private void createBackup(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").putExtra(Intent.EXTRA_TITLE,"GiantFolder-backup.json").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_BACKUP);}
    private void restoreBackup(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_RESTORE);}

    private void showHiddenManager(){List<ItemModel> list=store.hiddenItems();if(list.isEmpty()){Toast.makeText(this,"非表示の項目はありません",Toast.LENGTH_SHORT).show();return;}String[] names=new String[list.size()];boolean[] checked=new boolean[list.size()];for(int i=0;i<names.length;i++){names[i]=list.get(i).name;checked[i]=false;}new AlertDialog.Builder(this).setTitle("表示へ戻す項目を選択").setMultiChoiceItems(names,checked,(d,w,c)->checked[w]=c).setPositiveButton("表示へ戻す",(d,w)->{for(int i=0;i<list.size();i++)if(checked[i])list.get(i).hidden=false;store.save();recreate();}).setNegativeButton("キャンセル",null).show();}

    private String permissionSummary(){return "通知: "+(android.os.Build.VERSION.SDK_INT<33||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED?"OK":"未許可")+" / 重ねて表示: "+(Settings.canDrawOverlays(this)?"OK":"未許可")+" / 使用状況: "+(UsageTools.hasAccess(this)?"OK":"未許可")+" / 通知アクセス: "+(NotificationBadgeStore.hasAccess(this)?"OK":"未許可");}

    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();try{if(request==REQ_BG){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}store.setBackgroundUri(u.toString());Toast.makeText(this,"背景画像を設定しました",Toast.LENGTH_SHORT).show();}else if(request==REQ_BACKUP){try(OutputStream out=getContentResolver().openOutputStream(u)){if(out==null)throw new java.io.IOException("Cannot open output");out.write(store.exportJson().getBytes(StandardCharsets.UTF_8));}Toast.makeText(this,"バックアップしました",Toast.LENGTH_SHORT).show();}else if(request==REQ_RESTORE){String raw=readAll(u);new AlertDialog.Builder(this).setTitle("復元しますか？").setMessage("現在の配置と設定はバックアップ内容で置き換わります。元に戻すには別のバックアップが必要です。").setPositiveButton("復元",(d,w)->{try{store.importJson(raw);Toast.makeText(this,"復元しました",Toast.LENGTH_SHORT).show();recreate();}catch(Exception e){Toast.makeText(this,"復元できませんでした",Toast.LENGTH_SHORT).show();}}).setNegativeButton("キャンセル",null).show();}}catch(Exception e){Toast.makeText(this,"処理に失敗しました",Toast.LENGTH_SHORT).show();}}
    private String readAll(Uri u)throws Exception{
        final int maxBytes=10*1024*1024;
        try(InputStream in=getContentResolver().openInputStream(u);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new java.io.IOException("Cannot open input");
            byte[] buf=new byte[8192];int n,total=0;
            while((n=in.read(buf))>0){
                total+=n;
                if(total>maxBytes)throw new java.io.IOException("Backup is too large");
                out.write(buf,0,n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
