package com.push.jzb;

import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Context.VIBRATOR_SERVICE;
import static com.push.jzb.PrivateConstants.HW_PUSH_BUZID;
import static com.push.jzb.service.MyService.SDK_ID;
import static com.push.jzb.service.MyService.USER_ID;
import static com.push.jzb.service.MyService.USER_SIG;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Process;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import com.heytap.msp.push.HeytapPushManager;
import com.huawei.agconnect.config.AGConnectServicesConfig;
import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.common.ApiException;
import com.huawei.hms.push.HmsMessaging;
import com.meizu.cloud.pushsdk.PushManager;
import com.meizu.cloud.pushsdk.util.MzSystemUtils;
import com.push.jzb.bean.OfflineMessageDispatcher;
import com.push.jzb.notification.NotificationClickReceiver;
import com.push.jzb.notification.NotifyBean;
import com.push.jzb.service.MyService;
import com.push.jzb.service.OPPOPushImpl;
import com.push.jzb.utils.BrandUtil;
import com.push.jzb.utils.L;
import com.push.jzb.utils.MobileInfoUtils;
import com.push.jzb.utils.NotificationUtil;
import com.push.jzb.utils.SecuritySharedPreference;
import com.push.jzb.utils.ThirdPushTokenMgr;
import com.tencent.imsdk.v2.V2TIMCallback;
import com.tencent.imsdk.v2.V2TIMConversation;
import com.tencent.imsdk.v2.V2TIMConversationListener;
import com.tencent.imsdk.v2.V2TIMManager;
import com.tencent.imsdk.v2.V2TIMValueCallback;
import com.uzmap.pkg.uzcore.UZWebView;
import com.uzmap.pkg.uzcore.uzmodule.UZModule;
import com.uzmap.pkg.uzcore.uzmodule.UZModuleContext;
import com.vivo.push.PushClient;
import com.xiaomi.mipush.sdk.MiPushClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * create???2022/5/9 10:42
 *
 * @author ykx
 * @version 1.0
 * @Description ???????????? ??????IM->????????????,??????,ViVo,xiaomi,oppo??????????????????
 */
public class OfflinePushModule extends UZModule {

    private Intent intent;
    public static String temporaryExt = "";
    private String userID;
    private String userSig;
    private String sdkID;
    private static UZModuleContext notifyContext;

    public OfflinePushModule(UZWebView webView) {
        super(webView);
    }

    /**
     * ??????????????????
     */
    public void jsmethod_setHuaWeiBadge(UZModuleContext moduleContext) {
        notifyContext = moduleContext;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            createNotificationChannel(moduleContext.getContext(), "yd_jzb");
        }
        createNotify(moduleContext.getContext(), "title", "content", new Random().nextInt(1000000), "ext123", false);
//        initTPNS(moduleContext.getContext());
//        ((Activity) moduleContext.getContext()).finish();
    }

    /**
     * ???????????????????????????????????????????????????????????????IM
     */
    public void jsmethod_initOffLinePush(UZModuleContext moduleContext) {
        // ??????ID??????
        PrivateConstants.HW_PUSH_BUZID = moduleContext.optInt("HW_PUSH_BUZID");
        PrivateConstants.XM_PUSH_BUZID = moduleContext.optInt("XM_PUSH_BUZID");
        PrivateConstants.MZ_PUSH_BUZID = moduleContext.optInt("MZ_PUSH_BUZID");
        PrivateConstants.VIVO_PUSH_BUZID = moduleContext.optInt("VIVO_PUSH_BUZID");
        PrivateConstants.OPPO_PUSH_BUZID = moduleContext.optInt("OPPO_PUSH_BUZID");
        // ????????????????????????????????????
        initActivityLifecycle(moduleContext.getContext().getApplicationContext());
        // ?????????????????????
        initPush(moduleContext.getContext());
    }

    /**
     * ??????????????????????????????
     *
     * @param moduleContext ????????????
     */
    public void jsmethod_initOffLineVibrator(UZModuleContext moduleContext) {
        // ??????????????????
        boolean order = moduleContext.optString("order").equals("use");
        jsmethod_setUserInfo(moduleContext);
        // ????????????????????????
        boolean isStart = BrandUtil.isBrandHuawei() || BrandUtil.isBrandXiaoMi() || BrandUtil.isBrandOppo();
        SecuritySharedPreference securitySharedPreference = new SecuritySharedPreference(moduleContext.getContext(), "openNotifyStatus", Context.MODE_PRIVATE);
        // ???????????????????????????
        boolean firstIgnoringBatteryOptimizations = securitySharedPreference.getBoolean("firstDoBackgroundPermission", true);
        if (firstIgnoringBatteryOptimizations && order && isStart) {
            securitySharedPreference.edit().putBoolean("firstDoBackgroundPermission", false).apply();
            new AlertDialog.Builder(moduleContext.getContext())
                    .setTitle("????????????")
                    .setMessage("????????????????????????????????????????????????????????????????????????????????????" + "\"??????????????????\"" + "??????")
                    .setPositiveButton("??????", (dialogInterface, i) -> {
                        if (BrandUtil.isBrandHuawei() || BrandUtil.isBrandOppo()) {
                            MobileInfoUtils.jumpStartInterface(moduleContext.getContext());
                        } else if (BrandUtil.isBrandXiaoMi()) {
                            isIgnoringBatteryOptimizations(moduleContext.getContext(), true);
                        }
                    })
                    .setNegativeButton("??????", (dialogInterface, i) -> {
                        JSONObject ret = new JSONObject();
                        try {
                            ret.put("btn", "yes");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        moduleContext.success(ret, true);
                    })
                    .create()
                    .show();
        } else {
            new AlertDialog.Builder(moduleContext.getContext())
                    .setTitle("????????????")
                    .setMessage("???????????????????????????")
                    .setPositiveButton("??????", (dialogInterface, i) -> {
                        if (order && BrandUtil.isBrandHuawei()) {
                            initTPNS(moduleContext.getContext());
                        } else if (order && BrandUtil.isBrandOppo()) {
                            initTPNS(moduleContext.getContext());
                        } else if (order && BrandUtil.isBrandXiaoMi()) {
                            // ??????????????????
                            // ??????????????????????????????????????????
                            initTPNS(moduleContext.getContext());
                        }
                        JSONObject ret = new JSONObject();
                        try {
                            ret.put("btn", "yes");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        moduleContext.success(ret, true);
                    })
                    .setNegativeButton("??????", (dialogInterface, i) -> dialogInterface.dismiss())
                    .create()
                    .show();
        }
    }

    /**
     * ??????????????????
     *
     * @param moduleContext ????????????
     */
    public void jsmethod_deleteChannel(UZModuleContext moduleContext) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String id = moduleContext.optString("channelKey");
            NotificationManager manager = (NotificationManager) moduleContext.getContext().getSystemService(NOTIFICATION_SERVICE);
            List<NotificationChannel> channels = manager.getNotificationChannels();
            for (int i = 0; i < channels.size(); i++) {
                NotificationChannel notificationChannel = channels.get(i);
                if (notificationChannel.getId() != null) {
                    L.e(notificationChannel.getId() + " = channelID");
                    if (notificationChannel.getId().equals(id)) {
                        manager.deleteNotificationChannel(notificationChannel.getId());
                        break;
                    }
                }
            }
        }
    }

    /**
     * ????????????
     *
     * @param moduleContext ????????????
     */
    public void jsmethod_createVibrator(UZModuleContext moduleContext) {
        String pattern = moduleContext.optString("pattern");
        Vibrator vibrator = (Vibrator) moduleContext.getContext().getSystemService(VIBRATOR_SERVICE);
        if (vibrator.hasVibrator()) {
            if (!TextUtils.isEmpty(pattern)) {
                String[] patterns = pattern.split(",");
                long[] longPatterns = new long[patterns.length];
                for (int i = 0; i < patterns.length; i++) {
                    longPatterns[i] = Long.parseLong(patterns[i]);
                }
                vibrator.vibrate(longPatterns, -1);
            } else {
                vibrator.vibrate(new long[]{100, 500, 100, 500}, -1);
            }
        }
    }

    /**
     * ???????????????????????????
     */
    public void jsmethod_notifyMessageCloseAll(UZModuleContext context) {
        // ?????????????????????
        NotificationManager manager = (NotificationManager) context.getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        int id = context.optInt("id");
        // ??????????????????????????????
        if (id == 0) {
            manager.cancelAll();
        } else {
            manager.cancel(id);
        }
    }

    /**
     * ????????????
     */
    public void jsmethod_createNotify(UZModuleContext moduleContext) {
        notifyContext = moduleContext;
        String title = moduleContext.optString("title");
        String content = moduleContext.optString("content");
        int id = moduleContext.optInt("messageID");
        String ext = moduleContext.optString("ext");
        boolean isAt = moduleContext.optBoolean("isAt");
        createNotify(moduleContext.getContext(), title, content, id, ext, isAt);
    }

    /**
     * ????????????
     *
     * @param title   ??????
     * @param content ??????
     * @param ext     ????????????
     * @param isAt    ?????????@???????????????
     */
    public void createNotify(Context context, String title, String content, int messageID, String ext, boolean isAt) {
        L.e("????????????");
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        // ????????????????????????????????????????????????,?????????new intent()????????????????????????
        Intent intent = new Intent(context, NotificationClickReceiver.class);
        Bundle bundle = new Bundle();
        bundle.putString("ext", ext);
        intent.putExtras(bundle);
        PendingIntent mPendingIntent = PendingIntent.getBroadcast(context,
                (int) (Math.random() * 1000000), intent, PendingIntent.FLAG_UPDATE_CURRENT);
        // ??????????????????,?????????????????????
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
        // ??????ID
        String channelId;
        channelId = isAt ? "high_special" : BrandUtil.getChannelId();
        L.e(channelId);
        // ?????????????????????
        mBuilder.setContentTitle(title)
                // ???????????????????????????
                .setContentText(content)
                // ???????????????????????????
                .setContentIntent(mPendingIntent)
                // ???????????????????????????????????????????????????????????????????????????????????????
                .setWhen(System.currentTimeMillis())
                // ????????????????????????
                .setPriority(Notification.PRIORITY_HIGH)
                // ????????????
                .setChannelId(channelId)
                // ????????????????????????????????????????????????????????????????????????
                .setAutoCancel(true)
                // ?????????????????????????????????
                .setDefaults(Notification.DEFAULT_VIBRATE)
                // ???????????????ICON(??????????????????)
                .setSmallIcon(R.drawable.jzbpush_logo);
        if (!TextUtils.isEmpty(ext)) {
            mNotificationManager.notify(messageID, mBuilder.build());
            // ?????????????????????????????????????????? ??????????????????
            if (isAt) {
                Vibrator vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(new long[]{0, 1000}, -1);
                }
            }
        }
    }

    /**
     * ??????????????????????????????
     *
     * @param bean ????????????
     */
    public static void handlerMessageCallBack(NotifyBean bean) {
        if (bean != null && notifyContext != null) {
            L.e("????????????," + bean.getExt());
            JSONObject ret = new JSONObject();
            try {
                ret.put("conversationId", bean.getExt());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            notifyContext.success(ret);
        }
    }

    /**
     * ?????????intent??????????????????????????? ????????????????????????
     */
    public void jsmethod_getPushData(UZModuleContext moduleContext) {
        Activity activity = (Activity) moduleContext.getContext();
        String ext;
        if (intent == null) {
            // ?????????????????????????????????????????????
            intent = activity.getIntent();
            ext = OfflineMessageDispatcher.parseOfflineMessage(intent);
        } else {
            // ???n?????????intent?????????????????????????????????????????????
            // ???????????????????????????
            ext = "";
            temporaryExt = "";
        }
        if (TextUtils.isEmpty(ext)) {
            return;
        }
        JSONObject ret = new JSONObject();
        try {
            ret.put("conversationId", ext);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        moduleContext.success(ret, true);
    }

    /**
     * ???????????????app??????
     */
    public void jsmethod_setUserInfo(UZModuleContext moduleContext) {
        userID = moduleContext.optString(USER_ID);
        userSig = moduleContext.optString(USER_SIG);
        sdkID = moduleContext.optString(SDK_ID);
    }

    /**
     * ??????????????????
     *
     * @param context   ?????????
     * @param channelID ??????ID
     */
    private void createNotificationChannel(Context context, String channelID) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(channelID, "????????????", NotificationManager.IMPORTANCE_HIGH);
            channel.setVibrationPattern(new long[]{100, 500, 100, 500});
            channel.enableVibration(true);
//            channel.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.bell), Notification.AUDIO_ATTRIBUTES_DEFAULT);
            manager.createNotificationChannel(channel);

            // ????????????????????????
            manager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            channel = new NotificationChannel("high_special", "????????????", NotificationManager.IMPORTANCE_HIGH);
            channel.setVibrationPattern(new long[]{0});
            channel.enableVibration(false);
//            channel.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.bell), Notification.AUDIO_ATTRIBUTES_DEFAULT);
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * ????????????????????????
     */
    private boolean isAllowed(Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        try {
            int op = 10021;
            Method method = ops.getClass().getMethod("checkOpNoThrow", int.class, int.class, String.class);
            Integer result = (Integer) method.invoke(ops, op, Process.myUid(), context.getPackageName());
            if (result == null) {
                return false;
            }
            return result == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            L.e("not support");
        }
        return false;
    }

    /**
     * ??????????????????????????????
     */
    private void openNotifyPermission(Context context) {
        SecuritySharedPreference securitySharedPreference = new SecuritySharedPreference(context, "openNotifyStatus", Context.MODE_PRIVATE);
        if (!NotificationUtil.isNotifyEnabled(context)) {
            boolean firstGetNotifyStatus = securitySharedPreference.getBoolean("firstGetNotifyStatus", true);
            if (firstGetNotifyStatus) {
                securitySharedPreference.edit().putBoolean("firstGetNotifyStatus", false).apply();
                new AlertDialog.Builder(context)
                        .setTitle("???????????????????????????")
                        .setMessage("????????????????????????????????????????????????????????????")
                        .setPositiveButton("??????", (dialogInterface, i) -> {
                            Intent localIntent = new Intent();
                            // ?????????????????????????????????????????????
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {//8.0?????????
                                localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                localIntent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
                                localIntent.setData(Uri.fromParts("package", context.getPackageName(), null));
                            } else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                // 5.0?????????8.0??????
                                localIntent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                                localIntent.putExtra("app_package", context.getPackageName());
                                localIntent.putExtra("app_uid", context.getApplicationInfo().uid);
                            } else if (android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
                                // 4.4
                                localIntent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                localIntent.addCategory(Intent.CATEGORY_DEFAULT);
                                localIntent.setData(Uri.parse("package:" + context.getPackageName()));
                            }
                            startActivity(localIntent);
                        })
                        .setNegativeButton("??????", (dialogInterface, i) -> dialogInterface.dismiss())
                        .create()
                        .show();
            }
        }
    }

    /**
     * ????????????????????????????????????
     */
    private void initActivityLifecycle(Context appContext) {
        if (appContext instanceof Application) {
            ((Application) appContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                private int foregroundActivities = 1;
                private boolean isChangingConfiguration;

                private final V2TIMConversationListener unreadListener = new V2TIMConversationListener() {
                    @Override
                    public void onTotalUnreadMessageCountChanged(long totalUnreadCount) {
                        L.e("??????????????????");
                    }

                    @Override
                    public void onConversationChanged(List<V2TIMConversation> conversationList) {
                        super.onConversationChanged(conversationList);
                        for (int i = 0; i < conversationList.size(); i++) {
                            String lastMessageExt = Arrays.toString(conversationList.get(i).getLastMessage().getOfflinePushInfo().getExt());
                            L.e("lastMessageExt = " + lastMessageExt);
                        }
                    }
                };

                @Override
                public void onActivityCreated(Activity activity, Bundle bundle) {
                    if (bundle != null) { // ???bundle??????????????????????????????
                        L.e("??????????????????");
                    }
                }

                @Override
                public void onActivityStarted(Activity activity) {
                    foregroundActivities++;
                    if (foregroundActivities == 1 && !isChangingConfiguration) {
                        // ??????????????????
                        L.e("application enter foreground");
                        V2TIMManager.getOfflinePushManager().doForeground(new V2TIMCallback() {
                            @Override
                            public void onError(int code, String desc) {
                                L.e("doBackground err = " + code + ", desc = " + desc);
                            }

                            @Override
                            public void onSuccess() {
                                L.e("success");
                            }
                        });
                        V2TIMManager.getConversationManager().removeConversationListener(unreadListener);
                    }
                    isChangingConfiguration = false;
                }

                @Override
                public void onActivityResumed(Activity activity) {

                }

                @Override
                public void onActivityPaused(Activity activity) {

                }

                @Override
                public void onActivityStopped(Activity activity) {
                    foregroundActivities--;
                    if (foregroundActivities == 0) {
                        // ??????????????????
                        L.e("application enter background");
                        V2TIMManager.getConversationManager().getTotalUnreadMessageCount(new V2TIMValueCallback<Long>() {
                            @Override
                            public void onSuccess(Long aLong) {
                                int totalCount = aLong.intValue();
//                                V2TIMManager.getOfflinePushManager().doBackground(totalCount, new V2TIMCallback() {
//                                    @Override
//                                    public void onError(int code, String desc) {
//                                        L.e("doBackground err = " +
//                                                code + ", desc = " + desc);
//                                    }
//
//                                    @Override
//                                    public void onSuccess() {
//                                        L.e("success");
//                                    }
//                                });
                            }

                            @Override
                            public void onError(int code, String desc) {
                                L.e("doBackground err = " + code + ", desc = " + desc);
                            }
                        });
                        V2TIMManager.getConversationManager().addConversationListener(unreadListener);
                    }
                    isChangingConfiguration = activity.isChangingConfigurations();
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    L.e("???????????????");
//                    initTPNS(activity);
                }
            });
            V2TIMManager.getOfflinePushManager().doForeground(new V2TIMCallback() {
                @Override
                public void onError(int code, String desc) {
                    L.e("doBackground err = " + code + ", desc = " + desc);
                }

                @Override
                public void onSuccess() {
                    L.e("success");
                }
            });
        }
    }

    /**
     * ???????????????????????????
     */
    private void initPush(Context context) {
        if (context != null) {
            Intent intentOne = new Intent(context, MyService.class);
            context.stopService(intentOne);
            if (BrandUtil.isBrandXiaoMi()) {
                openNotifyPermission(context);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    createNotificationChannel(context, "high_system");
                }
                L.e("supportBackPermission = " + isAllowed(context));
                // ??????????????????
                MiPushClient.registerPush(context, PrivateConstants.XM_PUSH_APPID, PrivateConstants.XM_PUSH_APPKEY);
            } else if (BrandUtil.isBrandHuawei()) {
                openNotifyPermission(context);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    createNotificationChannel(context, "yd_jzb");
                }
                // ???????????????????????????????????????Push???????????????????????????
                HmsMessaging.getInstance(context).turnOnPush().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        L.e("huawei turnOnPush Complete");
                    } else {
                        L.e("huawei turnOnPush failed: ret=" + task.getException().getMessage());
                    }
                });
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            // read from agconnect-services.json
                            String appId = AGConnectServicesConfig.fromContext(context).getString("client/app_id");
                            String token = HmsInstanceId.getInstance(context).getToken(appId, "HCM");
                            L.e("huawei get token:" + token);
                            if (!TextUtils.isEmpty(token)) {
                                // ??? token ????????????????????? setOfflinePushConfig ???????????? IMSDK
                                ThirdPushTokenMgr.getInstance().setPushTokenToTIM(HW_PUSH_BUZID, token);
                            }
                        } catch (ApiException e) {
                            L.e("huawei get token failed, " + e);
                        }
                    }
                }.start();
            } else if (MzSystemUtils.isBrandMeizu(context)) {
                openNotifyPermission(context);
                // ??????????????????
                PushManager.register(context, PrivateConstants.MZ_PUSH_APPID, PrivateConstants.MZ_PUSH_APPKEY);
            } else if (BrandUtil.isBrandOppo()) {
                try {
                    boolean support = HeytapPushManager.isSupportPush(context);
                    if (support) {
                        // OPPO ???????????????????????????????????????
                        HeytapPushManager.requestNotificationPermission();
                        // oppo????????????
                        OPPOPushImpl oppo = new OPPOPushImpl();
                        oppo.createNotificationChannel(context);
                        // oppo??????????????????????????????????????????init(...)????????????????????????????????????
//                        HeytapPushManager.init(context, false);
                        HeytapPushManager.register(context, PrivateConstants.OPPO_PUSH_APPKEY, PrivateConstants.OPPO_PUSH_APPSECRET, oppo);
                        L.e("register oppo");
                    }
                } catch (NoClassDefFoundError | NullPointerException | IllegalArgumentException e) {
                    L.e("oppo??????????????????");
                }
            } else if (BrandUtil.isBrandVivo()) {
                openNotifyPermission(context);
                PushClient.getInstance(context).initialize();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    createNotificationChannel(context, "high_system");
                }
                L.e("vivo support push: " + PushClient.getInstance(context).isSupport());
                PushClient.getInstance(context).turnOnPush(state -> {
                    if (state == 0) {
                        String regId = PushClient.getInstance(context).getRegId();
                        L.e(regId + " = vivo");
                        ThirdPushTokenMgr.getInstance().setPushTokenToTIM(PrivateConstants.VIVO_PUSH_BUZID, regId);
                    } else {
                        // ??????vivo?????????????????????state = 101 ?????????vivo???????????????????????????vivo??????????????????https://dev.vivo.com.cn/documentCenter/doc/156
                        L.e("vivopush open vivo push fail state = " + state);
                    }
                });
            } else {
                openNotifyPermission(context);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    createNotificationChannel(context, "high_system");
                }
                L.e("supportBackPermission = " + isAllowed(context));
                // ??????????????????
                MiPushClient.registerPush(context, PrivateConstants.XM_PUSH_APPID, PrivateConstants.XM_PUSH_APPKEY);
            }
        }
    }

    /**
     * ???TPNS??????????????????????????????
     *
     * @param context ?????????
     */
    private void initTPNS(Context context) {
        Intent intentOne = new Intent(context, MyService.class);
        intentOne.putExtra("userid", userID);
        intentOne.putExtra("userSig", userSig);
        intentOne.putExtra("sdkAppid", sdkID);
        context.stopService(intentOne);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intentOne);
        } else {
            context.startService(intentOne);
        }
//        L.e(V2TIMManager.getInstance().getVersion());
//        L.e(V2TIMManager.getInstance().getLoginUser());
//        XGPushManager.registerPush(context, new XGIOperateCallback() {
//            @Override
//            public void onSuccess(Object data, int flag) {
//                // token?????????????????????????????????????????????
//                L.e("?????????????????????token??????" + data);
//            }
//
//            @Override
//            public void onFail(Object data, int errCode, String msg) {
//                L.e("???????????????????????????" + errCode + ",???????????????" + msg);
//            }
//        });
    }

    /**
     * ????????????????????????
     */
    private boolean isIgnoringBatteryOptimizations(Context context, boolean requestPermission) {
        boolean isIgnoring = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
            }
            if (!isIgnoring && requestPermission) {
                requestIgnoreBatteryOptimizations(context);
            }
        }
        return isIgnoring;
    }

    /**
     * ??????????????????
     *
     * @param context ?????????
     */
    public void requestIgnoreBatteryOptimizations(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent;
                intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                startActivity(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
