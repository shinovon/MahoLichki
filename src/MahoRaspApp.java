import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.DateField;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class MahoRaspApp extends MIDlet implements CommandListener, ItemStateListener, ItemCommandListener, Runnable {

	private static final Command exitCmd = new Command("Выход", Command.EXIT, 1);
	private static final Command submitCmd = new Command("Искать", Command.ITEM, 2);
	private static final Command chooseCmd = new Command("Выбрать город", Command.ITEM, 2);
	private static final Command itemCmd = new Command("Остановки", Command.ITEM, 2);
	protected static final Command okCmd = new Command("Ок", Command.OK, 1);
	private static final Command backCmd = new Command("Назад", Command.BACK, 1);
	
	private boolean started;
	
	public static MahoRaspApp midlet;

	public static JSONObject zones;
	
	private Form mainForm;
	private Form loadingForm;
	
	private DateField dateField;
	private StringItem submitBtn;
	private StringItem text;
	private StringItem fromBtn;
	private StringItem toBtn;
	
	private int fromZoneId;
	private String fromZoneName;
	private String fromSettlement;
	private String fromStation; // esr
	
	private int toZoneId;
	private String toZoneName;
	private String toSettlement;
	private String toStation; // esr
	
	private int choice;
	private Alert progressAlert;
	private int downloadZone;
	private int run;
	private boolean running;
	private String itemNumber;
	private String searchDate;

	public MahoRaspApp() {
		midlet = this;
		loadingForm = new Form("Загрузка");
		mainForm = new Form("Выф");
		dateField = new DateField("Дата", DateField.DATE);
		dateField.setDate(new Date(System.currentTimeMillis()));
		mainForm.append(dateField);
		fromBtn = new StringItem("Откуда", "Не выбрано", StringItem.BUTTON);
		fromBtn.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		fromBtn.addCommand(chooseCmd);
		fromBtn.setDefaultCommand(chooseCmd);
		fromBtn.setItemCommandListener(this);
		mainForm.append(fromBtn);
		toBtn = new StringItem("Куда", "Не выбрано", StringItem.BUTTON);
		toBtn.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		toBtn.addCommand(chooseCmd);
		toBtn.setDefaultCommand(chooseCmd);
		toBtn.setItemCommandListener(this);
		mainForm.append(toBtn);
		submitBtn = new StringItem(null, "Поиск", StringItem.BUTTON);
		submitBtn.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		submitBtn.addCommand(submitCmd);
		submitBtn.setDefaultCommand(submitCmd);
		submitBtn.setItemCommandListener(this);
		mainForm.append(submitBtn);
		text = new StringItem(null, "");
		text.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_BEFORE);
		mainForm.append(text);
		mainForm.setCommandListener(this);
		mainForm.setItemStateListener(this);
		mainForm.addCommand(exitCmd);
	}

	protected void destroyApp(boolean arg0) {
	}

	protected void pauseApp() {
	}

	protected void startApp() {
		if(started) return;
		started = true;
		display(loadingForm);
		try {
			zones = JSON.getObject(new String(readBytes("".getClass().getResourceAsStream("/zones.json")), "UTF-8"));
		} catch (Exception e) {
			e.printStackTrace();
			loadingForm.append(e.toString());
			return;
		}
		display(mainForm);
	}
	
	public void commandAction(Command c, Item i) {
		if(running) return; // не реагировать если сейчас что-то грузится
		if(c == chooseCmd) {
			choice = i == fromBtn ? 1 : 2;
			Alert a = new Alert("");
			a.setString("Выбрать станцию или город?");
			a.addCommand(new Command("Станция", Command.OK, 1));
			a.addCommand(new Command("Город", Command.CANCEL, 2));
			a.setCommandListener(this);
			display(a);
			return;
		}
		if(c == itemCmd) {
			itemNumber = i.getLabel();
			Alert a = new Alert("");
			a.setIndicator(new Gauge(null, false, Gauge.INDEFINITE, Gauge.CONTINUOUS_RUNNING));
			a.setString("Загрузка");
			a.setTimeout(5000);
			display(a);
			run = 3;
			new Thread(this).start();
		}
		this.commandAction(c, mainForm);
	}

	public void select(int type, String s, String s2) {
		if(type == 1) {
			int id = zones.getObject(s).getInt("i");
			if(choice == 1) {
				fromZoneName = s;
				fromZoneId = id;
			} else {
				toZoneName = s;
				toZoneId = id;
			}
			/*
			Alert a = new Alert("");
			a.addCommand(new Command("города", Command.OK, 3));
			a.addCommand(new Command("станции", Command.CANCEL, 4));
			a.setCommandListener(this);
			Display.getDisplay(midlet).setCurrent(a);
			*/
			try {
				RecordStore rs = RecordStore.openRecordStore("mahoRS_"+id, false);
				JSONArray r = JSON.getArray(new String(rs.getRecord(1), "UTF-8"));
				display(new ChoiceForm(3, id, r));
				rs.closeRecordStore();
			} catch (Exception e) {
				downloadZone = id;
				Alert a = new Alert("");
				a.setString("Станции зоны \"" + s + "\" не найдены в кэше. Загрузить?");
				a.addCommand(new Command("Загрузить", Command.OK, 4));
				a.addCommand(new Command("Выбрать город", Command.CANCEL, 3));
				a.setCommandListener(this);
				display(a);
			}
			return;
		}
		if(type == 2) {
			if(choice == 1) {
				fromBtn.setText(fromSettlement = s);
			} else {
				toBtn.setText(toSettlement = s);
			}
			display(mainForm);
			return;
		}
		if(type == 3) {
			if(choice == 1) {
				fromStation = s;
				fromBtn.setText(s2);
			} else {
				toStation = s;
				toBtn.setText(s2);
			}
			display(mainForm);
			return;
		}
	}

	public void commandAction(Command c, Displayable d) {
		if(c == exitCmd) {
			notifyDestroyed();
			return;
		}
		if(c == okCmd || c == backCmd) {
			display(mainForm);
			return;
		}
		if(c == Alert.DISMISS_COMMAND) {
			return;
		}
		if(d instanceof Alert) {
			switch (c.getPriority()) {
			case 1:
				display(new ChoiceForm(1, choice == 1 ? fromZoneId : toZoneId, null));
				break;
			case 2:
				if(choice == 1)
					fromZoneId = 0;
				else
					toZoneId = 0;
				display(new ChoiceForm(2, 0, null));
				break;
			case 3:
				display(new ChoiceForm(2, choice == 1 ? fromZoneId : toZoneId, null));
				break;
			case 4:
				progressAlert = new Alert("");
				progressAlert.setTimeout(Alert.FOREVER);
				progressAlert.setIndicator(new Gauge(null, false, Gauge.INDEFINITE, Gauge.CONTINUOUS_RUNNING));
				progressAlert.setTitle("Загрузка станций");
				progressAlert.removeCommand(Alert.DISMISS_COMMAND);
				display(progressAlert);
				run = 1;
				new Thread(this).start();
				break;
			}
		}
		if(c == submitCmd) {
			if(running) return;
			if(fromSettlement == null && fromStation == null) {
				text.setText("не выбран пункт отправки");
				return;
			}
			if(toSettlement == null && toStation == null) {
				text.setText("не выбран пункт назначения");
				return;
			}
			mainForm.deleteAll();
			mainForm.append(dateField);
			mainForm.append(fromBtn);
			mainForm.append(toBtn);
			mainForm.append(submitBtn);
			mainForm.append(text);
			//Display.getDisplay(this).setCurrentItem(text);
			run = 2;
			new Thread(this).start();
			return;
		}
	}
	
	public void run() {
		running = true;
		switch(run) {
		case 1:
			try {
				progressAlert.setTitle("Скачивание");
				Enumeration e = api("zone/" + downloadZone).getArray("zone_stations").elements();
				progressAlert.setString("Парсинг");
				JSONArray r = new JSONArray();
				while(e.hasMoreElements()) {
					JSONObject s = (JSONObject) e.nextElement();
					JSONObject rs = new JSONObject();
					rs.put("d", s.getNullableString("direction"));
					rs.put("t", s.getString("title"));
					rs.put("i", s.getString("esr"));
					r.add(rs);
				}
				progressAlert.setString("Запись");
				e = null;
				RecordStore rs = RecordStore.openRecordStore("mahoRS_" + downloadZone, true);
				byte[] b = r.toString().getBytes("UTF-8");
				rs.addRecord(b, 0, b.length);
				rs.closeRecordStore();
				rs = null;
				display(new ChoiceForm(3, downloadZone, r));
				return;
			} catch (Exception e) {
				progressAlert.setString(e.toString());
			}
			progressAlert.setCommandListener(midlet);
			progressAlert.addCommand(okCmd);
			break;
		case 2:
			try {
				String city_from = null;
				String city_to = null;
				if(fromStation == null || toStation == null) {
					if(fromZoneId != 0 && fromSettlement != null) 
						city_from = zones.getObject(fromZoneName).getObject("s").getString(fromSettlement);
					if(toZoneId != 0 && toSettlement != null)
						city_to = zones.getObject(toZoneName).getObject("s").getString(toSettlement);
					if(city_from == null || city_to == null) {
						Enumeration e = MahoRaspApp.zones.getTable().elements();
						while(e.hasMoreElements()) {
							JSONObject j = ((JSONObject) e.nextElement()).getObject("s");
							if(city_from == null && fromSettlement != null) city_from = j.getNullableString(fromSettlement);
							if(city_to == null && toSettlement != null) city_to = j.getNullableString(toSettlement);
							if((city_from != null || fromSettlement == null) && (city_to != null || toSettlement == null)) break;
						}
					}
				}
				Calendar cal = Calendar.getInstance();
				cal.setTime(dateField.getDate());
				searchDate = cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH);
				text.setLabel("Результаты");
				text.setText("Загрузка");
				String params = (fromStation != null ? ("station_from=" + fromStation) : ("city_from=" + city_from)) + "&" + (toStation != null ? ("station_to=" + toStation) : ("city_to=" + city_to));
				JSONObject j = api("search_on_date?date=" + searchDate + "&" + params);

				text.setText("");
				long server_time = timestamp(j.getObject("date_time").getString("server_time"));
				
				for(Enumeration e2 = j.getArray("days").elements(); e2.hasMoreElements();) {
					
					JSONObject day = (JSONObject) e2.nextElement();
					//mainForm.append(day.getString("date") + "\n");
					for(Enumeration e3 = day.getArray("segments").elements(); e3.hasMoreElements();) {
						JSONObject seg = (JSONObject) e3.nextElement();

						JSONObject departure = seg.getObject("departure");
						// пропускать ушедшие
						if(timestamp(departure.getString("time_utc")) < server_time) continue;
						
						JSONObject thread = seg.getObject("thread");
						JSONObject arrival = seg.getObject("arrival");

						String res = "";
						if(arrival.has("platform")) {
							res += arrival.getString("platform") + "\n";
						}
						res += "\n" + time(departure.getString("time"));
						res += " - ";
						res += time(arrival.getString("time"));
						res += " (" + seg.getString("duration") + " мин)";
						res += thread.getString("title_short", thread.getString("title")) + "\n";
						
						JSONObject tariff = seg.getNullableObject("tariff");
						if(tariff != null) res += "\n" + tariff.getString("value") + " " + tariff.getString("currency");
						res += "\n";

						StringItem s = new StringItem(thread.getString("number"), res);
						s.setFont(Font.getFont(0, 0, 8));
						//s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_BEFORE);
						s.addCommand(itemCmd);
						s.setDefaultCommand(itemCmd);
						s.setItemCommandListener(this);
						mainForm.append(s);
					}
					//res += "----\n";
				}
				//text.setText(res);
			} catch (Exception e) {
				e.printStackTrace();
				text.setText(e.toString());
			}
			break;
		case 3:
			try {
				// TODO
//				api("station_schedule_on_date/" + itemNumber + "?date=" + searchDate);
				Form f = new Form(itemNumber);
				f.addCommand(backCmd);
				f.setCommandListener(this);
				display(f);
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;
		}
		running = false;
		run = 0;
	}
	
	public void cancelChoice() {
		if(choice == 1) {
			fromZoneName = null;
			fromSettlement = null;
			fromStation = null;
		} else {
			toZoneName = null;
			toSettlement = null;
			toStation = null;
		}
		display(mainForm);
	}

	private void display(Displayable d) {
		if(d instanceof Alert) {
			Display.getDisplay(this).setCurrent((Alert) d, mainForm);
			return;
		}
		Display.getDisplay(this).setCurrent(d);
	}

	public void itemStateChanged(Item item) {
	}
	
	public static byte[] readBytes(InputStream is) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final byte[] buf = new byte[128];
		int read;
		while ((read = is.read(buf)) != -1) {
			baos.write(buf, 0, read);
		}
		is.close();
		byte[] b = baos.toByteArray();
		baos.close();
		return b;
	}
	
	private static String n(int n) {
		if(n < 10) {
			return "0".concat(i(n));
		} else return i(n);
	}
	
	private static String i(int n) {
		return Integer.toString(n);
	}
	
	public static Calendar parseDate(String date) {
		Calendar c = Calendar.getInstance();
		if(date.indexOf('T') != -1) {
			String[] dateSplit = split(date.substring(0, date.indexOf('T')), '-');
			String[] timeSplit = split(date.substring(date.indexOf('T')+1), ':');
			String second = split(timeSplit[2], '.')[0];
			int i = second.indexOf('+');
			if(i == -1) {
				i = second.indexOf('-');
			}
			//try {
				if(i != -1) {
					//String timezone = second.substring(i+1);
					second = second.substring(0, i);
					//if(timezone.startsWith("3")) {
					//	c.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
					//}
				}
			//} catch (Exception e) {
			//	e.printStackTrace();
			//}
			c.set(Calendar.YEAR, Integer.parseInt(dateSplit[0]));
			c.set(Calendar.MONTH, Integer.parseInt(dateSplit[1])-1);
			c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dateSplit[2]));
			c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeSplit[0]));
			c.set(Calendar.MINUTE, Integer.parseInt(timeSplit[1]));
			c.set(Calendar.SECOND, Integer.parseInt(second));
		} else {
			String[] dateSplit = split(date, '-');
			c.set(Calendar.YEAR, Integer.parseInt(dateSplit[0]));
			c.set(Calendar.MONTH, Integer.parseInt(dateSplit[1])-1);
			c.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dateSplit[2]));
		}
		return c;
	}

	public static String time(String date) {
		Calendar c = parseDate(date);
		//return c.get(Calendar.DAY_OF_MONTH) + " " + localizeMonthWithCase(c.get(Calendar.MONTH)) + " " +
		return n(c.get(Calendar.HOUR_OF_DAY)) + ":" + n(c.get(Calendar.MINUTE));
	}
	

	public static long timestamp(String date) {
		return parseDate(date).getTime().getTime();
	}
	
	static String localizeMonthWithCase(int month) {
		switch(month) {
		case Calendar.JANUARY:
			return "января";
		case Calendar.FEBRUARY:
			return "февраля";
		case Calendar.MARCH:
			return "марта";
		case Calendar.APRIL:
			return "апреля";
		case Calendar.MAY:
			return "мая";
		case Calendar.JUNE:
			return "июня";
		case Calendar.JULY:
			return "июля";
		case Calendar.AUGUST:
			return "августа";
		case Calendar.SEPTEMBER:
			return "сентября";
		case Calendar.OCTOBER:
			return "октября";
		case Calendar.NOVEMBER:
			return "ноября";
		case Calendar.DECEMBER:
			return "декабря";
		default:
			return "";
		}
	}
	
	public static String[] split(String str, char d) {
		int i = str.indexOf(d);
		if(i == -1)
			return new String[] {str};
		Vector v = new Vector();
		v.addElement(str.substring(0, i));
		while(i != -1) {
			str = str.substring(i + 1);
			if((i = str.indexOf(d)) != -1)
				v.addElement(str.substring(0, i));
			i = str.indexOf(d);
		}
		v.addElement(str);
		String[] r = new String[v.size()];
		v.copyInto(r);
		return r;
	}
	
	public static String url(String url) {
		StringBuffer sb = new StringBuffer();
		char[] chars = url.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			int c = chars[i];
			if (65 <= c && c <= 90) {
				sb.append((char) c);
			} else if (97 <= c && c <= 122) {
				sb.append((char) c);
			} else if (48 <= c && c <= 57) {
				sb.append((char) c);
			} else if (c == 32) {
				sb.append("%20");
			} else if (c == 45 || c == 95 || c == 46 || c == 33 || c == 126 || c == 42 || c == 39 || c == 40
					|| c == 41) {
				sb.append((char) c);
			} else if (c <= 127) {
				sb.append(hex(c));
			} else if (c <= 2047) {
				sb.append(hex(0xC0 | c >> 6));
				sb.append(hex(0x80 | c & 0x3F));
			} else {
				sb.append(hex(0xE0 | c >> 12));
				sb.append(hex(0x80 | c >> 6 & 0x3F));
				sb.append(hex(0x80 | c & 0x3F));
			}
		}
		return sb.toString();
	}

	private static String hex(int i) {
		String s = Integer.toHexString(i);
		return "%".concat(s.length() < 2 ? "0" : "").concat(s);
	}
	
	public static byte[] get(String url) throws IOException {
		ByteArrayOutputStream o = null;
		HttpConnection hc = null;
		InputStream in = null;
		try {
			hc = (HttpConnection) Connector.open(url);
			hc.setRequestMethod("GET");
			hc.getResponseCode();
			//if(r != 200) throw new IOException(r + " " + hc.getResponseMessage());
			in = hc.openInputStream();
			int read;
			o = new ByteArrayOutputStream();
			byte[] b = new byte[512];
			while((read = in.read(b)) != -1) {
				o.write(b, 0, read);
			}
			return o.toByteArray();
		} finally {
			try {
				if (in != null) in.close();
			} catch (IOException e) {
			}
			try {
				if (hc != null) hc.close();
			} catch (IOException e) {
			}
			try {
				if (o != null) o.close();
			} catch (IOException e) {
			}
		}
	}
	
	public static String getUtf(String url) throws IOException {
		return new String(get(url), "UTF-8");
	}
	
	public static JSONObject api(String url) throws Exception {
		String r = getUtf("http://export.rasp.yandex.net/v3/suburban/" + url);
		JSONObject j = JSON.getObject(r);
		if(j.has("error")) {
			throw new Exception(j.getObject("error").getString("text"));
		}
		return j;
	}

}
