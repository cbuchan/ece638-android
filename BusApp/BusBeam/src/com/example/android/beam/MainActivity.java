package com.example.android.beam;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

public class MainActivity extends Activity {

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
					MainActivity.this);

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

	private static final String PROVIDER_NAME = LocationManager.GPS_PROVIDER;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		Log.i("BEAM", "Main Activity");

		String path = Environment.getExternalStorageDirectory().toString()
				+ "/gpx/61S.gpx";

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

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.options, menu);
		return true;
	}

	private void setupMockLocationProvider() {
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (mLocationManager.getProvider(PROVIDER_NAME) != null) {
			// mLocationManager.removeTestProvider(PROVIDER_NAME);
		}

		mLocationManager.addTestProvider(PROVIDER_NAME, true, // requiresNetwork,
				false, // requiresSatellite,
				true, // requiresCell,
				false, // hasMonetaryCost,
				false, // supportsAltitude,
				false, // supportsSpeed, s
				false, // upportsBearing,
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
		Thread locationUpdater = new Thread() {
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

}
