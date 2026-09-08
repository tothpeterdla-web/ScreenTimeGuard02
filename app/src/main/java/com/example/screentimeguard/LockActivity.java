package com.example.screentimeguard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class LockActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PolicyUtils.prepareRestrictedLockTask(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int p=dp(24); root.setPadding(p,p,p,p);

        TextView title=new TextView(this);
        title.setText("Daily screen-time limit reached"); title.setTextSize(28); title.setGravity(Gravity.CENTER);
        root.addView(title,full());

        TextView body=new TextView(this);
        body.setText("\nNotifications remain visible. System apps are available, while browsers, app stores and third-party app activities are blocked until midnight.\n");
        body.setTextSize(17); body.setGravity(Gravity.CENTER); root.addView(body,full());

        Button home=new Button(this); home.setText("Open system apps");
        home.setOnClickListener(v->{ try{PolicyUtils.openHome(this);}catch(Exception e){Toast.makeText(this,"Could not open Home",Toast.LENGTH_SHORT).show();}});
        root.addView(home,full());

        Button call=new Button(this); call.setText("Open phone / make a call");
        call.setOnClickListener(v->{ try{PolicyUtils.openDialer(this);}catch(Exception e){Toast.makeText(this,"No dialer available",Toast.LENGTH_SHORT).show();}});
        root.addView(call,full());

        Button unlock=new Button(this); unlock.setText("Guardian unlock until midnight"); unlock.setOnClickListener(v->askPin()); root.addView(unlock,full());
        setContentView(root);
    }

    @Override protected void onResume(){ super.onResume(); try{
        DevicePolicyManager d=(DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
        if(d!=null && d.isLockTaskPermitted(getPackageName())) startLockTask();
    }catch(Exception ignored){} }

    private void askPin(){
        EditText pin=new EditText(this); pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("Guardian PIN").setView(pin)
                .setPositiveButton("Unlock",(d,w)->{
                    if(PinStore.verify(this,pin.getText().toString())){ Prefs.unlockUntilMidnight(this); try{stopLockTask();}catch(Exception ignored){} finish(); }
                    else Toast.makeText(this,"Wrong PIN",Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Cancel",null).show();
    }

    @Override public void onBackPressed() { }
    private LinearLayout.LayoutParams full(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(0,dp(6),0,dp(6)); return p; }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
