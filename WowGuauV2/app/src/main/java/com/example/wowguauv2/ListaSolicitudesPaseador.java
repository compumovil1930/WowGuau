package com.example.wowguauv2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.w3c.dom.Text;

public class ListaSolicitudesPaseador extends AppCompatActivity {

    public static final String PATH_PASEADORES = "user/paseador/";
    public static final String PATH_CLIENTES = "user/client/";
    public static final String PATH_PASEOS = "paseos/";

    Switch switchD;
    ListView lv1;
    TextView textoSolicitudes;
    Button logout;

    //Permisos
    static final int REQUEST_LOCATION = 2;

    //Base de datos
    private FirebaseAuth mAuth;
    FirebaseUser user;
    FirebaseDatabase database;
    DatabaseReference myRef;

    //Localizacion
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    Location mCurrentLocation;
    double RADIUS_OF_EARTH_KM = 6371.01;
    double lat2, lon2, distancia;

    Context contexto = this;

    String[][] datos = {
            {"", ""},
    };

    double [][] localizaciones = {{,}};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_solicitudes_paseador);

        mAuth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance();
        user = mAuth.getCurrentUser();

        myRef = database.getReference(PATH_PASEADORES + user.getUid());
        myRef.child("estado").setValue(true);

        switchD = (Switch) findViewById(R.id.switch_disponible);

        textoSolicitudes = (TextView) findViewById(R.id.textoSolicitudes);

        lv1 = (ListView) findViewById(R.id.lv1);

        lv1.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Intent intent = new Intent(view.getContext(), MapaSolicitud.class);
                //intent.putExtra("localizac",“value”);
                Bundle bundle = new Bundle();
                bundle.putDouble("lat", localizaciones[i][0]);
                bundle.putDouble("lon", localizaciones[i][1]);
                intent.putExtra("bundle",bundle);
                startActivity(intent);
            }
        });

        switchD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                myRef = database.getReference(PATH_PASEADORES + user.getUid());
                if (switchD.isChecked()) {
                    myRef.child("estado").setValue(true);
                    startLocationUpdates();
                    mostrarSolicitudes();
                    switchD.setText(getResources().getText(R.string.disponible));

                    switchD.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.colorAccent));
                } else {
                    myRef.child("estado").setValue(false);
                    stopLocationUpdates();
                    quitarSolicitudes();

                    switchD.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.rojo));
                }
            }
        });

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mLocationRequest = createLocationRequest();

        askPermission();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        } else {

            // Acceder configuracion del usuario, verificar que la ubicacion este encendida
            LocationSettingsRequest.Builder builder = new
                    LocationSettingsRequest.Builder().addLocationRequest(mLocationRequest);
            SettingsClient client = LocationServices.getSettingsClient(contexto);
            Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

            //Se tiene acceso
            task.addOnSuccessListener((Activity) contexto, new OnSuccessListener<LocationSettingsResponse>() {
                @Override
                public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                    startLocationUpdates(); //Todas las condiciones para recibir localizaciones
                }
            });

            // No se tiene acceso, mostrar dialogo
            task.addOnFailureListener((Activity) contexto, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    int statusCode = ((ApiException) e).getStatusCode();
                    switch (statusCode) {
                        case CommonStatusCodes.RESOLUTION_REQUIRED:
                            // Location settings are not satisfied, but this can be fixed by showing the user a dialog.
                            try {// Show the dialog by calling startResolutionForResult(), and check the result in onActivityResult().
                                ResolvableApiException resolvable = (ResolvableApiException) e;
                                resolvable.startResolutionForResult(ListaSolicitudesPaseador.this,
                                        REQUEST_LOCATION);
                            } catch (IntentSender.SendIntentException sendEx) {
                                // Ignore the error.
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            // Location settings are not satisfied. No way to fix the settings so we won't show the dialog.
                            break;
                    }
                }
            });

            //Acceder a la localizacion
            mFusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        mCurrentLocation = location;
                        double lat = location.getLatitude();
                        double lon = location.getLongitude();
                        myRef.child("latitud").setValue(lat);
                        myRef.child("longitud").setValue(lon);
                    }
                }
            });
        }

        // Cambios de posicion
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                Log.i("LOCATION", "Location update in the callback: " + location);
                if (location != null) {
                    mCurrentLocation = location;
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();
                    Log.i("TAG", "Localizacion " + lat + "   " + lon);
                    myRef.child("latitud").setValue(lat);
                    myRef.child("longitud").setValue(lon);
                    loadUsers();
                }
            }
        };
    }

    public void loadUsers() {
        DatabaseReference mRootRef = database.getReference(PATH_PASEOS);
        mRootRef.addListenerForSingleValueEvent(new ValueEventListener() {


            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int size = 0;
                for (DataSnapshot singleSnapshot : dataSnapshot.getChildren()) {
                    Paseo paseo = singleSnapshot.getValue(Paseo.class);
                    String paseadoruid = paseo.getPaseadorUid();
                    if (paseadoruid.equals(user.getUid())){
                        Log.i("TAG", "Resultado " + "si");
                        size ++;
                    }
                }
                localizaciones = new double[size][2];
                String[][] datos1 = new String [size][2];
                int i = 0;
                for (DataSnapshot singleSnapshot : dataSnapshot.getChildren()) {
                    Paseo paseo = singleSnapshot.getValue(Paseo.class);
                    String paseadoruid = paseo.getPaseadorUid();
                    if (paseadoruid.equals(user.getUid())){
                        String name = paseo.getNombreMascota();
                        String clienteuid = paseo.getClienteUid();

                        Log.i("TAG", "A ver " + name);
                        datos1[i][0] = name;
                        DatabaseReference mRootRef1 = database.getReference(PATH_CLIENTES + clienteuid);
                        mRootRef1.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                lat2 = dataSnapshot.child("latitud").getValue(double.class);
                                lon2 = dataSnapshot.child("longitud").getValue(double.class);
                                double lat = mCurrentLocation.getLatitude();
                                double lon = mCurrentLocation.getLongitude();
                                distancia = distance(lat, lon, lat2, lon2);
                            }
                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                            }
                        });
                        localizaciones [i][0] = lat2;
                        localizaciones [i][1] = lon2;
                        datos1[i][1] = Double.toString(distancia) + " km";

                        i++;
                    }
                    
                }
                datos = datos1;
                mostrarSolicitudes();
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w("TAG", "error en la consulta", databaseError.toException());
            }
        });
    }

    // Funcion actualizacion periodica
    protected LocationRequest createLocationRequest() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000); //tasa de refresco en milisegundos
        mLocationRequest.setFastestInterval(5000); //máxima tasa de refresco
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }

    private void askPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an expanation to the user *asynchronouslyÂ  Â
                Toast.makeText(this, "Se necesita el permiso para poder acceder a la localizacion!", Toast.LENGTH_LONG).show();
            }
            // Request the permission.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        }
    }

    //Resultado del dialogo
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_LOCATION: {
                if (resultCode == RESULT_OK) {
                    startLocationUpdates(); //Se encendió la localización!!!
                } else {
                    Toast.makeText(this,
                            "Sin acceso a localización, hardware deshabilitado!",
                            Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    //Inicia Actividad, inicia suscripcion
    @Override
    protected void onResume() {
        super.onResume();
        startLocationUpdates();
    }

    //Finaliza Actividad, finaliza suscripcion
    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    //Suscripcion a Actualizaciones
    private void startLocationUpdates() {
        //Verificación de permiso!!
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
        }
    }

    //Cancelar suscripcion a Actualizaciones
    private void stopLocationUpdates(){
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Acceso a localizacion!", Toast.LENGTH_LONG).show();
                    // Acceder a configuracion
                    LocationSettingsRequest.Builder builder = new
                            LocationSettingsRequest.Builder().addLocationRequest(mLocationRequest);
                    SettingsClient client = LocationServices.getSettingsClient(contexto);
                    Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

                    // Ubicacion activa
                    task.addOnSuccessListener((Activity) contexto, new OnSuccessListener<LocationSettingsResponse>() {
                        @Override
                        public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                            startLocationUpdates(); //Todas las condiciones para recibir localizaciones
                        }
                    });

                    // Ubicacion inactiva, mostrar dialogo
                    task.addOnFailureListener((Activity) contexto, new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            int statusCode = ((ApiException) e).getStatusCode();
                            switch (statusCode) {
                                case CommonStatusCodes.RESOLUTION_REQUIRED:
                                    // Location settings are not satisfied, but this can be fixed by showing the user a dialog.
                                    try {// Show the dialog by calling startResolutionForResult(), and check the result in onActivityResult().
                                        ResolvableApiException resolvable = (ResolvableApiException) e;
                                        resolvable.startResolutionForResult(ListaSolicitudesPaseador.this,
                                                REQUEST_LOCATION);
                                    } catch (IntentSender.SendIntentException sendEx) {
                                        // Ignore the error.
                                    } break;
                                case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                    // Location settings are not satisfied. No way to fix the settings so we won't show the dialog.
                                    break;
                            }
                        }
                    });

                    mFusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                mCurrentLocation = location;
                                double lat = location.getLatitude();
                                double lon = location.getLongitude();
                                Log.i("TAG", "Localizacion " + lat + "   " + lon);
                                myRef.child("latitud").setValue(lat);
                                myRef.child("longitud").setValue(lon);
                            }
                        }
                    });
                } else {
                    Toast.makeText(this, "Funcionalidad Limitada!", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void quitarSolicitudes() {
        String [][] datos = {
                {"Jorge Paredes", "3.00 km"},
                {"Juan Ortiz", "4.00 km"}
        };
        textoSolicitudes.setText(R.string.peticion_estado);
        lv1.setAdapter(null);
    }

    private void mostrarSolicitudes() {
        if (switchD.isChecked()){
            textoSolicitudes.setText(R.string.seleccione);
            lv1.setAdapter(new Adaptador(contexto, datos));
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_layout, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if(item.getItemId()==R.id.signOutMenuItd){

            myRef = database.getReference(PATH_PASEADORES + user.getUid());
            myRef.child("estado").setValue(false);
            FirebaseAuth.getInstance().signOut();
            finish();
            startActivity(new Intent(getApplicationContext(), MainActivity.class));
        }
        return super.onOptionsItemSelected(item);
    }

    // Calcular distancia entre dos puntos
    public double distance(double lat1, double long1, double lat2, double long2) {
        double latDistance = Math.toRadians(lat1 - lat2);
        double lngDistance = Math.toRadians(long1 - long2);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double result = RADIUS_OF_EARTH_KM * c;
        return Math.round(result*100.0)/100.0;
    }
}
