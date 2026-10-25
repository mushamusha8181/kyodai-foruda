from pathlib import Path

# trigger: 2026-10-25.2
p = Path('app/src/main/java/com/mushamusha/giantfolder/MainActivity.java')
s = p.read_text()

if 'import jp.mushamusha.mushinglass.MushinGlass;' not in s:
    anchor = 'import java.util.Set;\n'
    if anchor not in s:
        raise SystemExit('import anchor missing')
    s = s.replace(anchor, anchor + '\nimport jp.mushamusha.mushinglass.MushinGlass;\n', 1)

if 'MushinGlass.applyToView(this,grid);' not in s:
    anchor = '''        if(list.isEmpty()){
            TextView empty=text(q.isEmpty()?"ここは空っぽです\\n＋ から追加できます":"見つかりませんでした",16,0xFFEFEFF5);empty.setGravity(Gravity.CENTER);
            int cols=Math.max(1,grid.getColumnCount());GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(0,1f),GridLayout.spec(0,cols,1f));lp.width=0;lp.height=Ui.dp(this,150);grid.addView(empty,lp);
        }
    }

    private void addCell'''
    replacement = '''        if(list.isEmpty()){
            TextView empty=text(q.isEmpty()?"ここは空っぽです\\n＋ から追加できます":"見つかりませんでした",16,0xFFEFEFF5);empty.setGravity(Gravity.CENTER);
            int cols=Math.max(1,grid.getColumnCount());GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(0,1f),GridLayout.spec(0,cols,1f));lp.width=0;lp.height=Ui.dp(this,150);grid.addView(empty,lp);
        }
        MushinGlass.applyToView(this,grid);
    }

    private void addCell'''
    if anchor not in s:
        raise SystemExit('refreshGrid anchor missing')
    s = s.replace(anchor, replacement, 1)

for old, new in [
    ('TextView star=text("★",15,0xFFFFE082);star.setGravity(Gravity.CENTER);', 'TextView star=text("★",15,0xFFFFE082);star.setTag("keep_text_color");star.setGravity(Gravity.CENTER);'),
    ('TextView n=text("NEW",9,Color.WHITE);n.setGravity(Gravity.CENTER);', 'TextView n=text("NEW",9,Color.WHITE);n.setTag("keep_text_color");n.setGravity(Gravity.CENTER);'),
    ('TextView badge=text(numeric?(count>99?"99+":String.valueOf(count)):"●",numeric?10:11,Color.WHITE);', 'TextView badge=text(numeric?(count>99?"99+":String.valueOf(count)):"●",numeric?10:11,Color.WHITE);badge.setTag("keep_text_color");')
]:
    s = s.replace(old, new, 1)

if 'MushinGlass.applyToView(this,dock);' not in s:
    anchor = '''        boolean compact=compactUi();for(ItemModel m:list){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);ImageView iv=new ImageView(this);iv.setImageDrawable(iconFor(m));TextView tv=text(m.name,compact?9:10,Color.WHITE);tv.setGravity(Gravity.CENTER);tv.setSingleLine(true);int icon=compact?30:34;c.addView(iv,new LinearLayout.LayoutParams(Ui.dp(this,icon),Ui.dp(this,icon)));c.addView(tv,new LinearLayout.LayoutParams(-1,Ui.dp(this,compact?16:18)));c.setOnClickListener(v->requestLaunch(m));c.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("ドックから外しますか？").setMessage(m.name).setPositiveButton("外す",(d,w)->{store.deleteRecursive(m.id);refreshDock();}).setNegativeButton("キャンセル",null).show();return true;});dock.addView(c,new LinearLayout.LayoutParams(0,-1,1));}
    }

    private void toggleEditMode'''
    replacement = '''        boolean compact=compactUi();for(ItemModel m:list){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);ImageView iv=new ImageView(this);iv.setImageDrawable(iconFor(m));TextView tv=text(m.name,compact?9:10,Color.WHITE);tv.setGravity(Gravity.CENTER);tv.setSingleLine(true);int icon=compact?30:34;c.addView(iv,new LinearLayout.LayoutParams(Ui.dp(this,icon),Ui.dp(this,icon)));c.addView(tv,new LinearLayout.LayoutParams(-1,Ui.dp(this,compact?16:18)));c.setOnClickListener(v->requestLaunch(m));c.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("ドックから外しますか？").setMessage(m.name).setPositiveButton("外す",(d,w)->{store.deleteRecursive(m.id);refreshDock();}).setNegativeButton("キャンセル",null).show();return true;});dock.addView(c,new LinearLayout.LayoutParams(0,-1,1));}
        MushinGlass.applyToView(this,dock);
    }

    private void toggleEditMode'''
    if anchor not in s:
        raise SystemExit('refreshDock anchor missing')
    s = s.replace(anchor, replacement, 1)

p.write_text(s)
