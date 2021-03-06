package tran.com.android.tapla.gamecenter.market.receiver;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

import tran.com.android.tapla.gamecenter.R;
import tran.com.android.tapla.gamecenter.datauiapi.PushManager;
import tran.com.android.tapla.gamecenter.datauiapi.bean.PushInfoItem;
import tran.com.android.tapla.gamecenter.market.activity.module.AppListActivity;
import tran.com.android.tapla.gamecenter.market.activity.module.MarketDetailActivity;
import tran.com.android.tapla.gamecenter.market.db.PushDataAdapter;
import tran.com.android.tapla.gamecenter.market.model.DownloadData;
import tran.com.android.tapla.gamecenter.market.util.Globals;
import tran.com.android.tapla.gamecenter.market.util.Log;
import tran.com.android.tapla.gamecenter.market.util.SystemUtils;
import tran.com.android.tapla.gamecenter.market.util.TimeUtils;

/**
 * Created by suwei.tang on 2017/9/2.
 */

public class PushManagerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            long currentTime = System.currentTimeMillis();
            if("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())){
                AlarmManager manager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
                Intent pushIntent = new Intent(PushManager.PUSHACTION);
                PendingIntent pushPendingIntent = PendingIntent.getBroadcast(context,0, pushIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                manager.setRepeating(AlarmManager.RTC_WAKEUP, currentTime+(2*1000), AlarmManager.INTERVAL_HOUR * 3, pushPendingIntent);
            }

            SharedPreferences sp1 = context.getSharedPreferences(Globals.SHARED_DATA_UPDATE, context.MODE_APPEND);
            String updatePushTime = sp1.getString(Globals.SEARCH_PUSH_DATA_CACHE_UPDATETIME, "0");

            //与服务器保持心跳，隔一天去服务器请求一次数据
            if(!updatePushTime.equals(TimeUtils.getStringDateShort())){
                Thread thread = new Thread(new PushManager(context));
                thread.start();
            }

            //游戏中心不在前台，就推送消息
            if(!SystemUtils.isForeground(context)){
                PushDataAdapter ldb = new PushDataAdapter(context);
                ldb.open();
                List<PushInfoItem> pushDatas = ldb.getPushDatas(context, currentTime);

                if(pushDatas!=null && pushDatas.size()>0){
                    //推送消息
                    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    for(int i=0; i<pushDatas.size(); i++){
                        PushInfoItem item = pushDatas.get(i);
                        if(item.getAction() == 1){ //跳转到游戏详情
                            DownloadData downloadData = new DownloadData();
                            downloadData.setPackageName(item.getTo());
                            Intent detailIntent = new Intent(context,
                                    MarketDetailActivity.class);
                            detailIntent.putExtra("downloaddata",downloadData);
                            PendingIntent pendingDetailIntent = PendingIntent.getActivity(context,item.getId(), detailIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                            postNotification(context, pendingDetailIntent,notificationManager,item);
                            //一旦推送成功，则删掉数据
                            ldb.delete(item.getId());
                        }else if(item.getAction() == 2){ //跳转到专题
                            Intent specialIntent = new Intent(context, AppListActivity.class);
                            specialIntent.putExtra(AppListActivity.OPEN_TYPE, AppListActivity.TYPE_SPECIAL);
                            specialIntent.putExtra(AppListActivity.SPECIAL_ID,pushDatas.get(i).getTo());
                            PendingIntent pendingSpecialIntent = PendingIntent.getActivity(context,item.getId(), specialIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                            postNotification(context, pendingSpecialIntent, notificationManager,item);
                            //一旦推送成功，则删掉数据
                            ldb.delete(item.getId());
                        }
                    }
                }
                ldb.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void postNotification(Context context, PendingIntent pendingIntent, NotificationManager manager, PushInfoItem item) {
        NotificationCompat.Builder compat = new NotificationCompat.Builder(context);
        compat.setContentTitle(item.getTheme());
        compat.setContentText(item.getSummary());
        compat.setSmallIcon(R.drawable.ic_launcher);
        compat.setContentIntent(pendingIntent);
        compat.build();
        compat.setAutoCancel(true);
        Notification notification = compat.build();
        manager.notify(item.getId(), notification);
    }
}
