package com.thangglobal.warehouse;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.webkit.*;
import android.widget.*;
import android.text.InputType;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String BASE = "https://thangglobal.com";
    private LinearLayout root, panel, profileList;
    private WebView web;
    private EditText labelInput, emailInput, passInput, searchInput;
    private TextView status;
    private JSONArray profiles = new JSONArray();
    private int pendingLogin = -1;
    private boolean warehousePending = false;
    private android.content.SharedPreferences securePrefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(16,7,7));
        getWindow().setNavigationBarColor(Color.rgb(16,7,7));
        try {
            MasterKey key = new MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            securePrefs = EncryptedSharedPreferences.create(this, "secure_profiles", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            profiles = new JSONArray(securePrefs.getString("profiles", "[]"));
        } catch (Exception e) {
            Toast.makeText(this, "Secure storage unavailable on this device.", Toast.LENGTH_LONG).show();
        }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(16,7,7));
        setContentView(root);
        header();
        buildHome();
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(12,10,12,10); return t;
    }
    private Button button(String s, Runnable action) {
        Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE);
        b.setAllCaps(false); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(126,28,28)));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private EditText field(String hint, boolean secret) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint);
        e.setHintTextColor(Color.LTGRAY); e.setTextColor(Color.WHITE);
        e.setPadding(14,8,14,8);
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(160,75,55)));
        return e;
    }
    private void header() {
        LinearLayout h = new LinearLayout(this); h.setPadding(8,8,8,8);
        h.setGravity(Gravity.CENTER_VERTICAL); h.setBackgroundColor(Color.rgb(35,10,10));
        TextView title = text("THANG GLOBAL",18,Color.rgb(238,190,104),true);
        h.addView(title, new LinearLayout.LayoutParams(0,-2,1));
        h.addView(button("HOME", this::buildHome));
        h.addView(button("WAREHOUSE", this::openWarehouse));
        root.addView(h);
    }
    private void buildHome() {
        if (root.getChildCount() > 1) root.removeViews(1, root.getChildCount()-1);
        web = null;
        ScrollView scroll = new ScrollView(this);
        panel = new LinearLayout(this); panel.setPadding(16,12,16,24); panel.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(panel); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        panel.addView(text("WAREHOUSE MANAGER",22,Color.WHITE,true));
        panel.addView(text("Save your Thang Global accounts for quicker access.",14,Color.LTGRAY,false));
        labelInput = field("Profile name (e.g. Main)",false);
        emailInput = field("Email address",false);
        passInput = field("Password (encrypted on this phone)",true);
        panel.addView(labelInput); panel.addView(emailInput); panel.addView(passInput);
        panel.addView(button("SAVE ACCOUNT", this::saveProfile));
        panel.addView(text("SAVED ACCOUNTS",18,Color.rgb(238,190,104),true));
        profileList = new LinearLayout(this); profileList.setOrientation(LinearLayout.VERTICAL); panel.addView(profileList);
        refreshProfiles();
        panel.addView(button("OPEN THANG GLOBAL", () -> load(BASE + "/profile")));
        panel.addView(button("OPEN WAREHOUSE", this::openWarehouse));
        panel.addView(button("CHECK ALL ACCOUNTS", () ->
            setStatus("Sequential account scanning will be enabled after the authenticated warehouse data format is verified.")));
        status = text("Ready. Saved credentials are encrypted on this device.",13,Color.LTGRAY,false);
        panel.addView(status);
    }
    private void saveProfile() {
        String label = labelInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String pass = passInput.getText().toString();
        if (securePrefs == null) { setStatus("Secure storage could not be opened; account was not saved."); return; }
        if (label.isEmpty() || email.isEmpty() || pass.isEmpty()) { setStatus("Enter a profile name, email and password."); return; }
        try {
            JSONObject p = new JSONObject(); p.put("label",label); p.put("email",email); p.put("password",pass);
            profiles.put(p); securePrefs.edit().putString("profiles",profiles.toString()).apply();
            labelInput.setText(""); emailInput.setText(""); passInput.setText("");
            refreshProfiles(); setStatus("Account saved securely.");
        } catch(Exception e) { setStatus("Could not save this profile."); }
    }
    private void refreshProfiles() {
        if (profileList == null) return;
        profileList.removeAllViews();
        for (int i=0;i<profiles.length();i++) {
            final int index=i;
            try {
                JSONObject p=profiles.getJSONObject(i);
                LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(text(p.optString("label"),16,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
                row.addView(button("LOGIN",()->loginProfile(index)));
                row.addView(button("REMOVE",()->removeProfile(index)));
                profileList.addView(row);
            } catch(Exception ignored) {}
        }
        if(profiles.length()==0) profileList.addView(text("No saved accounts yet.",14,Color.LTGRAY,false));
    }
    private void removeProfile(int i) {
        JSONArray n=new JSONArray();
        for(int j=0;j<profiles.length();j++) if(j!=i) n.put(profiles.optJSONObject(j));
        profiles=n;
        if (securePrefs != null) securePrefs.edit().putString("profiles",profiles.toString()).apply();
        refreshProfiles();
    }
    private void showBrowser(String startUrl) {
        if (root.getChildCount() > 1) root.removeViews(1,root.getChildCount()-1);
        LinearLayout area=new LinearLayout(this); area.setOrientation(LinearLayout.VERTICAL);
        LinearLayout controls=new LinearLayout(this); controls.setPadding(6,4,6,4);
        searchInput=field("Search warehouse by item name...",false);
        controls.addView(searchInput,new LinearLayout.LayoutParams(0,-2,1));
        controls.addView(button("SEARCH",this::searchWarehouse));
        area.addView(controls);
        web=new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setLoadsImagesAutomatically(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url) {
                if(pendingLogin>=0 && url.contains("/auth/login")) injectLogin();
                if(warehousePending && url.contains("/profile")) {
                    warehousePending=false;
                    view.evaluateJavascript("(function(){const els=[...document.querySelectorAll('button,a,[role=button],div')];const e=els.find(x=>(x.innerText||'').trim().toLowerCase()==='warehouse');if(e)e.click();return !!e;})()", null);
                }
            }
        });
        area.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        web.loadUrl(startUrl);
    }
    private void load(String url) { showBrowser(url); }
    private void loginProfile(int i) {
        pendingLogin=i; showBrowser(BASE+"/auth/login?returnTo=/profile");
    }
    private void injectLogin() {
        try {
            JSONObject p=profiles.getJSONObject(pendingLogin);
            String email=JSONObject.quote(p.optString("email"));
            String pass=JSONObject.quote(p.optString("password"));
            String js="(function(){const ins=[...document.querySelectorAll('input')];"+
                "const em=ins.find(x=>x.type==='email'||/email/i.test(x.placeholder+' '+x.name));"+
                "const pw=ins.find(x=>x.type==='password');"+
                "if(!em||!pw)return 'fields-not-found';"+
                "const set=(e,v)=>{let s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));};"+
                "set(em,"+email+");set(pw,"+pass+");"+
                "const b=[...document.querySelectorAll('button')].find(x=>/login/i.test(x.innerText));if(b)b.click();return 'submitted';})()";
            web.evaluateJavascript(js, result -> { pendingLogin=-1; });
        } catch(Exception e) { pendingLogin=-1; }
    }
    private void openWarehouse() {
        warehousePending=true;
        if(web==null) showBrowser(BASE+"/profile");
        else web.loadUrl(BASE+"/profile");
    }
    private void searchWarehouse() {
        if(web==null) { setStatus("Open the warehouse first."); return; }
        String q=searchInput.getText().toString();
        if(q.trim().isEmpty()) return;
        String quoted=JSONObject.quote(q);
        String js="(function(){const e=[...document.querySelectorAll('input')].find(x=>/search by name|descr/i.test(x.placeholder||''));"+
            "if(!e)return 'warehouse-search-field-not-found';"+
            "const s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;s.call(e,"+quoted+");"+
            "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));return 'search-entered';})()";
        web.evaluateJavascript(js, r -> setStatus("Search text sent to the warehouse page. Use the website's own field if it was not detected."));
    }
    private void setStatus(String s) { if(status!=null) status.setText(s); }
    @Override public void onBackPressed() {
        if(web!=null && web.canGoBack()) web.goBack(); else buildHome();
    }
}
