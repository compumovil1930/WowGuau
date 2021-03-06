package com.example.wowguauv2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

public class PerfilPaseador extends AppCompatActivity {

    TextView txtNombre;
    TextView txtExperiencia;
    TextView txtAniosExp;
    TextView txtDistancia;
    TextView txtCalificacion;
    Button btnSolicitarPaseo;
    Double distancia;
    Spinner spinnerHora;
    Spinner spinnerMascotas;
    Paseador p;
    ArrayList<String> mascotasNombres;
    ImageView profile;

    FirebaseDatabase database;
    DatabaseReference myRef;
    FirebaseUser user;
    private StorageReference mStorageRef;

    private static final String TAG = "WalkerProfile";

    public static final String PATH_PASEO = "paseos/";
    public static final String MASCOTAS_PATH = "mascotas/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil_paseador);

        mStorageRef = FirebaseStorage.getInstance().getReference();

        database = FirebaseDatabase.getInstance();
        user = FirebaseAuth.getInstance().getCurrentUser();

        txtNombre = findViewById(R.id.txtNombre);
        txtExperiencia = findViewById(R.id.txtExperiencia);
        txtAniosExp = findViewById(R.id.txtAniosExp);
        txtDistancia = findViewById(R.id.txtDistancia);
        txtCalificacion = findViewById(R.id.txtCalificacion);
        btnSolicitarPaseo = findViewById(R.id.btnSolicitarPaseo);
        spinnerMascotas = findViewById(R.id.spinnerMascotas);
        spinnerHora = findViewById(R.id.spinnerHora);
        mascotasNombres = new ArrayList<String>();

        loadMascotas();

        p = (Paseador) getIntent().getSerializableExtra("paseador");
        Log.d(TAG, p.toString());
        distancia = getIntent().getDoubleExtra("DistanciaClientPas", 0.0);
        txtNombre.setText("Nombre: " + p.getNombre());
        txtExperiencia.setText("Experiencia del paseador: " + p.getDescripcion());
        txtAniosExp.setText("Años de experiencia: " + p.getAñosE());
        txtDistancia.setText("Distancia: " + distancia);
        /////////////////////////////////////

        StorageReference lugarRef = mStorageRef.child(p.getPathFoto());
        Log.d(TAG, lugarRef.toString());

        File localFile = null;

        try {
            localFile = File.createTempFile("images_" + p.getNombre(), ".png");
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.i(TAG, localFile.toString());

        final File finalLocalFile = localFile;
        lugarRef.getFile(localFile)
                .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                        Log.i(TAG, "EXITO" + taskSnapshot.getStorage().toString());
                        File imgFile = new  File(finalLocalFile.getPath());

                        if(imgFile.exists()){

                            Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());

                            ImageView myImage = (ImageView) findViewById(R.id.profileWalker);

                            myImage.setImageBitmap(myBitmap);

                        }

                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Log.i(TAG, "FALLA");
            }
        });

        //temp.add(localFile.getPath());

///////////////////////////
        btnSolicitarPaseo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Paseo p = crearPaseo();
                myRef = database.getReference(PATH_PASEO);
                String key = myRef.push().getKey();
                myRef = database.getReference(PATH_PASEO + key);
                myRef.setValue(p).addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(getApplicationContext(), "Solicitud de paseo agregada", Toast.LENGTH_LONG).show();
                    }
                });
                Intent i = new Intent(getApplicationContext(), PPrincipalCliente.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);

            }
        });
    }

    private Paseo crearPaseo() {
        Log.d(TAG, "crearPaseo: " + p);
        Paseo paseo = new Paseo();
        paseo.setCalificado(false);
        paseo.setAceptado(false);
        paseo.setActivo(true);
        paseo.setClienteUid(user.getUid());
        paseo.setPaseadorUid(p.getUid());
        paseo.setNombreMascota(spinnerMascotas.getSelectedItem().toString());
        paseo.setLatPaseador(0.0);
        paseo.setLongPaseador(0.0);
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm");
        Date date = new Date();
        StringTokenizer st = new StringTokenizer(spinnerHora.getSelectedItem().toString(), ":");
        int horas = Integer.parseInt(st.nextToken());
        int minutes = Integer.parseInt(st.nextToken());
        date.setHours(horas);
        date.setMinutes(minutes);
        paseo.setInicio(date);
        Log.i("PASEO", "crearPaseo: " + date);
        return paseo;
    }

    public void loadMascotas() {
        Query q = database.getReference(MASCOTAS_PATH).orderByChild("duenoUid").equalTo(user.getUid());
        q.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot dsh : dataSnapshot.getChildren()) {
                    Mascota m = dsh.getValue(Mascota.class);
                    Log.i("consulta", "onDataChange: " + m.getNombre());
                    mascotasNombres.add(m.getNombre());
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, mascotasNombres);
                spinnerMascotas.setAdapter(adapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.w("errorLectura", "error en la consulta", databaseError.toException());
            }
        });
    }


}
