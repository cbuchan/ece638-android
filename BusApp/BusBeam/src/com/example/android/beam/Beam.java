/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.beam;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SealedObject;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.StrictMode;
import android.provider.Settings;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;



public class Beam extends Activity implements CreateNdefMessageCallback,
        OnNdefPushCompleteCallback {
    NfcAdapter mNfcAdapter;
    private LocationManager locationManager;
    private String provider;
    TextView mInfoText;
    private static final int MESSAGE_SENT = 1;
    private static final String USER_APP_PREF_DATA= "com.example.android.userID";
    private static final String KEY = "com.example.android.beam.users";
	 static final String PROVIDER_NAME = LocationManager.GPS_PROVIDER;

    private LocationManager mLocationManager;
	private PendingIntent mPendingIntent;

	private final String LATITUDE = "latitude";
	private final String LONGITUDE = "longitude";
	private final String INDEX = "index";

	List<Location> gpxList;

	Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			// get the bundle and extract data by key
			Bundle b = msg.getData();

			int index = b.getInt(INDEX);

			sendLocation(b.getDouble(LATITUDE), b.getDouble(LONGITUDE),
					Beam.this);

			index = (index + 1) % gpxList.size();

			Message msgNext = new Message();
			Bundle bNext = new Bundle();
			bNext.putInt(INDEX, index);
			bNext.putDouble(LATITUDE,
					((Location) gpxList.get(index)).getLatitude());
			bNext.putDouble(LONGITUDE,
					((Location) gpxList.get(index)).getLongitude());
			msgNext.setData(bNext);

			handler.sendMessageDelayed(msgNext, 5000);

		}
	};

    
    
    
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    	StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

    	StrictMode.setThreadPolicy(policy);
    	
    	
    	
		String path = Environment.getExternalStorageDirectory().toString()
				+ "/gpx/course.gpx";

		TextView textInfo = (TextView) findViewById(R.id.textView);
		String info = "";

		File gpxFile = new File(path);
		info += gpxFile.getPath() + "\n\n";

		gpxList = decodeGPX(gpxFile);

		for (int i = 0; i < gpxList.size(); i++) {
			info += ((Location) gpxList.get(i)).getLatitude() + " : "
					+ ((Location) gpxList.get(i)).getLongitude() + "\n";
		}

		setupMockLocationProvider();

		if (gpxList.size() > 0) {
			// Kick off location loop
			Message msg = new Message();
			Bundle b = new Bundle();
			b.putInt(INDEX, 0);
			b.putDouble(LATITUDE, ((Location) gpxList.get(0)).getLatitude());
			b.putDouble(LONGITUDE, ((Location) gpxList.get(0)).getLongitude());
			msg.setData(b);

			handler.sendMessageDelayed(msg, 1000);
		}

		textInfo.setText(info);    	
        SharedPreferences settings = getApplicationContext().getSharedPreferences(USER_APP_PREF_DATA, 0);
        
        SharedPreferences.Editor editor = settings.edit();
        HashSet<String> users = new HashSet<String>();
        editor.putStringSet(KEY,users);

        mInfoText = (TextView) findViewById(R.id.textView);
        // Check for available NFC Adapter
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            mInfoText = (TextView) findViewById(R.id.textView);
            mInfoText.setText("NFC is not available on this device.");
        } else {
            // Register callback to set NDEF message
            mNfcAdapter.setNdefPushMessageCallback(this, this);
            // Register callback to listen for message-sent success
            mNfcAdapter.setOnNdefPushCompleteCallback(this, this);
        }
    }
    
    private void setupMockLocationProvider() {
    	mLocationManager =(LocationManager) getSystemService(Context.LOCATION_SERVICE);
    			
		if (mLocationManager.getProvider(PROVIDER_NAME) != null){
			Log.e("BEAM", mLocationManager.getAllProviders().toString());
			mLocationManager.removeTestProvider(PROVIDER_NAME);
		}

		mLocationManager.addTestProvider(PROVIDER_NAME, true, // requiresNetwork,
				false, // requiresSatellite,
				true, // requiresCell,
				false, // hasMonetaryCost,
				false, // supportsAltitude,
				false, // supportsSpeed,
				false, // supportsBearing,
				Criteria.POWER_MEDIUM, // powerRequirement
				Criteria.ACCURACY_FINE); // accuracy

	}

	/**
	 * Asynchronously update the mock location provider with given latitude and
	 * longitude
	 * 
	 * @param latitude
	 *            - update location
	 * @param longitude
	 *            - update location
	 * @param observer
	 *            - optionally, object to notify when update is sent.If null, no
	 *            update will be sent
	 */
	private void sendLocation(final double latitude, final double longitude,
			final Object observer) {
		Thread locationUpdater = new Thread(){
			@Override

			public void run() {
				Location loc = new Location(PROVIDER_NAME);
				loc.setLatitude(latitude);
				loc.setLongitude(longitude);
				loc.setAccuracy((float) 1.0);
				loc.setTime(java.lang.System.currentTimeMillis());

				Method locationJellyBeanFixMethod;
				try {
					locationJellyBeanFixMethod = Location.class
							.getMethod("makeComplete");
					if (locationJellyBeanFixMethod != null) {
						locationJellyBeanFixMethod.invoke(loc);
					}
				} catch (NoSuchMethodException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				mLocationManager.setTestProviderLocation(PROVIDER_NAME, loc);
				if (observer != null) {
					synchronized (observer) {
						observer.notify();
					}
				}

			}
		};
		locationUpdater.start();
	

	}

	protected void tearDown() throws Exception {
		mLocationManager.removeTestProvider(PROVIDER_NAME);
		if (mPendingIntent != null) {
			mLocationManager.removeProximityAlert(mPendingIntent);
		}
	}

	private List<Location> decodeGPX(File file) {
		List<Location> list = new ArrayList<Location>();

		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
				.newInstance();
		try {
			DocumentBuilder documentBuilder = documentBuilderFactory
					.newDocumentBuilder();
			FileInputStream fileInputStream = new FileInputStream(file);
			Document document = documentBuilder.parse(fileInputStream);
			Element elementRoot = document.getDocumentElement();

			NodeList nodelist_trkpt = elementRoot.getElementsByTagName("trkpt");

			for (int i = 0; i < nodelist_trkpt.getLength(); i++) {

				Node node = nodelist_trkpt.item(i);
				NamedNodeMap attributes = node.getAttributes();

				String newLatitude = attributes.getNamedItem("lat")
						.getTextContent();
				Double newLatitude_double = Double.parseDouble(newLatitude);

				String newLongitude = attributes.getNamedItem("lon")
						.getTextContent();
				Double newLongitude_double = Double.parseDouble(newLongitude);

				String newLocationName = newLatitude + ":" + newLongitude;
				Location newLocation = new Location(newLocationName);
				newLocation.setLatitude(newLatitude_double);
				newLocation.setLongitude(newLongitude_double);

				list.add(newLocation);

			}

			fileInputStream.close();

		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return list;
	}





    /**
     * Implementation for the CreateNdefMessageCallback interface
     */
    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        Time time = new Time();
        time.setToNow();
        String text = ("This is the Bus, Static Bus Information will go here!\n\n" +
                "Beam Time: " + time.format("%H:%M:%S"));
        NdefMessage msg = new NdefMessage(NdefRecord.createMime(
                "application/com.example.android.beam", text.getBytes())
         /**
          * The Android Application Record (AAR) is commented out. When a device
          * receives a push with an AAR in it, the application specified in the AAR
          * is guaranteed to run. The AAR overrides the tag dispatch system.
          * You can add it back in to guarantee that this
          * activity starts when receiving a beamed message. For now, this code
          * uses the tag dispatch system.
          */
          //,NdefRecord.createApplicationRecord("com.example.android.beam")
        );
        return msg;
    }

    /**
     * Implementation for the OnNdefPushCompleteCallback interface
     */
    @Override
    public void onNdefPushComplete(NfcEvent arg0) {
        // A handler is needed to send messages to the activity when this
        // callback occurs, because it happens from a binder thread
        mHandler.obtainMessage(MESSAGE_SENT).sendToTarget();
    }

    /** This handler receives a message from onNdefPushComplete */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_SENT:
                Toast.makeText(getApplicationContext(), "Message sent!", Toast.LENGTH_LONG).show();
                break;
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        // Check to see that the Activity started due to an Android Beam
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }

    /**
     * Parses the NDEF Message from the intent and prints to the TextView
     */
    void processIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        Log.i("Test", "parse");
        // only one message sent during the beam
        NdefMessage msg = (NdefMessage) rawMsgs[0];

        String ndefinput = new String(msg.getRecords()[0].getPayload());
        //Getting location when receiving NFC content
        //LocationManager mLocationManager = new LocationManager();
        
        Passenger pass = new Passenger();
        pass.setId(ndefinput);
        Time time = new Time();
        time.setToNow();
        pass.setTime(time.format("%H:%M:%S"));
        
        SharedPreferences settings = getApplicationContext().getSharedPreferences(USER_APP_PREF_DATA, 0);
        HashSet<String> users = (HashSet<String>) settings.getStringSet(KEY, null);
        
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        // Define the criteria how to select the location provider -> use
        // default
        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, false);
        Location location = locationManager.getLastKnownLocation(provider);
    
        
        pass.setLongitude(location.getLongitude());
        pass.setLattitude(location.getLatitude());
        Log.i("Test", provider);

        //If record exists, remove record
        //if record doesn't exist, add record
        if(users == null || users.isEmpty()){
        	  Log.i("Test", "Empty users");
        	  users = new HashSet<String>();
        	  users.add(ndefinput);
        }else if(users.contains(ndefinput)==true){
        	pass.setAction("Leaving");
        	Log.i("Test", "User Present");
        	try{  
            	Socket s = new Socket("128.237.124.135",3000);  
            	OutputStream os = s.getOutputStream();
            	//ObjectOutputStream oos = new ObjectOutputStream(os);  
            	SecretKey key64 = new SecretKeySpec( new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07 }, "Blowfish" );
            	Cipher cipher = Cipher.getInstance( "Blowfish" );

            	//Code to write your object to file
            	cipher.init( Cipher.ENCRYPT_MODE, key64 );
            	
            	SealedObject sealedObject = new SealedObject(pass.getId()+":"+pass.getAction()+":"+pass.getLattitude()+":"+pass.getLongitude(), cipher);
            	//CipherOutputStream cipherOutputStream = new CipherOutputStream( os, cipher );
            	ObjectOutputStream outputStream = new ObjectOutputStream( os );
            	outputStream.writeObject( sealedObject );
            	outputStream.flush();
            	//outputStream.close();
            	//ObjectOutputStream oos = new ObjectOutputStream(os);  
            	//oos.writeObject(pass.getId()+":"+pass.getAction()+":"+pass.getLattitude()+":"+pass.getLongitude());
            	
            	//oos.close();  
            	//os.close();  
            	//s.close(); 
            	 
            	}catch(Exception e){System.out.println(e);}
        	users.remove(ndefinput);
        	
        } else{
        	Log.i("Test", "User not present");
        	pass.setAction("Authenticating");
        	int authresult=0;
        	try{  
            	Socket s = new Socket("128.237.124.135",3000);
            	OutputStream os = s.getOutputStream();  
            	InputStream is = s.getInputStream();
            	//ObjectOutputStream oos = new ObjectOutputStream(os);  
            	SecretKey key64 = new SecretKeySpec( new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07 }, "Blowfish" );
            	Cipher cipher = Cipher.getInstance( "Blowfish" );

            	//Code to write your object to file
            	cipher.init( Cipher.ENCRYPT_MODE, key64 );
            	
            	SealedObject sealedObject = new SealedObject(pass.getId()+":"+pass.getAction()+":"+pass.getLattitude()+":"+pass.getLongitude(), cipher);
            	//CipherOutputStream cipherOutputStream = new CipherOutputStream( os, cipher );
            	ObjectOutputStream outputStream = new ObjectOutputStream( os);
            	outputStream.writeObject( sealedObject );
            	outputStream.flush();
            	//outputStream.close();
            	

            	ObjectInputStream ois = new ObjectInputStream(is);
            	//oos.writeObject(pass.getId()+":"+pass.getAction()+":"+pass.getLattitude()+":"+pass.getLongitude()); 
       
            	
				authresult = (Integer) ois.readObject();
     
            	 
            	}catch(Exception e){System.out.println(e);}
        	if(authresult>0){
        		users.add(ndefinput);
        	}else System.out.println("User not Found\n");
        }
        
       
        Log.i("Test", "Added Objects");

        mInfoText.setText("");
        for ( String s : users)  
        {  
            mInfoText.append(s +"\n" + location.toString() +"\n");  
        } 
       SharedPreferences.Editor editor = settings.edit();
       editor.putStringSet(KEY,users);
       editor.commit();
       Log.i("Test", " "+users.size());
        
        // record 0 contains the MIME type, record 1 is the AAR, if present
       // mInfoText.setText(new String(msg.getRecords()[0].getPayload()));
       // mInfoText.append(locationGPS.toString());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // If NFC is not available, we won't be needing this menu
        if (mNfcAdapter == null) {
            return super.onCreateOptionsMenu(menu);
        }
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                Intent intent = new Intent(Settings.ACTION_NFCSHARING_SETTINGS);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
