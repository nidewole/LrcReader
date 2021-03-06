package com.wyq.lrcreader.activity;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.tencent.mm.sdk.openapi.IWXAPI;
import com.tencent.mm.sdk.openapi.SendMessageToWX;
import com.tencent.mm.sdk.openapi.WXAPIFactory;
import com.wyq.lrcreader.R;
import com.wyq.lrcreader.cache.DiskLruCacheUtil;
import com.wyq.lrcreader.model.LrcInfo;
import com.wyq.lrcreader.model.Song;
import com.wyq.lrcreader.share.WeChatShare;
import com.wyq.lrcreader.utils.BitmapUtil;
import com.wyq.lrcreader.utils.LogUtil;
import com.wyq.lrcreader.utils.LrcParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Created by Uni.W on 2016/8/30.
 */
public class LrcActivity extends Activity implements View.OnTouchListener, View.OnClickListener {

    private TextView lrcView;
    private ScrollView scrollView;
    private RelativeLayout relativeLayout;
    private LinearLayout setMenuLayout;

    private Button menuBackBt, menuHideBt, menuLikeBt, menuPlainBt;
    private Button menuQzoneBt, menuWeiboBt, menuWechatBt, menuMomentsBt;
    private SeekBar menuTextSizeSeek;

    private String lrcText, artist, songName;
    private Bitmap albumCover;
    private Song song;
    private LrcInfo lrcInfo;
    float startTextSize = 0;
    private LrcParserThread lrcParserThread;
    private BlurImageThread blurImageThread;

    private long firstClickTime = 0;
    private float startX = 0, endX = 0, startY = 0, endY = 0;
    private boolean isMenuVisiblity = false;
    private boolean isLike = false;
    private boolean isPlain = false;
    private Animation showAnimation, hideAnimation;

    private DiskLruCacheUtil diskLruCacheUtil;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    lrcView.setText(msg.obj.toString());
                    break;
                case 1:
                    scrollView.setBackground(new BitmapDrawable((Bitmap) msg.obj));
                    setMenuLayout.setBackground(new BitmapDrawable(BitmapUtil.getTransparentBitmap((Bitmap) msg.obj, 100)));
//                    relativeLayout.setBackground(new BitmapDrawable((Bitmap) msg.obj));
//                        backImage.setAlpha(0.5f);
//                        backImage.setImageBitmap((Bitmap) msg.obj);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lrc_view);

        scrollView = (ScrollView) findViewById(R.id.activity_lrc_view_scrollview);
        lrcView = (TextView) findViewById(R.id.activity_lrc_view_text);
        lrcView.setOnTouchListener(this);

        relativeLayout = (RelativeLayout) findViewById(R.id.activity_lrc_view_relativelayout);
        setMenuLayout = (LinearLayout) findViewById(R.id.activity_lrc_view_setmenu);

        initMenu();

        artist = getIntent().getExtras().getString("artist");
        lrcText = getIntent().getExtras().getString("lrcText");
        songName = getIntent().getExtras().getString("songName");
        albumCover = BitmapUtil.convertStringToIcon(getIntent().getExtras().getString("albumCover"));
        isLike = getIntent().getExtras().getBoolean("isLike");
        if (isLike) {
            menuLikeBt.setBackground(getResources().getDrawable(R.drawable.like_1_red));
        }

        if (artist != null && lrcText != null && albumCover != null) {
            song = new Song();
            song.setArtist(artist);
            song.setLrc(lrcText);
            song.setSongName(songName);
            song.setAlbumCover(albumCover);
        } else {
            finish();
        }

        diskLruCacheUtil = DiskLruCacheUtil.getInstance(this, "song");

//        handler.obtainMessage(0, new LrcParser().parserAll(lrcText)).sendToTarget();
        if (lrcText != null && lrcParserThread == null) {
            lrcParserThread = new LrcParserThread();
            lrcParserThread.start();
        }
        if (albumCover != null && blurImageThread == null) {
            blurImageThread = new BlurImageThread();
            blurImageThread.start();
        }

    }

    private void initMenu() {
        menuBackBt = (Button) findViewById(R.id.menu_lrc_view_back_bt);
        menuHideBt = (Button) findViewById(R.id.menu_lrc_view_hide_bt);
        menuLikeBt = (Button) findViewById(R.id.menu_lrc_view_like_bt);
        menuPlainBt = (Button) findViewById(R.id.menu_lrc_view_plain_bt);
        menuQzoneBt = (Button) findViewById(R.id.menu_lrc_view_qzone_bt);
        menuWeiboBt = (Button) findViewById(R.id.menu_lrc_view_weibo_bt);
        menuWechatBt = (Button) findViewById(R.id.menu_lrc_view_wechat_bt);
        menuMomentsBt = (Button) findViewById(R.id.menu_lrc_view_moments_bt);
        menuBackBt.setOnClickListener(this);
        menuHideBt.setOnClickListener(this);
        menuLikeBt.setOnClickListener(this);
        menuPlainBt.setOnClickListener(this);
        menuQzoneBt.setOnClickListener(this);
        menuWeiboBt.setOnClickListener(this);
        menuWechatBt.setOnClickListener(this);
        menuMomentsBt.setOnClickListener(this);
        startTextSize = lrcView.getTextSize();//the size (in pixels) of the default text size in this TextView
        menuTextSizeSeek = (SeekBar) findViewById(R.id.menu_lrc_view_text_size_seek);

        menuTextSizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lrcView.setTextSize(TypedValue.COMPLEX_UNIT_PX, startTextSize + (progress / 100f - 0.5f) * 20f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

    }

    @Override
    protected void onDestroy() {
        if (lrcParserThread != null) {
            lrcParserThread.interrupt();
            lrcParserThread = null;
        }
        if (blurImageThread != null) {
            blurImageThread.interrupt();
            blurImageThread = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        long secondClickTime = 0;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Toast.makeText(LrcActivity.this,"ckick"+event.getDownTime(),Toast.LENGTH_SHORT).show();
                if (firstClickTime != 0) {
                    secondClickTime = event.getDownTime();
                    if (secondClickTime - firstClickTime < 300) {
                        // Toast.makeText(this, "双击", Toast.LENGTH_SHORT).show();
                        showMenu();
                    }
                }
                firstClickTime = event.getDownTime();

                startX = event.getRawX();
                startY = event.getRawY();
                break;
            case MotionEvent.ACTION_MOVE:
                endX = event.getRawX();
                endY = event.getRawY();
                float distanceX = endX - startX;
                float distanceY = Math.abs(endY - startY);
                if (distanceX > 100 && distanceY < 100) {
                    finish();
                }
                break;
            case MotionEvent.ACTION_UP:
                startX = 0;
                startY = 0;
                endX = 0;
                endY = 0;
                break;
        }
        return false;
    }

    private void showMenu() {
        if (!isMenuVisiblity) {
            showAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 1f,
                    Animation.RELATIVE_TO_SELF, 0f);
            showAnimation.setDuration(300);
            setMenuLayout.setAnimation(showAnimation);
            setMenuLayout.setVisibility(View.VISIBLE);
            menuHideBt.setBackground(getResources().getDrawable(R.drawable.show_2_white));
            isMenuVisiblity = true;
        } else {
            hideAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 1f);
            hideAnimation.setDuration(300);
            setMenuLayout.setAnimation(hideAnimation);
            setMenuLayout.setVisibility(View.GONE);
            menuHideBt.setBackground(getResources().getDrawable(R.drawable.hide_1_white));
            isMenuVisiblity = false;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.menu_lrc_view_back_bt:
                finish();
                break;
            case R.id.menu_lrc_view_hide_bt:
                menuHideBt.setBackground(getResources().getDrawable(R.drawable.hide_1_white));
                hideAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f,
                        Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
                        Animation.RELATIVE_TO_SELF, 1f);
                hideAnimation.setDuration(300);
                setMenuLayout.setAnimation(hideAnimation);
                setMenuLayout.setVisibility(View.GONE);
                isMenuVisiblity = false;
                break;

            case R.id.menu_lrc_view_qzone_bt:

                break;
            case R.id.menu_lrc_view_weibo_bt:

                break;
            case R.id.menu_lrc_view_wechat_bt:
                shareToWX(SendMessageToWX.Req.WXSceneSession);
                break;
            case R.id.menu_lrc_view_moments_bt:
                shareToWX(SendMessageToWX.Req.WXSceneTimeline);
                break;
            case R.id.menu_lrc_view_like_bt:
                if (!isLike) {
                    menuLikeBt.setBackground(getResources().getDrawable(R.drawable.like_1_red));
                    isLike = true;
                    diskLruCacheUtil.addToDiskCache(song);
                } else {
                    menuLikeBt.setBackground(getResources().getDrawable(R.drawable.unlike_1_white));
                    isLike = false;
                    diskLruCacheUtil.removeFromDiskCache(song);
                }
                break;
            case R.id.menu_lrc_view_plain_bt:
                if (!isPlain) {
                    lrcView.setGravity(Gravity.LEFT);
                    handler.obtainMessage(0, lrcText).sendToTarget();

                    menuPlainBt.setTextColor(Color.BLACK);
                    isPlain = true;
                } else {
                    lrcView.setGravity(Gravity.CENTER);
                    if (lrcParserThread != null) {
                        lrcParserThread.run();
                    } else {
                        lrcParserThread = new LrcParserThread();
                        lrcParserThread.start();
                    }
                    menuPlainBt.setTextColor(Color.WHITE);
                    isPlain = false;
                }
                break;
            default:
                break;
        }
    }


    public boolean shareToWX(int req) {
        WeChatShare weChatShare = WeChatShare.getInstance(getApplicationContext());
        boolean regB = weChatShare.regToWX();
        LogUtil.i("regB" + (regB == true ? "true" : "false"));
        // Bitmap bitmap = BitmapFactory.decodeResource(getResources(),R.drawable.send_img);
        boolean flag = weChatShare.sendImgToWX(BitmapUtil.convertViewToBitmap(scrollView), req);
        LogUtil.i("flag" + (flag == true ? "true" : "false"));
        return flag;
    }

    public class BlurImageThread extends Thread {
        @Override
        public void run() {
            super.run();
            Display display = getWindowManager().getDefaultDisplay();
            // Bitmap orginalImage = BitmapFactory.decodeByteArray(albumCover, 0, albumCover.length);
            Bitmap smallImage = BitmapUtil.scaleBitmap(albumCover, 0.2f, 0.2f);
            Bitmap blurImage = BitmapUtil.blur(smallImage, 20);
            Bitmap backImage = BitmapUtil.getSuitaleBitmap(blurImage, display.getWidth(), display.getHeight());
            handler.obtainMessage(1, BitmapUtil.getTransparentBitmap(backImage, 90)).sendToTarget();
        }
    }

    public class LrcParserThread extends Thread {
        @Override
        public void run() {
            super.run();

            try {
                lrcInfo = new LrcParser().parser(new ByteArrayInputStream(lrcText.getBytes()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (lrcInfo == null || lrcInfo.getInfos().size() == 0) {
                lrcText.replace("\r\n", "\n\n");
                handler.obtainMessage(0, songName + "\n\n" + lrcText).sendToTarget();
                return;
            }

            String lrcStr = "";
            if (lrcInfo.getTitle() != null && lrcInfo.getTitle().length() > 0) {
                lrcStr = lrcStr + lrcInfo.getTitle();
            }
            if (lrcInfo.getArtist() != null && lrcInfo.getArtist().length() > 0) {
                lrcStr = lrcStr + "\n\n演唱：" + lrcInfo.getArtist();
            }
            if (lrcInfo.getAlbum() != null && lrcInfo.getAlbum().length() > 0) {
                lrcStr += ("    专辑：" + lrcInfo.getAlbum());
            }
            for (String s : lrcInfo.getInfos().values()) {
                lrcStr += ("\n\n" + s);
            }

            handler.obtainMessage(0, lrcStr).sendToTarget();
        }
    }
}
