package com.rubird.muzeipinterest;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;
import com.pinterest.android.pdk.PDKCallback;
import com.pinterest.android.pdk.PDKClient;
import com.pinterest.android.pdk.PDKPin;
import com.pinterest.android.pdk.PDKResponse;

import java.util.HashMap;
import java.util.Random;

/**
 * Created by varunoberoi on 20/03/15.
 */
public class PinterestArtSource extends RemoteMuzeiArtSource {

    private static final String TAG = "PinterestMuzei";
    private static final String SOURCE_NAME = "PinterestArtSource";

    public static final String ACTION_REFRESH = "com.rubird.muzeipinterest.REFRESH";
    private static final int PAGE_SIZE = 100;
    private HashMap<String, String> params = new HashMap<>();

    public PinterestArtSource() {
        super(SOURCE_NAME);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setUserCommands(BUILTIN_COMMAND_ID_NEXT_ARTWORK);
    }


    @Override
    protected void onTryUpdate(final int reason) throws RetryException {
        final SharedPreferences settings = getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
        final long rotateTimeMillis = getRotateTimeMillisFromSetting(settings.getFloat(PreferenceKeys.FREQUENCY, 2));

        String user, board;

        user = settings.getString(PreferenceKeys.PINTEREST_USER, "");
        board = settings.getString(PreferenceKeys.BOARD, "");

        // Don't execute main code until user & board are set
        if(user.isEmpty() || board.isEmpty()){
            if (BuildConfig.DEBUG) Log.d(TAG, "Refresh avoided: no user or/and board");
            if(rotateTimeMillis != -1)
                scheduleUpdate(System.currentTimeMillis() + rotateTimeMillis);
            return;
        }

        // Check if we cancel the update due to WIFI connection
        if (settings.getBoolean(PreferenceKeys.WIFI_ONLY, false) && !Utils.isWifiConnected(this)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Refresh avoided: no wifi");
            if(rotateTimeMillis != -1)
                scheduleUpdate(System.currentTimeMillis() + rotateTimeMillis);
            return;
        }

        String BOARD_PINS_URL = "boards/" + board + "/pins/";
        params.put("limit", PAGE_SIZE+"");
        params.put("fields", "id,link,image,original_link,note");

        final String currentToken = (getCurrentArtwork() != null) ? getCurrentArtwork().getToken() : null;
        PDKClient client = PDKClient.configureInstance(this, Config.PINTEREST_APP_ID);
        client.setDebugMode(true);
        Log.d(TAG, "Making API Call");
        client.getPath(BOARD_PINS_URL, params, new PDKCallback(){
            @Override
            public void onSuccess(PDKResponse response) {
                Random random = new Random();
                int pinNum = random.nextInt(PAGE_SIZE);

                // High probability of choosing in latest 25 pins
                if(random.nextInt(5) <= 3)
                    pinNum = random.nextInt(25);

                Log.d(TAG, "onSuccess " + response.getPinList().size() + " & picked " + pinNum);

                String token;
                PDKPin pin;
                while (true) {
                    pin = response.getPinList().get(pinNum);
                    token = pin.getUid();
                    if (response.getPinList().size() <= 1 || !TextUtils.equals(token, currentToken)) {
                        break;
                    }
                }
                if(pin != null) {
                    String by = pin.getMetadata();

                    publishArtwork(new Artwork.Builder()
                            .title(pin.getNote())
                            .byline(by)
                            .imageUri(Uri.parse(pin.getImageUrl()))
                            .token(token)
                            .viewIntent(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse(pin.getLink())))
                            .build());

                    scheduleUpdate(System.currentTimeMillis() + rotateTimeMillis);
                }
            }
        });
    }

    long getRotateTimeMillisFromSetting(float frequencySetting){
        return (long) (frequencySetting * 60 * 60 * 1000);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        if (ACTION_REFRESH.equals(action)) {
            scheduleUpdate(System.currentTimeMillis() + 1000);
            return;
        }
        super.onHandleIntent(intent);
    }
}
