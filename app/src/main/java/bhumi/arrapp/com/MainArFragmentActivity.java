package bhumi.arrapp.com;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Menu;
import android.view.MenuItem;
import android.view.PixelCopy;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import java.lang.ref.WeakReference;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;

import com.google.ar.core.HitResult;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.animation.ModelAnimator;

import com.google.ar.sceneform.rendering.AnimationData;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;



import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

public class MainArFragmentActivity extends AppCompatActivity {
    private ArFragment fragment;
    private PointerDrawable pointerDrawable = new PointerDrawable(); // Pointer at center location
    private boolean isTracking; // Indicating if ARCore is in tracking state.
    private boolean isHitting; // Indicating the user is looking at a plane. The method for figuring this out is called hitTest which is why it is called isHitting.
    private ModelLoader modelLoader; // This class Async loaded into AR Fragment
    private Anchor cloudAnchor;
    private enum AppAnchorState {
        NONE,
        HOSTING,
        HOSTED,
        RESOLVING,
        RESOLVED
    }
    private AppAnchorState appAnchorState = AppAnchorState.NONE;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mainarfragment);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        // Take Photo when click FAB
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> MainArFragmentActivity.this.takePhoto());

        fragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);

        // This callback called before processing every frame in ArSceneView
        fragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            fragment.onUpdate(frameTime);
            MainArFragmentActivity.this.onUpdate();
        });

        // Create instance of ModelLoader with weakRefrence
        modelLoader = new ModelLoader(new WeakReference<>(this));
        // Initialise gallery views
//        initializeGallery();
//        sofaGallery();
        Button clearButton = findViewById(R.id.clear_button);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCloudAnchor(null);
            }
        });

    }

    private void setCloudAnchor (Anchor newAnchor) {
        if (cloudAnchor != null) {
            cloudAnchor.detach();
        }

        cloudAnchor = newAnchor;
        appAnchorState = AppAnchorState.NONE;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_options, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Toast.makeText(this, "Selected Item: " +item.getTitle(), Toast.LENGTH_SHORT).show();

        switch (item.getItemId()) {
            case R.id.search_item:
                // do your code
                initializeGallery();
                return true;
            case R.id.upload_item:
                // do your code
                sofaGallery();
                return true;
            case R.id.copy_item:
                // do your code
                return true;
            case R.id.print_item:
                // do your code
                return true;
            case R.id.share_item:
                // do your code
                return true;
            case R.id.bookmark_item:
                // do your code
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Update the tracking state. If ARCore is not tracking, remove the pointer until tracking is restored.
    private void onUpdate(){
        boolean trackingChanged = updateTracking();
        View contentView = findViewById(android.R.id.content);
        if (trackingChanged) {
            if (isTracking) {
                contentView.getOverlay().add(pointerDrawable);
            } else {
                contentView.getOverlay().remove(pointerDrawable);
            }
            contentView.invalidate();
        }

        if (isTracking) {
            boolean hitTestChanged = updateHitTest();
            if (hitTestChanged) {
                pointerDrawable.setEnabled(isHitting);
                contentView.invalidate();
            }
        }
    }

    //  Uses ARCore's camera state and returns true if the tracking state has changed since last call.
    private boolean updateTracking(){
        Frame frame = fragment.getArSceneView().getArFrame();
        boolean wasTracking = isTracking;
        isTracking = frame != null && frame.getCamera().getTrackingState() == TrackingState.TRACKING;
        return isTracking != wasTracking;
    }

    // As soon as any hit is detected, the method returns.
    private boolean updateHitTest(){
        Frame frame = fragment.getArSceneView().getArFrame();
        Point pt = getScreenCenter();
        List<HitResult> hits;
        boolean wasHitting = isHitting;
        isHitting = false;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane &&
                        ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    isHitting = true;
                    break;
                }
            }
        }
        return wasHitting != isHitting;
    }

    // To make plane in center of the screen
    private Point getScreenCenter(){
        View vw = findViewById(android.R.id.content);
        return new android.graphics.Point(vw.getWidth()/2, vw.getHeight()/2);
    }

    // Create gallery for selecting augument objects
    private void initializeGallery() {
        LinearLayout gallery = findViewById(R.id.gallery_layout);

        // Add image to Linear Layout, for each augumented object eg. Andy, car, ignoo etc
        ImageView andy = new ImageView(this);
        andy.setImageResource(R.drawable.droid_thumb);
        andy.setContentDescription("andy");
        andy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainArFragmentActivity.this.addObject(Uri.parse("andy_dance.sfb"));
            }
        });
        gallery.addView(andy);

        ImageView cabin = new ImageView(this);
        cabin.setImageResource(R.drawable.cabin_thumb);
        cabin.setContentDescription("cabin");
        cabin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainArFragmentActivity.this.addObject(Uri.parse("Cabin.sfb"));
            }
        });
        gallery.addView(cabin);

        ImageView house = new ImageView(this);
        house.setImageResource(R.drawable.clock);
        house.setContentDescription("house");
        house.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("House.sfb")));
        gallery.addView(house);

        ImageView igloo = new ImageView(this);
        igloo.setImageResource(R.drawable.stool);
        igloo.setContentDescription("igloo");
        igloo.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("igloo.sfb")));
        gallery.addView(igloo);
    }

    private void sofaGallery() {
        LinearLayout gallery = findViewById(R.id.gallery_layout);

        // Add image to Linear Layout, for each augumented object eg. Andy, car, ignoo etc
        ImageView andy = new ImageView(this);
        andy.setImageResource(R.drawable.sofa_thumb);
        andy.setContentDescription("andy");
        andy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainArFragmentActivity.this.addObject(Uri.parse("andy_dance.sfb"));
            }
        });
        gallery.addView(andy);


        ImageView igloo = new ImageView(this);
        igloo.setImageResource(R.drawable.igloo_thumb);
        igloo.setContentDescription("igloo");
        igloo.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("igloo.sfb")));
        gallery.addView(igloo);
    }

    // Add augumented object to AR Fragment
    private void addObject(Uri model) {
        Frame frame = fragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane &&
                        ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    modelLoader.loadModel(hit.createAnchor(), model);
                    break;

                }
            }
        }
    }


    // Basically this method is used for doing operations with nodes,
    public void addNodeToScene(Anchor anchor, ModelRenderable renderable) {
        //  Anchor nodes are positioned based on the pose of an ARCore Anchor.
        //  As a result, they stay positioned in the sample place relative to the real world.
        AnchorNode anchorNode = new AnchorNode(anchor);

        // We could use the base class type, Node for the placing the objects,
        // but Node does not have the interaction functionality to handle moving, scaling and rotating based on user gestures.
        TransformableNode node = new TransformableNode(fragment.getTransformationSystem());

        node.setRenderable(renderable);
        node.setParent(anchorNode);
        fragment.getArSceneView().getScene().addChild(anchorNode);
        node.select();
        startAnimation(node, renderable);
    }

    // when the network is down, loading a model remotely will fail.
    public void onException(Throwable throwable){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(throwable.getMessage())
                .setTitle("Main activity error!");
        AlertDialog dialog = builder.create();
        dialog.show();
        return;
    }

    // Start animation for node
    public void startAnimation(TransformableNode node, ModelRenderable renderable){
        if(renderable==null || renderable.getAnimationDataCount() == 0) {
            return;
        }
        for(int i = 0;i < renderable.getAnimationDataCount();i++){
            AnimationData animationData = renderable.getAnimationData(i);
        }
        ModelAnimator animator = new ModelAnimator(renderable.getAnimationData(0), renderable);
        animator.start();

        node.setOnTapListener(
                (hitTestResult, motionEvent) -> {
                    togglePauseAndResume(animator);
                });
    }

    // Tap to start and stop animation for that use TapListener
    public void togglePauseAndResume(ModelAnimator animator) {
        if (animator.isPaused()) {
            animator.resume();
        } else if (animator.isStarted()) {
            animator.pause();
        } else {
            animator.start();
        }
    }

    // Generate file name for captured Image
    private String generateFilename() {
        String date =
                new SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.getDefault()).format(new Date());
        return Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES) + File.separator + "Sceneform/" + date + "_screenshot.jpg";
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
        } catch (IOException ex) {
            throw new IOException("Failed to save bitmap to disk", ex);
        }
    }

    private void takePhoto() {
        final String filename = generateFilename();
        ArSceneView view = fragment.getArSceneView();

        // Create a bitmap the size of the scene view.
        final Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);

        // Create a handler thread to offload the processing of the image.
        final HandlerThread handlerThread = new HandlerThread("PixelCopier");
        handlerThread.start();
        // Make the request to copy.
        PixelCopy.request(view, bitmap, new PixelCopy.OnPixelCopyFinishedListener() {
            @Override
            public void onPixelCopyFinished(int copyResult) {
                if (copyResult == PixelCopy.SUCCESS) {
                    try {
                        MainArFragmentActivity.this.saveBitmapToDisk(bitmap, filename);
                    } catch (IOException e) {
                        Toast toast = Toast.makeText(MainArFragmentActivity.this, e.toString(),
                                Toast.LENGTH_LONG);
                        toast.show();
                        return;
                    }
                    Snackbar snackbar = Snackbar.make(MainArFragmentActivity.this.findViewById(android.R.id.content),
                            "Photo saved", Snackbar.LENGTH_LONG);
                    snackbar.setAction("Open in Photos", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            File photoFile = new File(filename);

                            Uri photoURI = FileProvider.getUriForFile(MainArFragmentActivity.this,
                                    MainArFragmentActivity.this.getPackageName() + ".ar.codelab.name.provider",
                                    photoFile);
                            Intent intent = new Intent(Intent.ACTION_VIEW, photoURI);
                            intent.setDataAndType(photoURI, "image/*");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            MainArFragmentActivity.this.startActivity(intent);

                        }
                    });
                    snackbar.show();
                } else {
                    Toast toast = Toast.makeText(MainArFragmentActivity.this,
                            "Failed to copyPixels: " + copyResult, Toast.LENGTH_LONG);
                    toast.show();
                }
                handlerThread.quitSafely();
            }
        }, new Handler(handlerThread.getLooper()));
    }
}

