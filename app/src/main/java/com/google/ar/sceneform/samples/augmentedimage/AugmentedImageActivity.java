package com.google.ar.sceneform.samples.augmentedimage;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.samples.common.helpers.SnackbarHelper;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AugmentedImageActivity extends AppCompatActivity {

    private ArFragment arFragment;
    private Frame frame;
    private ModelRenderable pikachu, eevee, pokeball;
    private Map<AugmentedImage, AugmentedImageNode> augmentedImageMap = new HashMap<>();
    private Collection<AugmentedImage> updatedAugmentedImages;
    private AnchorNode aNode;
    private Anchor anchor;
    private String latest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageButton cameraBtn = findViewById(R.id.cameraButton);

        cameraBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (ActivityCompat.checkSelfPermission(AugmentedImageActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(AugmentedImageActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                    Toast.makeText(AugmentedImageActivity.this, "다시 버튼을 눌러주세요", Toast.LENGTH_SHORT).show();
                } else if (ActivityCompat.checkSelfPermission(AugmentedImageActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    takePhoto();
                }

            }
        });

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
       arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);

        mInit();

        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (pikachu == null || eevee == null || pokeball == null) {
                        return;
                    }
                });
    }

    public void onButtonShowPopupWindowClick(View view, Uri uri) {
        // inflate the layout of the popup window
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_window, null);

        // create the popup window
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);


        // show the popup window
        // which view you pass in doesn't matter, it is only used for the window tolken
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);

        ImageView iv = (ImageView) popupWindow.getContentView().findViewById(R.id.iv_preview);
        iv.setImageURI(uri);


        // dismiss the popup window when touched
        popupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                popupWindow.dismiss();
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (augmentedImageMap.isEmpty()) {
        }
    }

    private void onUpdateFrame(FrameTime frameTime) {

        frame = arFragment.getArSceneView().getArFrame();

        if (frame == null) {
            return;
        }

        updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage.class);

        for (AugmentedImage augmentedImage : updatedAugmentedImages) {

            switch (augmentedImage.getTrackingState()) {

                case PAUSED:

                    final TextView mSwitcher = findViewById(R.id.MD_detail_popup);

                    if (augmentedImage.getIndex() == 0) {
                        mSwitcher.setText("이것은 피카츄입니다");
                    } else if (augmentedImage.getIndex() == 1) {
                        mSwitcher.setText("이것은 이브이입니다");
                    } else if (augmentedImage.getIndex() == 2) {
                        mSwitcher.setText("이것은 포켓볼입니다");
                    }

                    final Animation in = new AlphaAnimation(1.0f, 0.0f);
                    in.setDuration(0);

                    final Animation out = new AlphaAnimation(1.0f, 0.0f);
                    out.setDuration(6500);

                    mSwitcher.startAnimation(in);
                    mSwitcher.startAnimation(out);

                    //mSwitcher.setVisibility(View.INVISIBLE);

                    break;

                case TRACKING:
                    // Have to switch to UI Thread to update View.
                    // Create a new anchor for newly found images.

                    if (!augmentedImageMap.containsKey(augmentedImage)) {
                        if (augmentedImage.getAnchors().size() == 0) {
                            anchor = augmentedImage.createAnchor(augmentedImage.getCenterPose());
                            aNode = new AnchorNode(anchor);
                            aNode.setParent(arFragment.getArSceneView().getScene());

                            augmentedImageMap.put(augmentedImage, new AugmentedImageNode(this));

                            TransformableNode tNode = new TransformableNode(arFragment.getTransformationSystem());
                            tNode.getScaleController().setMinScale(0.01f);
                            tNode.getScaleController().setMaxScale(0.05f);
                            tNode.getTranslationController().setEnabled(false);
                            tNode.setParent(aNode);

                            switch (augmentedImage.getIndex()) {
                                case 0:
                                    tNode.setRenderable(pikachu);
                                    break;

                                case 1:
                                    tNode.setRenderable(eevee);
                                    break;

                                case 2:
                                    tNode.setRenderable(pokeball);
                                    break;
                            }

                            tNode.select();
                        }
                    }

                    break;

                case STOPPED:
                    augmentedImageMap.remove(augmentedImage);
                    break;
            }
        }
    }

    private void mInit() {
        //model 1
        ModelRenderable.builder()
                .setSource(this, R.raw.pikachu)
                .build()
                .thenAccept(renderable -> pikachu = renderable)
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });

        //model 2
        ModelRenderable.builder()
                .setSource(this, R.raw.eevee)
                .build()
                .thenAccept(renderable -> eevee = renderable)
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });

        //model 3
        ModelRenderable.builder()
                .setSource(this, R.raw.pokeball)
                .build()
                .thenAccept(renderable -> pokeball = renderable)
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });
    }

//    private void takePhoto() {
//        final String filename = generateFilename();
//        ArSceneView view = arFragment.getArSceneView();
//
//        // Create a bitmap the size of the scene view.
//        final Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
//                Bitmap.Config.ARGB_8888);
//
//        // Create a handler thread to offload the processing of the image.
//        final HandlerThread handlerThread = new HandlerThread("PixelCopier");
//        handlerThread.start();
//        // Make the request to copy.
//        PixelCopy.request(view, bitmap, (copyResult) -> {
//            if (copyResult == PixelCopy.SUCCESS) {
//                try {
//                    saveBitmapToDisk(bitmap, filename);
//                } catch (IOException e) {
//                    Toast toast = Toast.makeText(AugmentedImageActivity.this, e.toString(),
//                            Toast.LENGTH_LONG);
//                    toast.show();
//                    return;
//                }
//                Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
//                        "저장 완료", Snackbar.LENGTH_LONG);
//                snackbar.setAction("사진 열기", v -> {
//                    File photoFile = new File(filename);
//                    Uri photoURI = FileProvider.getUriForFile(AugmentedImageActivity.this,
//                            AugmentedImageActivity.this.getPackageName() + ".ar.codelab.name.provider",
//                            photoFile);
//                    onButtonShowPopupWindowClick(view, photoURI);
//                });
//                snackbar.show();
//            } else {
//                Toast toast = Toast.makeText(AugmentedImageActivity.this,
//                        "Failed to copyPixels: " + copyResult, Toast.LENGTH_LONG);
//                toast.show();
//            }
//            handlerThread.quitSafely();
//        }, new Handler(handlerThread.getLooper()));
//    }

    private void takePhoto() {

        final String filename = generateFilename();
        ArSceneView view = arFragment.getArSceneView();

        // Create a bitmap the size of the scene view.
        Bitmap waterMark = BitmapFactory.decodeResource(getResources(), R.drawable.shinsegae);

        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);

        // Create a handler thread to offload the processing of the image.
        final HandlerThread handlerThread = new HandlerThread("PixelCopier");
        handlerThread.start();
        // Make the request to copy.
        PixelCopy.request(view, bitmap, (copyResult) -> {
            if (copyResult == PixelCopy.SUCCESS) {
                try {
                    Canvas canvas = new Canvas(bitmap);
                    canvas.drawBitmap(waterMark, view.getWidth()/2, view.getHeight()-50, null);
                    saveBitmapToDisk(bitmap, filename);
                } catch (IOException e) {
                    Toast toast = Toast.makeText(AugmentedImageActivity.this, e.toString(),
                            Toast.LENGTH_LONG);
                    toast.show();
                    return;
                }
                Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
                        "저장 완료", Snackbar.LENGTH_LONG);
                snackbar.setAction("사진 열기", v -> {
                    File photoFile = new File(filename);
                    Uri photoURI = FileProvider.getUriForFile(AugmentedImageActivity.this,
                            AugmentedImageActivity.this.getPackageName() + ".ar.codelab.name.provider",
                            photoFile);
                    onButtonShowPopupWindowClick(view, photoURI);
                });
                snackbar.show();
            } else {
                Toast toast = Toast.makeText(AugmentedImageActivity.this,
                        "Failed to copyPixels: " + copyResult, Toast.LENGTH_LONG);
                toast.show();
            }
            handlerThread.quitSafely();
        }, new Handler(handlerThread.getLooper()));
    }

    private String generateFilename() {
        String date =
                new SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.getDefault()).format(new Date());
        latest = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES) + File.separator + "AR갤러리/" + date + "_screenshot.jpg";
        return latest;
    }

    private void saveBitmapToDisk(Bitmap bitmap, String filename) throws IOException {

        File out = new File(filename);

        if (!out.getParentFile().exists()) {
            out.getParentFile().mkdirs();
        }
        try (FileOutputStream outputStream = new FileOutputStream(filename);
             ByteArrayOutputStream outputData = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputData);
            outputData.writeTo(outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (IOException ex) {
            throw new IOException("Failed to save bitmap to disk", ex);
        }
    }

}