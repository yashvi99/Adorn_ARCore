package bhumi.arrapp.com;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.AppCompatActivity;

public class
itemsDisplayActivity extends AppCompatActivity {

    FloatingActionButton flb;
    ImageView imageView;
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_items_display);



    ItemsModel itemsModel;



        imageView = findViewById(R.id.imageViewItem);
        textView  = findViewById(R.id.tvDesc);
        flb=findViewById(R.id.floating_action_button);

        Intent intent = getIntent();

        if(intent.getExtras() != null){

            itemsModel = (ItemsModel) intent.getSerializableExtra("item");
            imageView.setImageResource(itemsModel.getImage());
            textView.setText(itemsModel.getDesc());
            String info=getIntent().getStringExtra("Error");
            textView.setText(info);

        }


    }
}

