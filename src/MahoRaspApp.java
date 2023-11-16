import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
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
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class MahoRaspApp extends MIDlet implements CommandListener, ItemCommandListener, Runnable, ItemStateListener {
	
	// Функции главной формы
	private static final Command exitCmd = new Command("Выход", Command.EXIT, 1);
	private static final Command showGoneCmd = new Command("Показать ушедшие", Command.SCREEN, 3);
	private static final Command hideGoneCmd = new Command("Скрыть ушедшие", Command.SCREEN, 3);
	private static final Command bookmarksCmd = new Command("Закладки", Command.SCREEN, 4);
	private static final Command addBookmarkCmd = new Command("Добавить в закладки", Command.SCREEN, 5);
	private static final Command cacheScheduleCmd = new Command("Сохранить", Command.SCREEN, 6);
	private static final Command settingsCmd = new Command("Настройки", Command.SCREEN, 7);
	private static final Command aboutCmd = new Command("О программе", Command.SCREEN, 8);

	// Функции элементов
	private static final Command submitCmd = new Command("Искать", Command.ITEM, 2);
	private static final Command chooseCmd = new Command("Выбрать", Command.ITEM, 2);
	private static final Command itemCmd = new Command("Остановки", Command.ITEM, 2);
	private static final Command clearStationsCmd = new Command("Очистить станции", Command.ITEM, 2);
	private static final Command clearScheduleCmd = new Command("Очистить расписания", Command.ITEM, 2);
	private static final Command removeCmd = new Command("Удалить", Command.ITEM, 2);
	
	// Команды диалогов
	protected static final Command okCmd = new Command("Ок", Command.OK, 1);
	private static final Command backCmd = new Command("Назад", Command.BACK, 1);
	
	// Команды формы поиска
	private static final Command searchCmd = new Command("Поиск", Command.ITEM, 2);
	private static final Command doneCmd = new Command("Готово", Command.OK, 1);
	private static final Command cancelCmd = new Command("Отмена", Command.CANCEL, 1);
	
	// Список настроек типа on/off
	private static final String[] SETTINGS = new String[] {
			// TODO
	};
	
	// Константы названий RecordStore
	private static final String SETTINGS_RECORDNAME = "mahoLsets";
	private static final String STATIONS_RECORDPREFIX = "mahoLS_";
	private static final String SCHEDULE_RECORDPREFIX = "mahoLD";
	private static final String BOOKMARKS_RECORDNAME = "mahoLbm";
	
	private boolean started;
	
	public static MahoRaspApp midlet;

	// бд зон и городов
	private int[][] zonesAndCities;
	private String[] zoneNames;
	private int[] cityIds;
	private String[] cityNames;
	
	// Настройки
	private int defaultChoiceType;
	
	private Form mainForm;
	private Form loadingForm;
	private Form settingsForm;
	
	// UI главной
	private DateField dateField;
	private StringItem submitBtn;
	private StringItem text;
	private StringItem fromBtn;
	private StringItem toBtn;

	// UI настроек
	private ChoiceGroup settingsDefaultTypeChoice;
	private ChoiceGroup settingsMultipleChoice;
	
	// точка А
	private int fromZone;
	private int fromCity; // title
	private String fromStation; // esr
	
	// точка Б
	private int toZone;
	private int toCity; // title
	private String toStation; // esr
	
	private int choosing; // 1 - отправление, 2 - прибытие
	private Alert progressAlert;
	private int downloadZone;
	private int run;
	private boolean running;
	private String threadUid;
	private String searchDate;
	private boolean showGone;
	private Hashtable uids;
	private String scheduleRecordName;
	private String clear;
	private JSONArray bookmarks;
	private String searchParams;
	
	// форма поиска
	private Form searchForm;
	private int searchType; // 1 - зона, 2 - город, 3 - станция
	private int searchZone;
	private TextField searchField;
	private ChoiceGroup searchChoice;
	private JSONArray searchStations;
	private boolean searchCancel;

	/// UI
	
	public MahoRaspApp() {
		midlet = this;
		uids = new Hashtable();
		loadingForm = new Form("Загрузка");
		mainForm = new Form("Махолички");
		mainForm.addCommand(exitCmd);
		mainForm.addCommand(showGone ? hideGoneCmd : showGoneCmd);
		mainForm.addCommand(bookmarksCmd);
		mainForm.addCommand(settingsCmd);
		mainForm.addCommand(aboutCmd);
		mainForm.addCommand(addBookmarkCmd);
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
	}

	protected void destroyApp(boolean arg0) {
	}

	protected void pauseApp() {
	}

	protected void startApp() {
		if(started) return;
		started = true;
		display(loadingForm);
		// парс зон
		try {
			JSONObject j = JSON.getObject(new String(readBytes("".getClass().getResourceAsStream("/zones.json")), "UTF-8"));
			int i = 0;
			int l = j.size();
			zonesAndCities = new int[l][];
			zoneNames = new String[l];
			Vector cityIdsTmp = new Vector();
			Vector cityNamesTmp = new Vector();
			int m = 0;
			Enumeration e = j.keys();
			while(e.hasMoreElements()) {
				String k;
				zoneNames[i] = (k = (String) e.nextElement());
				JSONObject c = j.getObject(k);
				int n = c.getInt("i");
				(zonesAndCities[i] = new int[(c = c.getObject("s")).size() + 1])[0] = n;
				Enumeration e2 = c.keys();
				n = 1;
				while(e2.hasMoreElements()) {
					String s;
					cityNamesTmp.addElement((s = (String) e2.nextElement()));
					cityIdsTmp.addElement(new Integer(c.getInt(s)));
					zonesAndCities[i][n++] = m++;
				}
				i++;
			}
			cityNames = new String[l = cityNamesTmp.size()];
			cityIds = new int[l];
			cityNamesTmp.copyInto(cityNames);
			for(i = 0; i < l; i++) {
				cityIds[i] = ((Integer) cityIdsTmp.elementAt(i)).intValue();
			}
		} catch (Exception e) {
			e.printStackTrace();
			loadingForm.append(e.toString());
			return;
		}
		// загрузка настроек
		try {
			RecordStore r = RecordStore.openRecordStore(SETTINGS_RECORDNAME, false);
			JSONObject j = JSON.getObject(new String(r.getRecord(1), "UTF-8"));
			r.closeRecordStore();
			defaultChoiceType = j.getInt("defaultChoiceType", defaultChoiceType);
		} catch (Exception e) {
		}
		display(mainForm);
		loadingForm = null;
		if(isJ2MEL()) {
			// https://github.com/nikita36078/J2ME-Loader/issues/1024
			Alert a = new Alert("Внимание");
			a.setString("J2ME Loader поддерживается только с патчем #1026\nЕсли 1.8.0 уже вышла и у вас стоит она, то игнорируйте это сообщение.");
			a.setTimeout(5000);
			display(a);
		}
	}
	
	public void commandAction(Command c, Item i) {
		if(running) return; // не реагировать если сейчас что-то грузится
		// нажатие на куда / откуда
		if(c == chooseCmd) {
			choosing = i == fromBtn ? 1 : 2;
			if(defaultChoiceType == 1) {
				if(choosing == 1)
					fromZone = 0;
				else
					toZone = 0;
				display(searchForm(2, 0, null));
				return;
			}
			if(defaultChoiceType == 2) {
				display(searchForm(1, choosing == 1 ? fromZone : toZone, null));
				return;
			}
			Alert a = new Alert("");
			a.setString("Выбрать станцию или город?");
			a.addCommand(new Command("Станция", Command.OK, 1));
			a.addCommand(new Command("Город", Command.CANCEL, 2));
			a.setCommandListener(this);
			display(a);
			return;
		}
		// нажатие на элемент расписания
		if(c == itemCmd) {
			threadUid = (String) uids.get(i);
			if(i == null) return;
			display(loadingAlert("Загрузка"));
			run(3);
			return;
		}
		this.commandAction(c, mainForm);
	}

	public void commandAction(Command c, Displayable d) {
		if(c == exitCmd) {
			notifyDestroyed();
			return;
		}
		if(c == okCmd || c == backCmd) {
			if(d == settingsForm) {
				defaultChoiceType = settingsDefaultTypeChoice.getSelectedIndex();
				try {
					RecordStore.deleteRecordStore(SETTINGS_RECORDNAME);
				} catch (Exception e) {
				}
				try {
					JSONObject j = new JSONObject();
					j.put("defaultChoiceType", defaultChoiceType);
					byte[] b = j.toString().getBytes("UTF-8");
					RecordStore r = RecordStore.openRecordStore(SETTINGS_RECORDNAME, true);
					r.addRecord(b, 0, b.length);
					r.closeRecordStore();
				} catch (Exception e) {
				}
			}
			display(mainForm);
			return;
		}
		if(c == showGoneCmd) {
			showGone = true;
			mainForm.removeCommand(showGoneCmd);
			mainForm.addCommand(hideGoneCmd);
			commandAction(submitCmd, d);
			return;
		}
		if(c == hideGoneCmd) {
			showGone = false;
			mainForm.removeCommand(hideGoneCmd);
			mainForm.addCommand(showGoneCmd);
			commandAction(submitCmd, d);
			return;
		}
		if(c == submitCmd) {
			if(running) return;
			if(fromCity == 0 && fromStation == null) {
				text.setText("Не выбрана точка А");
				return;
			}
			if(toCity == 0 && toStation == null) {
				text.setText("Не выбрана точка Б");
				return;
			}
			mainForm.addCommand(cacheScheduleCmd);
			mainForm.deleteAll();
			mainForm.append(dateField);
			mainForm.append(fromBtn);
			mainForm.append(toBtn);
			mainForm.append(submitBtn);
			mainForm.append(text);
//			Display.getDisplay(this).setCurrentItem(text);
			run(2);
			return;
		}
		if(c == aboutCmd) {
			Form f = new Form("О программе");
			f.addCommand(backCmd);
			f.setCommandListener(this);
			f.append(new StringItem("Махолички", "Разработчик: shinovon\nНазвание придумал: sym_ansel\nПредложил: MuseCat77\n\n292 labs"));
			display(f);
			return;
		}
		if(c == settingsCmd) {
			settingsForm = new Form("Настройки");
			settingsForm.addCommand(backCmd);
			settingsForm.setCommandListener(this);
			settingsDefaultTypeChoice = new ChoiceGroup("Выбор пункта по умолчанию", Choice.POPUP, new String[] { "Спрашивать", "Город", "Станция" }, null);
			settingsDefaultTypeChoice.setSelectedIndex(defaultChoiceType, true);
			settingsForm.append(settingsDefaultTypeChoice);
			settingsMultipleChoice = new ChoiceGroup("", Choice.MULTIPLE, SETTINGS, null);
			settingsForm.append(settingsMultipleChoice);
			StringItem clearStationsBtn = new StringItem("", "Очистить станции", StringItem.BUTTON);
			clearStationsBtn.addCommand(clearStationsCmd);
			clearStationsBtn.setDefaultCommand(clearStationsCmd);
			clearStationsBtn.setItemCommandListener(this);
			clearStationsBtn.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_EXPAND);
			settingsForm.append(clearStationsBtn);
			StringItem clearScheduleBtn = new StringItem("", "Очистить расписания", StringItem.BUTTON);
			clearScheduleBtn.addCommand(clearScheduleCmd);
			clearScheduleBtn.setDefaultCommand(clearScheduleCmd);
			clearScheduleBtn.setItemCommandListener(this);
			clearScheduleBtn.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_EXPAND);
			settingsForm.append(clearScheduleBtn);
			display(settingsForm);
			return;
		}
		if(c == clearStationsCmd) {
			if(running) return;
			Display.getDisplay(this).setCurrent(loadingAlert("Очистка станций"), settingsForm);
			clear = STATIONS_RECORDPREFIX;
			run(4);
			return;
		}
		if(c == clearScheduleCmd) {
			if(running) return;
			Display.getDisplay(this).setCurrent(loadingAlert("Очистка расписаний"), settingsForm);
			clear = SCHEDULE_RECORDPREFIX;
			run(4);
			return;
		}
		if(c == bookmarksCmd) {
			if(running) return;
			loadingAlert("Загрузка");
			run(5);
			return;
		}
		if(c == addBookmarkCmd) {
			// загрузить закладки
			if(bookmarks == null) {
				try {
					RecordStore r = RecordStore.openRecordStore(BOOKMARKS_RECORDNAME, false);
					bookmarks = JSON.getArray(new String(r.getRecord(1), "UTF-8"));
					r.closeRecordStore();
				} catch (Exception e) {
					bookmarks = new JSONArray();
				}
			}
			
			// пункты не выбраны
			if((fromCity == 0 && fromStation == null) || (toCity == 0 && toStation == null)) {
				return;
			}
			
			JSONObject bm = new JSONObject();
			bm.put("fn", fromBtn.getText());
			bm.put("fz", fromZone);
			bm.put("fs", fromStation);
			bm.put("tn", toBtn.getText());
			bm.put("tz", toZone);
			bm.put("ts", toStation);
			bookmarks.add(bm);
			
			// запись закладок
			try {
				RecordStore.deleteRecordStore(BOOKMARKS_RECORDNAME);
			} catch (Exception e) {
			}
			try {
				RecordStore r = RecordStore.openRecordStore(BOOKMARKS_RECORDNAME, true);
				byte[] b = bookmarks.toString().getBytes("UTF-8");
				r.addRecord(b, 0, b.length);
				r.closeRecordStore();
			} catch (Exception e) {
			}
			return;
		}
		if(c == cacheScheduleCmd) {
			if(running) return;
			// пункты не выбраны
			if((fromCity == 0 && fromStation == null) || (toCity == 0 && toStation == null) || (scheduleRecordName == null)) {
				return;
			}
			progressAlert = loadingAlert("Загрузка");
			progressAlert.setTimeout(Alert.FOREVER);
			display(progressAlert);
			run(6);
			return;
		}
		if(c == searchCmd) {
			// не выполнять поиск если текущий поток поиска занят
			if(running) return;
			run(7);
			return;
		}
		if(c == cancelCmd) {
			cancelChoice();
			return;
		}
		if(c == doneCmd) {
			int i = searchChoice.getSelectedIndex();
			if(i == -1) { // если список пустой то отмена
				cancelChoice();
				return;
			}
			String s = searchChoice.getString(i);
			if(searchType == 3) { // выбрана станция
				String esr = null;
				Enumeration e = searchStations.elements();
				while(e.hasMoreElements()) {
					JSONObject j = (JSONObject) e.nextElement();
					if((j.getString("t") + " - " + j.getString("d")).equals(s)) {
						esr = j.getString("i");
						break;
					}
				}
				select(searchType, esr, s);
				return;
			}
			// зона или город
			select(searchType, s, null);
			return;
		}
		if(c == removeCmd) { // удалить закладку
			if(bookmarks == null) return;
			int idx;
			((List)d).delete(idx = ((List)d).getSelectedIndex());
			bookmarks.remove(idx);
			return;
		}
		if(c == List.SELECT_COMMAND) { // выбрана закладка
			if(bookmarks == null) return;
			JSONObject bm = bookmarks.getObject(((List)d).getSelectedIndex());
			String s;
			fromZone = bm.getInt("fz", 0);
			fromBtn.setText(s = bm.getString("fn"));
			if((fromStation = bm.getString("fs", null)) == null) {
				fromCity = getCity(fromZone, s);
			}
			toZone = bm.getInt("tz", 0);
			toBtn.setText(s = bm.getString("tn"));
			if((toStation = bm.getString("ts", null)) == null) {
				toCity = getCity(toZone, s);
			}
			display(mainForm);
			commandAction(submitCmd, d);
			return;
		}
		if(c == Alert.DISMISS_COMMAND) { // игнорировать дефолтное ОК
			return;
		}
		// команды диалогов
		if(d instanceof Alert) {
			switch (c.getPriority()) {
			case 1: // выбор зоны перед выбором станции
				display(searchForm(1, choosing == 1 ? fromZone : toZone, null));
				break;
			case 2: // глобальный выбор города
				if(choosing == 1)
					fromZone = 0;
				else
					toZone = 0;
				display(searchForm(2, 0, null));
				break;
			case 3: // выбрать город в зоне
				display(searchForm(2, choosing == 1 ? fromZone : toZone, null));
				break;
			case 4: // загрузить станции
				progressAlert = loadingAlert("Загрузка станций");
				progressAlert.setTimeout(Alert.FOREVER);
				display(progressAlert);
				run(1);
				break;
			}
		}
	}

	public void itemStateChanged(Item item) {
		if(item == searchField) { // выполнять поиск при изменениях в поле ввода
			commandAction(searchCmd, searchField);
		}
	}
	
	private Form searchForm(int type, int zone, JSONArray stations) {
		String zoneName = null;
		int i = 0;
		if(zone != 0) while(zonesAndCities[i][0] != zone && (i+=1) < zoneNames.length);
		searchForm = new Form((type == 1 ? "Выбор зоны" : type == 2 ? "Выбор города" : "Выбор станции") + (zoneName != null ? " (" + zoneName + ")" : ""));
		searchCancel = true;
		searchType = type;
		searchZone = zone;
		searchStations = stations;
		searchField = new TextField("Поиск", "", 100, TextField.ANY);
//		field.addCommand(searchCmd);
//		field.setDefaultCommand(searchCmd);
		searchField.setItemCommandListener(this);
		searchForm.append(searchField);
		searchChoice = new ChoiceGroup("", Choice.EXCLUSIVE);
//		choice.addCommand(doneCmd);
//		choice.setDefaultCommand(doneCmd);
//		choice.setItemCommandListener(this);
		searchForm.append(searchChoice);
		searchForm.addCommand(cancelCmd);
		searchForm.setCommandListener(this);
		searchForm.setItemStateListener(this);
		return searchForm;
	}
	
	/// Бэкенд

	public void run() {
		running = true;
		switch(run) {
		case 1: // скачать станции зоны
			try {
				progressAlert.setString("Скачивание");
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
				RecordStore rs = RecordStore.openRecordStore(STATIONS_RECORDPREFIX + downloadZone, true);
				byte[] b = r.toString().getBytes("UTF-8");
				rs.addRecord(b, 0, b.length);
				rs.closeRecordStore();
				rs = null;
				display(searchForm(3, downloadZone, r));
				break;
			} catch (Exception e) {
				progressAlert.setString(e.toString());
			}
			progressAlert.setCommandListener(midlet);
			progressAlert.addCommand(okCmd);
			break;
		case 2: // выполнить запрос
			try {
				uids.clear();
				/*
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
				*/
				Calendar cal = Calendar.getInstance();
				cal.setTime(dateField.getDate());
				searchDate = cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH);
				
				text.setLabel("Результаты");
				text.setText("Загрузка");
				
				scheduleRecordName = SCHEDULE_RECORDPREFIX + searchDate + (fromStation != null ? ("s" + fromStation) : ("c" + fromCity)) + (toStation != null ? ("s" + toStation) : ("c" + toCity));
				
				searchParams = (fromStation != null ? ("station_from=" + fromStation) : ("city_from=" + fromCity)) + "&" + (toStation != null ? ("station_to=" + toStation) : ("city_to=" + toCity));
				JSONObject j;
				try {
					j = api("search_on_date?date=" + searchDate + "&" + searchParams);
				} catch (IOException e) {
					// попробовать загрузить кэшированное расписание
					try {
						RecordStore r = RecordStore.openRecordStore(scheduleRecordName, false);
						j = JSON.getObject(new String(r.getRecord(1), "UTF-8"));
						r.closeRecordStore();
						JSONArray a = j.getArray("a");
						if(a.size() > 0) {
							Calendar c = parseDate(j.getString("t"));
							text.setLabel(c.get(Calendar.DAY_OF_MONTH) + "." + (c.get(Calendar.MONTH) + 1) + "." + c.get(Calendar.YEAR) + " " + n(c.get(Calendar.HOUR_OF_DAY)) + ":" + n(c.get(Calendar.MINUTE)));
							text.setText("Результаты (кэш)\n");

							for(Enumeration e2 = a.elements(); e2.hasMoreElements();) {
								JSONObject seg = (JSONObject) e2.nextElement();
								StringItem s = new StringItem(seg.getString("t"), seg.getString("r"));
								s.setFont(Font.getFont(0, 0, 8));
								s.addCommand(itemCmd);
								s.setDefaultCommand(itemCmd);
								s.setItemCommandListener(this);
								mainForm.append(s);
								uids.put(s, seg.getString("u"));
							}
						} else {
							text.setLabel("Результаты (кэш)");
							text.setText("Пусто");
						}
					} catch (Exception e2) {
						e2.printStackTrace();
						text.setLabel("Результаты");
						text.setText("Нет сети!\n" + e.toString());
					}
					break;
				}
				// время сервера в UTC
				Calendar server_time = parseDate(j.getObject("date_time").getString("server_time"));

				text.setLabel("");
				text.setText("Результаты\n");
				
				int count = 0;
				// парс маршрутов
				for(Enumeration e2 = j.getArray("days").elements(); e2.hasMoreElements();) { // дни
					JSONObject day = (JSONObject) e2.nextElement();
//					mainForm.append(day.getString("date") + "\n");
					for(Enumeration e3 = day.getArray("segments").elements(); e3.hasMoreElements();) { // сегменты
						JSONObject seg = (JSONObject) e3.nextElement();
						count++;

						JSONObject departure = seg.getObject("departure");
						Calendar c = parseDate(departure.getString("time_utc"));
						// пропускать ушедшие сегодня
						if(!showGone && oneDay(c, server_time) && c.before(server_time)) continue;
						
						JSONObject thread = seg.getObject("thread");
						JSONObject arrival = seg.getObject("arrival");

						String res = "";
						// платформа отправления
						if(departure.has("platform")) {
							res += departure.getString("platform") + "\n";
						}
						
						// время отправления - время прибытия (длина)
						// показывается местное время
						String time = time(departure.getString("time")) + " - " + time(arrival.getString("time")) + " (" + duration(seg.getInt("duration")) + ")\n";
						
						// название
						res += thread.getString("title_short", thread.getString("title")) + "\n";
						
						// тариф
						JSONObject tariff = seg.getNullableObject("tariff");
						if(tariff != null) res += replaceOnce(tariff.getString("value"), ".0", "") + " " + tariff.getString("currency") + "\n";
						
						// опоздание
						if(departure.has("state")) {
							JSONObject state = departure.getObject("state");
							int minutes_from = state.getInt("minutes_from", -1);
							int minutes_to = state.getInt("minutes_to", -1);
							if(minutes_from >= 0 && minutes_to >= 0) {
								if(c.before(server_time)) { // ушел
									if(minutes_from == 0) {
										res += "Ушёл по расписанию";
									} else {
										res += "Ушёл позже на " + minutes_from + " мин.";
									}
								} else if(minutes_from > 0 || minutes_to > 0) {
									res += "Возможно опоздание ";
									if(minutes_from == minutes_to) {
										res += "на " + minutes_from + " мин.";
									} else {
										res += "от " + minutes_from + " до " + minutes_to + " мин.";
									}
								}
								res += "\n";
							} else if(state.has("type") && "possible_delay".equals(state.getString("type"))) {
								res += "Возможно опоздание\n";
							}
						}
						
						// транспорт
						if(thread.has("transport") && thread.getObject("transport").has("subtype")) {
							res += replaceOnce(thread.getObject("transport").getObject("subtype").getString("title"),"<br/>","\n") + "\n";
						}

						StringItem s = new StringItem(time, res);
						s.setFont(Font.getFont(0, 0, 8));
//						s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_BEFORE);
						s.addCommand(itemCmd);
						s.setDefaultCommand(itemCmd);
						s.setItemCommandListener(this);
						mainForm.append(s);
						uids.put(s, thread.getString("uid"));
					}
				}
				if(count == 0) {
					text.setLabel("Результаты");
					text.setText("Пусто");
				}
			} catch (Exception e) {
				e.printStackTrace();
				text.setText(e.toString());
			}
			break;
		case 3: { // доп информация по маршруту
			Form f = new Form(threadUid);
			f.addCommand(backCmd);
			f.setCommandListener(this);
			try {
				JSONObject j = api("thread_on_date/" + threadUid + "?date=" + searchDate);
				f.append(j.getString("title") + "\nОстановки: " + j.getString("stops") + "\n");
				for(Enumeration e = j.getArray("stations").elements(); e.hasMoreElements();) {
					JSONObject station = (JSONObject) e.nextElement();
					StringItem s = new StringItem("", station.getString("title") + " " + time(station.getNullableString("departure_local"))+"\n");
					s.setFont(Font.getFont(0, 0, 8));
					f.append(s);
				}
			} catch (Exception e) {
				e.printStackTrace();
				f.append(e.toString());
			}
			display(f);
			break;
		}
		case 4: // очистка рекордов
			try {
				String[] records = RecordStore.listRecordStores();
				for(int i = 0; i < records.length; i++) {
					String record = records[i];
					if(record.startsWith(clear)) {
						try {
							RecordStore.deleteRecordStore(record);
						} catch (Exception e) {
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			display(mainForm);
			break;
		case 5: { // закладки
			List l = new List("Закладки", List.IMPLICIT);
			l.addCommand(backCmd);
			l.addCommand(List.SELECT_COMMAND);
			l.addCommand(removeCmd);
			l.setCommandListener(this);
			try {
				if(bookmarks == null) {
					RecordStore r = RecordStore.openRecordStore(BOOKMARKS_RECORDNAME, false);
					bookmarks = JSON.getArray(new String(r.getRecord(1), "UTF-8"));
					r.closeRecordStore();
				}
				for(Enumeration e = bookmarks.elements(); e.hasMoreElements();) {
					JSONObject bm = (JSONObject) e.nextElement();
					l.append(bm.getString("fn") + " - " + bm.getString("tn"), null);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			display(l);
			break;
		}
		case 6: // сохранение расписания
			if((fromCity == 0 && fromStation == null) || (toCity == 0 && toStation == null) || (scheduleRecordName == null)) {
				break;
			}
			progressAlert.setString("Удаление");
			try {
				RecordStore.deleteRecordStore(scheduleRecordName);
			} catch (Exception e) {
			}
			try {
				JSONObject r = new JSONObject();
				JSONArray a = new JSONArray();
				progressAlert.setString("Загрузка");
				JSONObject j = api("search_on_date?date=" + searchDate + "&" + searchParams);
				progressAlert.setString("Парс");
				r.put("t", j.getObject("date_time").getString("server_time"));
				for(Enumeration e2 = j.getArray("days").elements(); e2.hasMoreElements();) {
					JSONObject day = (JSONObject) e2.nextElement();
					for(Enumeration e3 = day.getArray("segments").elements(); e3.hasMoreElements();) { 
						JSONObject seg = (JSONObject) e3.nextElement();
						JSONObject sr = new JSONObject();

						JSONObject departure = seg.getObject("departure");
						//sr.put("d", departure.getString("time_utc"));
						
						JSONObject thread = seg.getObject("thread");
						JSONObject arrival = seg.getObject("arrival");
						
						sr.put("u", thread.getString("uid"));
						sr.put("t", time(departure.getString("time")) + " - " + time(arrival.getString("time")) + " (" + seg.getString("duration") + " мин)");
						
						String res = "";
						if(departure.has("platform")) {
							res += departure.getString("platform") + "\n";
						}
						res += thread.getString("title_short", thread.getString("title")) + "\n";
						
						JSONObject tariff = seg.getNullableObject("tariff");
						if(tariff != null) res += replaceOnce(tariff.getString("value"), ".0", "") + " " + tariff.getString("currency") + "\n";
						
						if(departure.has("state")) {
							JSONObject state = departure.getObject("state");
							int minutes_from = state.getInt("minutes_from", -1);
							int minutes_to = state.getInt("minutes_to", -1);
							if(minutes_from > 0 && minutes_to > 0) {
								res += "Возможно опоздание ";
								if(minutes_from == minutes_to) {
									res += "на " + minutes_from + " мин.";
								} else {
									res += "от " + minutes_from + " до " + minutes_to + " мин.";
								}
								res += "\n";
							} else if(state.has("type") && "possible_delay".equals(state.getString("type"))) {
								res += "Возможно опоздание\n";
							}
						}
						
						if(thread.has("transport") && thread.getObject("transport").has("subtype")) {
							res += thread.getObject("transport").getObject("subtype").getString("title") + "\n";
						}
						
						sr.put("r", res);
						a.add(sr);
					}
				}
				r.put("a", a);
				progressAlert.setString("Запись");
				byte[] b = r.toString().getBytes("UTF-8");
				RecordStore rs = RecordStore.openRecordStore(scheduleRecordName, true);
				rs.addRecord(b, 0, b.length);
				rs.closeRecordStore();
				display(mainForm);
			} catch (Exception e) {
				e.printStackTrace();
				display(new Alert("Ошибка", e.toString(), null, AlertType.ERROR));
			}
		case 7:
			try {
				String query = searchField.getString().toLowerCase().trim();
				searchChoice.deleteAll();
				// TODO: переделать поиск
				search: {
				if(searchType == 1) { // поиск зоны
					if(query.length() < 2) break search;
					int i = 0;
					while(i < zoneNames.length) {
						String s = zoneNames[i++];
						if(!s.toLowerCase().startsWith(query)) continue;
						searchChoice.append(s, null);
					}
				} else if(searchType == 2) { // поиск города
					if(searchZone == 0) {
						// глобальный
						if(query.length() < 2) break search;
						int i = 0;
						while(i < cityNames.length) {
							String s = cityNames[i++];
							if(!s.toLowerCase().startsWith(query)) continue;
							searchChoice.append(s, null);
						}
					} else {
						// внутри одной зоны
						int i = 0;
						int[] c;
						while((c = zonesAndCities[i])[0] != searchZone && (i+=1) < zonesAndCities.length);
						i = 0;
						while(i < c.length) {
							String s = cityNames[c[i++]];
							if(!s.toLowerCase().startsWith(query)) continue;
							searchChoice.append(s, null);
						}
					}
				} else if(searchType == 3) { // поиск станции
					if(query.length() < 2) break search; 
					Enumeration e = searchStations.elements();
					while(e.hasMoreElements()) {
						JSONObject s = (JSONObject) e.nextElement();
						// поиск в названии и направлении
						if(!s.getString("d").toLowerCase().startsWith(query)
								&& !s.getString("t").toLowerCase().startsWith(query)) continue;
						searchChoice.append(s.getString("t") + " - " + s.getString("d"), null);
					}
				}
				}
				// замена функции "отмена" на "готово"
				if(searchChoice.getSelectedIndex() != -1) {
					if(!searchCancel) break;
					searchForm.removeCommand(cancelCmd);
					searchForm.addCommand(doneCmd);
					searchCancel = false;
					break;
				}
				if(searchCancel) break;
				searchForm.removeCommand(doneCmd);
				searchForm.addCommand(cancelCmd);
				searchCancel = true;
			} catch (Throwable e) {
				Alert a = new Alert("");
				a.setString(e.toString());
				display(a);
				e.printStackTrace();
			}
			break;
		}
		running = false;
		run = 0;
	}

	private void run(int run) {
		this.run = run;
		new Thread(this).start();
	}
	
	// при type=1, 2: s - название зоны / города, s2 - не используется
	// при type=3: s - esr код станции, s2 - название
	private void select(int type, String s, String s2) {
		searchForm = null;
		searchField = null;
		searchChoice = null;
		searchStations = null;
		if(type == 1) { // выбрана зона
			int id = 0;
			while(!s.equals(zoneNames[id]) && (id+=1) < zoneNames.length);
			id = zonesAndCities[id][0];
			if(choosing == 1) {
				fromZone = id;
			} else {
				toZone = id;
			}
			// проверка на наличие станций зоны в памяти
			try {
				RecordStore rs = RecordStore.openRecordStore(STATIONS_RECORDPREFIX + id, false);
				JSONArray r = JSON.getArray(new String(rs.getRecord(1), "UTF-8"));
				display(searchForm(3, id, r));
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
		if(type == 2) { // выбран город
			int id = getCity(searchZone, s);
			if(choosing == 1) {
				fromCity = id;
				fromBtn.setText(s);
			} else {
				toCity = id;
				toBtn.setText(s);
			}
			display(mainForm);
			return;
		}
		if(type == 3) { // выбрана станция
			if(choosing == 1) {
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

	private void cancelChoice() {
		// выбор отменен
		/*
		if(choosing == 1) {
			fromSettlement = 0;
			fromStation = null;
			fromBtn.setText("Не выбрано");
		} else {
			toSettlement = 0;
			toStation = null;
			toBtn.setText("Не выбрано");
		}
		*/
		display(mainForm);
		searchForm = null;
		searchField = null;
		searchChoice = null;
		searchStations = null;
	}
	
	private int getCity(int zone, String name) {
		int r = 0;
		if(zone == 0) {
			while(!name.equals(cityNames[r]) && (r+=1) < cityNames.length);
		} else {
			int i = 0;
			int[] z;
			while((z = zonesAndCities[i])[0] != zone && (i+=1) < zonesAndCities.length);
			i = 0;
			while(!name.equals((cityNames[r = z[i]])) && (i+=1) < z.length);
		}
		return r == cityNames.length ? 0 : cityIds[r];
	}
	
	/// Утилки

	private void display(Displayable d) {
		if(d instanceof Alert) {
			Display.getDisplay(this).setCurrent((Alert) d, mainForm);
			return;
		}
		Display.getDisplay(this).setCurrent(d);
	}

	private Alert loadingAlert(String text) {
		Alert a = new Alert("");
		a.setString(text);
		a.setIndicator(new Gauge(null, false, Gauge.INDEFINITE, Gauge.CONTINUOUS_RUNNING));
		a.setTimeout(5000);
		return a;
	}
	
	private static byte[] readBytes(InputStream is) throws IOException {
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
	
	// парсер даты ISO 8601 без учета часового пояса
	private static Calendar parseDate(String date) {
		Calendar c = Calendar.getInstance();
		if(date.indexOf('T') != -1) {
			String[] dateSplit = split(date.substring(0, date.indexOf('T')), '-');
			String[] timeSplit = split(date.substring(date.indexOf('T')+1), ':');
			String second = split(timeSplit[2], '.')[0];
			int i = second.indexOf('+');
			if(i == -1) {
				i = second.indexOf('-');
			}
			if(i != -1) {
				second = second.substring(0, i);
			}
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

	private static String time(String date) {
		if(date == null) return "";
		Calendar c = parseDate(date);
		//return c.get(Calendar.DAY_OF_MONTH) + " " + localizeMonthWithCase(c.get(Calendar.MONTH)) + " " +
		return n(c.get(Calendar.HOUR_OF_DAY)) + ":" + n(c.get(Calendar.MINUTE));
	}
	
//	private static long timestamp(String date) {
//		return parseDate(date).getTime().getTime();
//	}
	
	private static boolean oneDay(Calendar a, Calendar b) {
		return a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH) &&
				a.get(Calendar.MONTH) == b.get(Calendar.MONTH) &&
				a.get(Calendar.YEAR) == b.get(Calendar.YEAR);
	}
	
//	private static String localizeMonthWithCase(int month) {
//		switch(month) {
//		case Calendar.JANUARY:
//			return "января";
//		case Calendar.FEBRUARY:
//			return "февраля";
//		case Calendar.MARCH:
//			return "марта";
//		case Calendar.APRIL:
//			return "апреля";
//		case Calendar.MAY:
//			return "мая";
//		case Calendar.JUNE:
//			return "июня";
//		case Calendar.JULY:
//			return "июля";
//		case Calendar.AUGUST:
//			return "августа";
//		case Calendar.SEPTEMBER:
//			return "сентября";
//		case Calendar.OCTOBER:
//			return "октября";
//		case Calendar.NOVEMBER:
//			return "ноября";
//		case Calendar.DECEMBER:
//			return "декабря";
//		default:
//			return "";
//		}
//	}
	
	private static String duration(int minutes) {
		if(minutes > 24 * 60) {
			int hours = minutes / 60;
			return (hours / 24) + "д " + (hours % 24) + " ч";
		}
		if(minutes > 60) {
			return (minutes / 60) + " ч " + (minutes % 60) + " мин";
		}
		return minutes + " мин";
	}
	
	private static String[] split(String str, char d) {
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
	
//	private static String url(String url) {
//		StringBuffer sb = new StringBuffer();
//		char[] chars = url.toCharArray();
//		for (int i = 0; i < chars.length; i++) {
//			int c = chars[i];
//			if (65 <= c && c <= 90) {
//				sb.append((char) c);
//			} else if (97 <= c && c <= 122) {
//				sb.append((char) c);
//			} else if (48 <= c && c <= 57) {
//				sb.append((char) c);
//			} else if (c == 32) {
//				sb.append("%20");
//			} else if (c == 45 || c == 95 || c == 46 || c == 33 || c == 126 || c == 42 || c == 39 || c == 40
//					|| c == 41) {
//				sb.append((char) c);
//			} else if (c <= 127) {
//				sb.append(hex(c));
//			} else if (c <= 2047) {
//				sb.append(hex(0xC0 | c >> 6));
//				sb.append(hex(0x80 | c & 0x3F));
//			} else {
//				sb.append(hex(0xE0 | c >> 12));
//				sb.append(hex(0x80 | c >> 6 & 0x3F));
//				sb.append(hex(0x80 | c & 0x3F));
//			}
//		}
//		return sb.toString();
//	}
//
//	private static String hex(int i) {
//		String s = Integer.toHexString(i);
//		return "%".concat(s.length() < 2 ? "0" : "").concat(s);
//	}
	
	private static byte[] get(String url) throws IOException {
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
	
	private static String getUtf(String url) throws IOException {
		return new String(get(url), "UTF-8");
	}
	
	private static JSONObject api(String url) throws Exception {
		String r = getUtf("http://export.rasp.yandex.net/v3/suburban/" + url);
		JSONObject j = JSON.getObject(r);
		if(j.has("error")) {
			// выбрасывать эксепшн с текстом ошибки
			throw new Exception(j.getObject("error").getString("text"));
		}
		return j;
	}
	
	private static String replaceOnce(String str, String hay, String ned) {
		int idx = str.indexOf(hay);
		if(idx != -1) {
			str = str.substring(0, idx) + ned + str.substring(idx+hay.length());
		}
		return str;
	}
	
	private static boolean isJ2MEL() {
		try {
			Class.forName("javax.microedition.shell.MicroActivity");
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}
