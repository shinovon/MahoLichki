import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.TimeZone;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.DateField;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class MahoRaspApp extends MIDlet implements CommandListener, ItemStateListener, ItemCommandListener {

	private static final Command exitCmd = new Command("Выход", Command.EXIT, 1);
	private static final Command findCmd = new Command("Искать", Command.ITEM, 2);
	
	private boolean started;

	private JSONObject cities;
	
	private Form mainForm;
	private Form loadingForm;
	
	private DateField dateField;
	private TextField fromField;
	private TextField toField;
	private StringItem find;
	private StringItem text;

	public MahoRaspApp() {
		loadingForm = new Form("Загрузка");
		mainForm = new Form("Выф");
		dateField = new DateField("Дата", DateField.DATE);
		dateField.setDate(new Date(System.currentTimeMillis()));
		mainForm.append(dateField);
		mainForm.append(fromField = new TextField("Откуда", "", 256, TextField.ANY));
		mainForm.append(toField = new TextField("Куда", "", 256, TextField.ANY));
		find = new StringItem(null, "Ввод", StringItem.BUTTON);
		find.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		find.addCommand(findCmd);
		find.setDefaultCommand(findCmd);
		find.setItemCommandListener(this);
		mainForm.append(find);
		text = new StringItem(null, "");
		text.setLayout(Item.LAYOUT_NEWLINE_BEFORE);
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
		Display.getDisplay(this).setCurrent(loadingForm);
		try {
			cities = JSON.getObject(new String(readBytes("".getClass().getResourceAsStream("/cities.json")), "UTF-8"));
		} catch (Exception e) {
			e.printStackTrace();
			loadingForm.append(e.toString());
			return;
		}
		Display.getDisplay(this).setCurrent(mainForm);
	}
	
	public void commandAction(Command c, Item i) {
		this.commandAction(c, mainForm);
	}

	public void commandAction(Command c, Displayable d) {
		if(c == exitCmd) {
			notifyDestroyed();
			return;
		}
		if(c == findCmd) {
			String c1 = fromField.getString().trim().toLowerCase();
			String c2 = toField.getString().trim().toLowerCase();
			
			String city_from = null;
			String city_to = null;
			
			for(Enumeration e = cities.keys(); e.hasMoreElements(); ) {
				String k = (String) e.nextElement();
				if(k.equalsIgnoreCase(c1)) {
					city_from = cities.getString(k);
					System.out.println("from found: " + city_from);
				}
				if(k.equalsIgnoreCase(c2)) {
					city_to = cities.getString(k);
					System.out.println("to found: " + city_to);
				}
				if(city_from != null && city_to != null) {
					break;
				}
			}
			
			if(city_from == null || city_to == null) {
				text.setText("город введен неправильно");
				return;
			}
			
			Calendar cal = Calendar.getInstance();
			cal.setTime(dateField.getDate());
			String date = cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH);
			try {
				text.setLabel("Результаты");
				JSONObject j = api("search_on_date?city_from=" + city_from + "&city_to=" + city_to + "&date=" + date);
				//text.setText(j.toString());
				String res = "";
				JSONArray days = j.getArray("days");
				for(Enumeration e2 = days.elements(); e2.hasMoreElements();) {
					JSONObject day = (JSONObject) e2.nextElement();
					res += day.getString("date") + ":\n";
					JSONArray segments = day.getArray("segments");
					for(Enumeration e3 = segments.elements(); e3.hasMoreElements();) {
						JSONObject seg = (JSONObject) e3.nextElement();
						
						JSONObject thread = seg.getObject("thread");
						res += thread.getString("number") + " " + thread.getString("title") + "\n";
						res += date(seg.getObject("departure").getString("time"));
						res += " - ";
						res += date(seg.getObject("arrival").getString("time")) + "\n";
						res += seg.getString("duration") + " мин.";
						if(thread.has("stops")) {
							res += " Остановки: " + thread.getString("stops");
						}
						res += "\n";
						
						
						JSONObject tariff = seg.getObject("tariff");
						res += tariff.getString("value") + " " + tariff.getString("currency") +"\n";
						res += "--\n";
					}
					res += "----\n";
				}
				text.setText(res);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
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

	public static String date(String date) {
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
		return c.get(Calendar.DAY_OF_MONTH) + " " + localizeMonthWithCase(c.get(Calendar.MONTH)) + " " +
		n(c.get(Calendar.HOUR_OF_DAY)) + ":" + n(c.get(Calendar.MINUTE));
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
			int r = hc.getResponseCode();
			if(r != 200) throw new IOException(r + " " + hc.getResponseMessage());
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
