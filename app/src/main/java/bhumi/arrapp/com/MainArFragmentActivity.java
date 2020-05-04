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

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Sun;
import com.google.ar.sceneform.animation.ModelAnimator;
import com.google.ar.sceneform.rendering.AnimationData;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import yuku.ambilwarna.AmbilWarnaDialog;

public class MainArFragmentActivity extends AppCompatActivity {
    private ArFragment fragment;
    private PointerDrawable pointerDrawable = new PointerDrawable(); // Pointer at center location
    private boolean isTracking; // Indicating if ARCore is in tracking state.
    private boolean isHitting; // Indicating the user is looking at a plane. The method for figuring this out is called hitTest which is why it is called isHitting.
    private ModelLoader modelLoader; // This class Async loaded into AR Fragment
    private Anchor cloudAnchor;


    private int currentColor;
    private enum AppAnchorState {
        NONE,
        HOSTING,
        HOSTED,
        RESOLVING,
        RESOLVED
    }
    private AppAnchorState appAnchorState = AppAnchorState.NONE;


    // defining the variables for integration.

//    Main Class Starts here
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

//       Clear Button Added
        Button clearButton = findViewById(R.id.clear_button);
        clearButton.setOnClickListener(v -> setCloudAnchor(null));

        Button colorButton=findViewById(R.id.color_btton);
        colorButton.setOnClickListener(v -> opendialog(false));

        Button measButton=findViewById(R.id.measure);
        measButton.setOnClickListener(view -> {
            Intent intent=new Intent(MainArFragmentActivity.this,ArMeasureActivity.class);
            startActivity(intent);

        });
    }
    private void opendialog(boolean supportAlpha){

        AmbilWarnaDialog dialog=new AmbilWarnaDialog(this, currentColor, supportAlpha, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog) {

            }

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {

                currentColor = color;

            }
        });

        dialog.show();
    }






    private void setCloudAnchor (Anchor newAnchor) {
        List<Node> children = new ArrayList<>(fragment.getArSceneView().getScene().getChildren());
        for (Node node : children) {
            if (node instanceof AnchorNode) {
                if (((AnchorNode) node).getAnchor() != null) {
                    ((AnchorNode) node).getAnchor().detach();
                }
            }
            if (!(node instanceof Camera) && !(node instanceof Sun)) {
                node.setParent(null);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_options, menu);
        return true;
    }

//    Options Menu to place objects
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Toast.makeText(this, "Selected Item: " +item.getTitle(), Toast.LENGTH_SHORT).show();

        switch (item.getItemId()) {
            case R.id.Sofa:

                SofaGallery();
                return true;
            case R.id.beds:

                bedsGallery();
                return true;

            case R.id.Lamp:

                lampGallery();
                return true;

            case R.id.clock:

                clockGallery();
                return true;

            case R.id.D_Table:

                dinning_tableGallery();
                return true;

            case R.id.Table:

                tableGallery();
                return true;

            case R.id.Plant:

                plantGallery();
                return true;


            case R.id.Painting:

                paintingGallery();
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
    private void SofaGallery() {

       LinearLayout gallery = findViewById(R.id.gallery_layout);

        ImageView sofa1 = new ImageView(this);
        sofa1.setImageResource(R.drawable.sofa_1);
        sofa1.setContentDescription("Sofa1");
        sofa1.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("sofa_1.sfb")));
        gallery.addView(sofa1);


        ImageView sofa2 = new ImageView(this);
        sofa2.setImageResource(R.drawable.sofa_5);
        sofa2.setContentDescription("Sofa2");
        sofa2.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("sofa_5.sfb")));
        gallery.addView(sofa2);


        ImageView sofa3 = new ImageView(this);
        sofa3.setImageResource(R.drawable.sofa_3);
        sofa3.setContentDescription("Sofa3");
        sofa3.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("Sofa_3.sfb")));
        gallery.addView(sofa3);

    }

    private void bedsGallery() {
        LinearLayout gallery = findViewById(R.id.gallery_layout);

        // Add image to Linear Layout, for each augumented object eg. Andy, car, ignoo etc
        ImageView beds1 = new ImageView(this);
        beds1.setImageResource(R.drawable.bed_1);
        beds1.setContentDescription("Bed Image 1");
        beds1.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("bed_1.sfb")));
        gallery.addView(beds1);


        ImageView bed2 = new ImageView(this);
        bed2.setImageResource(R.drawable.bed_2);
        bed2.setContentDescription("Bed Image 2");
        bed2.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("bed_2.sfb")));
        gallery.addView(bed2);


        ImageView bed3 = new ImageView(this);
        bed3.setImageResource(R.drawable.bed_4);
        bed3.setContentDescription("Bed Image 3");
        bed3.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("bed_4.sfb")));
        gallery.addView(bed3);
    }

    private void clockGallery(){
        LinearLayout gallery = findViewById(R.id.gallery_layout);

        // Add image to Linear Layout, for each augumented object eg. Andy, car, ignoo etc
        ImageView clock = new ImageView(this);
        clock.setImageResource(R.drawable.clock);
        clock.setContentDescription("Clock Image 1");
        clock.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("Clock.sfb")));
        gallery.addView(clock);


    }

    private void lampGallery(){
        LinearLayout gallery = findViewById(R.id.gallery_layout);

        // Add image to Linear Layout, for each augumented object eg. Andy, car, ignoo etc
        ImageView lamp1 = new ImageView(this);
        lamp1.setImageResource(R.drawable.ceiling_lamp);
        lamp1.setContentDescription("Lamp1");
        lamp1.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("ceiling_lamp.sfb")));
        gallery.addView(lamp1);

        ImageView lamp2 = new ImageView(this);
        lamp2.setImageResource(R.drawable.ceilinglamp2);
        lamp2.setContentDescription("Lamp2");
        lamp2.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("ceiling_lamp_2 (1).sfb")));
        gallery.addView(lamp2);

        ImageView lamp3 = new ImageView(this);
        lamp3.setImageResource(R.drawable.floor_lamp);
        lamp3.setContentDescription("Floor_lamp3");
        lamp3.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("floor_lamp.sfb")));
        gallery.addView(lamp3 );

        ImageView lamp4 = new ImageView(this);
        lamp4.setImageResource(R.drawable.table_lamp);
        lamp4.setContentDescription("Table_lamp4");
        lamp4.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("table_lamp (3).sfb")));
        gallery.addView(lamp4);

        ImageView walllamp = new ImageView(this);
        walllamp.setImageResource(R.drawable.wall_lamp);
        walllamp.setContentDescription("Wall_lamp5");
        walllamp.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("wall_lamp (2).sfb")));
        gallery.addView(walllamp);

        ImageView walllight = new ImageView(this);
        walllight.setImageResource(R.drawable.wall_light);
        walllight.setContentDescription("WallLight6");
        walllight.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("wall_light.sfb")));
        gallery.addView(walllight);
    }

    private void dinning_tableGallery() {
        LinearLayout gallery = findViewById(R.id.gallery_layout);

        // Add image to Linear Layout, for each augumented object eg. Andy, car, ignoo etc
        ImageView table = new ImageView(this);
        table.setImageResource(R.drawable.dinning_table_1);
        table.setContentDescription("Table Image 1");
        table.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("dinning_table_1.sfb")));
        gallery.addView(table);

        ImageView table2 = new ImageView(this);
        table2.setImageResource(R.drawable.dinning_table_3);
        table2.setContentDescription("Lamp Image 1");
        table2.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("dinning_table_3.sfb")));
        gallery.addView(table2);

    }

    private void tableGallery(){
        LinearLayout gallery = findViewById(R.id.gallery_layout);

        // Add image to Linear Layout, for each augumented object eg. Andy, car, ignoo etc
        ImageView table1 = new ImageView(this);
        table1.setImageResource(R.drawable.coffee_table);
        table1.setContentDescription("Table1");
        table1.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("coffee_table(2).sfb")));
        gallery.addView( table1);

        ImageView  table2 = new ImageView(this);
        table2.setImageResource(R.drawable.coffee_table_2);
        table2.setContentDescription("Table2");
        table2.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("coffee_table_2(2).sfb")));
        gallery.addView(table2);


        ImageView table3 = new ImageView(this);
        table3.setImageResource(R.drawable.softwall);
        table3.setContentDescription("Table3");
        table3.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("softwall.sfb")));
        gallery.addView(table3);


        ImageView table4 = new ImageView(this);
        table4.setImageResource(R.drawable.table_1);
        table4.setContentDescription("Table4");
        table4.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("table_1(2).sfb")));
        gallery.addView(table4);


        ImageView table5 = new ImageView(this);
        table5.setImageResource(R.drawable.table_2);
        table5.setContentDescription("Table5");
        table5.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("table_2(2).sfb")));
        gallery.addView(table5);



    }

    private void plantGallery(){
        LinearLayout gallery = findViewById(R.id.gallery_layout);

        // Add image to Linear Layout, for each augumented object eg. Andy, car, ignoo etc
        ImageView clock = new ImageView(this);
        clock.setImageResource(R.drawable.plants_3);
        clock.setContentDescription("Plant1");
        clock.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("plants_3.sfb")));
        gallery.addView(clock);


    }

    private void paintingGallery(){
        LinearLayout gallery = findViewById(R.id.gallery_layout);

        // Add image to Linear Layout, for each augumented object eg. Andy, car, ignoo etc
        ImageView woman = new ImageView(this);
        woman.setImageResource(R.drawable.cleo);
        woman.setContentDescription("painting1");
        woman.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("woman_painting(1).sfb")));
        gallery.addView(woman);

        ImageView panel = new ImageView(this);
        panel.setImageResource(R.drawable.panel);
        panel.setContentDescription("panel1");
        panel.setOnClickListener(view -> MainArFragmentActivity.this.addObject(Uri.parse("panel.sfb")));
        gallery.addView(panel);


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

