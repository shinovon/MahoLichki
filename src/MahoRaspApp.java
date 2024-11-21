/*
Copyright (c) 2023-2024 Arman Jussupgaliyev
*/
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
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
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Spacer;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreNotFoundException;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import cc.nnproject.json.JSONStream;

public class MahoRaspApp extends MIDlet implements CommandListener, ItemCommandListener, Runnable, ItemStateListener {
	
	// Функции главной формы
	private static final Command exitCmd = new Command("Выход", Command.EXIT, 1);
	private static final Command bookmarksCmd = new Command("Закладки", Command.SCREEN, 4);
	private static final Command reverseCmd = new Command("Перевернуть", Command.ITEM, 7);
	private static final Command settingsCmd = new Command("Настройки", Command.SCREEN, 8);
	private static final Command aboutCmd = new Command("О программе", Command.SCREEN, 9);
	
	// Команды формы результатов
	private static final Command showGoneCmd = new Command("Показать ушедшие", Command.SCREEN, 3);
	private static final Command hideGoneCmd = new Command("Скрыть ушедшие", Command.SCREEN, 3);
	private static final Command addBookmarkCmd = new Command("Добавить в закладки", Command.SCREEN, 5);
	private static final Command cacheScheduleCmd = new Command("Сохранить", Command.SCREEN, 6);
	private static final Command teasersCmd = new Command("Уведомления", Command.ITEM, 2);

	// Функции элементов
	private static final Command submitCmd = new Command("Искать", Command.ITEM, 2);
	private static final Command chooseCmd = new Command("Выбрать", Command.ITEM, 2);
	private static final Command itemCmd = new Command("Остановки", Command.ITEM, 2);
	private static final Command clearStationsCmd = new Command("Очистить станции", Command.ITEM, 2);
	private static final Command clearScheduleCmd = new Command("Очистить расписания", Command.ITEM, 2);
	private static final Command importStationsCmd = new Command("Импорт.", Command.ITEM, 2);
	private static final Command exportStationsCmd = new Command("Экспорт.", Command.ITEM, 2);
	private static final Command removeBookmarkCmd = new Command("Удалить", Command.ITEM, 2);
	
	// Команды диалогов
	private static final Command okCmd = new Command("Ок", Command.OK, 1);
	private static final Command backCmd = new Command("Назад", Command.BACK, 1);
	
	// Команды формы поиска
	private static final Command doneCmd = new Command("Готово", Command.OK, 1);
	private static final Command doneCmdI = new Command("Готово", Command.ITEM, 1);
	private static final Command cancelCmd = new Command("Отмена", Command.CANCEL, 1);
	private static final Command showStationsCmd = new Command("Показать станции", Command.ITEM, 2);
	
	// команды файл менеджера
	private final static Command dirOpenCmd = new Command("Открыть", Command.ITEM, 1);
	private final static Command dirSelectCmd = new Command("Выбрать", Command.SCREEN, 2);
	
	// Константы названий RecordStore
	private static final String SETTINGS_RECORDNAME = "mahoLsets";
	private static final String STATIONS_RECORDPREFIX = "mahoLS_";
	private static final String SCHEDULE_RECORDPREFIX = "mahoLD";
	private static final String BOOKMARKS_RECORDNAME = "mahoLbm";
	
	private static final String STATIONS_FILEPREFIX = "mLzone_";

	private static final int RUN_DOWNLOAD_STATIONS = 1;
	private static final int RUN_SUBMIT = 2;
	private static final int RUN_THREAD_INFO = 3;
	private static final int RUN_CLEAR_RECORDS = 4;
	private static final int RUN_BOOKMARKS = 5;
	private static final int RUN_SAVE = 6;
	private static final int RUN_SEARCH = 7;
	private static final int RUN_SEARCH_TIMER = 8;
	
	private static MahoRaspApp midlet;
	private static Display display;
	private static Font smallfont = Font.getFont(0, 0, 8);
	private static Font smallboldfont = Font.getFont(0, Font.STYLE_BOLD, 8);

	// бд зон и городов
	private static int[][] zonesAndCities;
	private static String[] zoneNames;
	private static int[] cityIds;
	private static String[] cityNames;
	
	// Настройки
	private static int defaultChoiceType = 1; // 1 - город, 2 - станция, 0 - спрашивать
	
	private static Form mainForm;
	private static Form loadingForm;
	private static Form settingsForm;
	private static Form resForm;
	
	// UI главной
	private static DateField dateField;
	private static StringItem text;
	private static StringItem fromBtn;
	private static StringItem toBtn;

	// UI настроек
	private static ChoiceGroup settingsDefaultTypeChoice;
	
	// точка А
	private static int fromZone;
	private static int fromCity; // title
	private static String fromStation; // esr
	
	// точка Б
	private static int toZone;
	private static int toCity; // title
	private static String toStation; // esr
	
	private static int choosing; // 1 - отправление, 2 - прибытие
	private static Alert progressAlert;
	private static int downloadZone;
	private static int run;
	private static boolean running;
	private static String threadUid;
	private static String searchDate;
	private static boolean showGone;
	private static Hashtable uids = new Hashtable();
	private static String clear;
	private static JSONArray bookmarks;
	private static JSONArray teasers;
	
	
	// форма поиска
	private static Form searchForm;
	private static int searchType; // 1 - зона, 2 - город, 3 - станция
	private static int searchZone;
	private static TextField searchField;
	private static ChoiceGroup searchChoice;
	private static JSONArray searchStations;
	private static boolean searchDoneCmdAdded;
	private static Object searchLock = new Object();
	private static boolean searching;
	private static int searchTimer;
	private static Thread searchThread;
	private static boolean searchCancel;
	
	// файлы
	private static List fileList;
	private static String curDir;
	private static Vector rootsList;
	private static int fileMode;
	private static int saveZone;

	/// UI

	protected void destroyApp(boolean arg0) {
	}

	protected void pauseApp() {
	}

	protected void startApp() {
		if (midlet != null) return;
		midlet = this;
		display = Display.getDisplay(this);
		display(loadingForm = new Form("Загрузка"));
		// парс зон
		try {
			JSONObject j = JSONObject.parseObject(new String(readBytes("".getClass().getResourceAsStream("/zones.json"), 40677, 1024, 2048), "UTF-8"));
			int i = 0;
			int l = j.size();
			zonesAndCities = new int[l][];
			zoneNames = new String[l];
			Vector cityIdsTmp = new Vector();
			Vector cityNamesTmp = new Vector();
			int m = 0;
			Enumeration e = j.keys();
			while (e.hasMoreElements()) {
				String k;
				zoneNames[i] = (k = (String) e.nextElement());
				JSONObject c = j.getObject(k);
				int n = c.getInt("i");
				(zonesAndCities[i] = new int[(c = c.getObject("s")).size() + 1])[0] = n;
				Enumeration e2 = c.keys();
				n = 1;
				while (e2.hasMoreElements()) {
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
			for (i = 0; i < l; i++) {
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
			JSONObject j = JSONObject.parseObject(new String(r.getRecord(1), "UTF-8"));
			r.closeRecordStore();
			defaultChoiceType = j.getInt("defaultChoiceType", defaultChoiceType);
		} catch (Exception e) {
		}
		// главная форма
		Form f = new Form("Махолички");
		f.setCommandListener(this);
		f.addCommand(exitCmd);
		f.addCommand(bookmarksCmd);
		f.addCommand(settingsCmd);
		f.addCommand(aboutCmd);
//		f.addCommand(reverseCmd);
		dateField = new DateField("Дата", DateField.DATE);
		dateField.setDate(new Date(System.currentTimeMillis()));
		f.append(dateField);
		
		StringItem s;
		
		s = new StringItem("Откуда", "Не выбрано", StringItem.BUTTON);
		s.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		s.addCommand(chooseCmd);
		s.setDefaultCommand(chooseCmd);
		s.setItemCommandListener(this);
		f.append(fromBtn = s);
		
		// reverse btn
		s = new StringItem("", "<->", StringItem.BUTTON);
		s.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		s.addCommand(reverseCmd);
		s.setDefaultCommand(reverseCmd);
		s.setItemCommandListener(this);
		f.append(s);
		
		s = new StringItem("Куда", "Не выбрано", StringItem.BUTTON);
		s.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		s.addCommand(chooseCmd);
		s.setDefaultCommand(chooseCmd);
		s.setItemCommandListener(this);
		f.append(toBtn = s);
		
		s = new StringItem(null, "Поиск", StringItem.BUTTON);
		s.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		s.addCommand(submitCmd);
		s.setDefaultCommand(submitCmd);
		s.setItemCommandListener(this);
		f.append(s);
		
		display(mainForm = f);
		loadingForm = null;
		if (isJ2MEL() && "Nokia 6233".equals(System.getProperty("microedition.platform"))) {
			// https://github.com/nikita36078/J2ME-Loader/issues/1024
			Alert a = new Alert("Внимание");
			a.setString("J2ME Loader поддерживается только с патчем #1026\nЕсли 1.8.0 уже вышла и у вас стоит она или вы пользуетесь JL-Mod, то игнорируйте это сообщение.");
			a.setTimeout(5000);
			display(a);
		}
	}
	
	public void commandAction(Command c, Item item) {
		if (running) return; // не реагировать если сейчас что-то грузится
		// нажатие на куда / откуда
		if (c == chooseCmd) {
			choosing = item == fromBtn ? 1 : 2;
			if (defaultChoiceType == 1) {
				if (choosing == 1)
					fromZone = 0;
				else
					toZone = 0;
				display(searchForm(2, 0, null));
				return;
			}
			if (defaultChoiceType == 2) {
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
		if (c == itemCmd) {
			threadUid = (String) uids.get(item);
			if (item == null) return;
			display(loadingAlert("Загрузка"), null);
			start(RUN_THREAD_INFO);
			return;
		}
		// настройки
		if (c == importStationsCmd) {
			showFileList(1);
			return;
		}
		if (c == exportStationsCmd) {
			choosing = 3;
			display(searchForm(1, 0, null));
			return;
		}
		if (c == clearStationsCmd) {
			if (running) return;
			display(loadingAlert("Очистка станций"), settingsForm);
			clear = STATIONS_RECORDPREFIX;
			start(RUN_CLEAR_RECORDS);
			return;
		}
		if (c == clearScheduleCmd) {
			if (running) return;
			display(loadingAlert("Очистка расписаний"), settingsForm);
			clear = SCHEDULE_RECORDPREFIX;
			start(RUN_CLEAR_RECORDS);
			return;
		}
		if (c == teasersCmd) {
			if (teasers == null) return;
			Form f = new Form("Уведомления");
			f.addCommand(backCmd);
			f.setCommandListener(this);
			
			StringItem s;
			int l = teasers.size();
			for (int i = 0; i < l; i++) {
				JSONObject j = teasers.getObject(i);
				
				s = new StringItem(null, j.getString("title", "").concat("\n"));
				f.append(s);
				
				String t = j.getString("content", "");
				StringBuffer sb = new StringBuffer();
				
				int d = t.indexOf('<');
				int o = 0;
				boolean bold = false;
				
				while (d != -1) {
					if (t.charAt(d + 1) == '/') {
						if (o != d) {
							sb.append(t.substring(o, d));
							
							f.append(new Spacer(16, 16));
							
							s = new StringItem(null, sb.toString());
							s.setFont(bold ? smallboldfont : smallfont);
							f.append(s);
							if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ')
								f.append(new Spacer(smallfont.charWidth(' ') + 1, smallfont.getHeight()));
							
							sb.setLength(0);
						}
						if (t.charAt(d + 2) == 'b') bold = false;
					} else {
						sb.append(t.substring(o, d));
						
						s = new StringItem(null, sb.toString());
						s.setFont(bold ? smallboldfont : smallfont);
						f.append(s);
						if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ')
							f.append(new Spacer(smallfont.charWidth(' ') + 1, smallfont.getHeight()));
						
						sb.setLength(0);
						
						if (t.charAt(d + 1) == 'b') bold = true;
						if (t.charAt(d + 1) == 'l') sb.append("* ");
					}
					d = t.indexOf('<', o = t.indexOf('>', d) + 1);
				}
				
				if (o < t.length()) {
					s = new StringItem(null, t.substring(o + 1));
					s.setFont(smallfont);
					f.append(s);
				}
			}
			
			display(f);
			return;
		}
		this.commandAction(c, mainForm);
	}

	public void commandAction(Command c, Displayable d) {
		if (c == exitCmd) {
			notifyDestroyed();
			return;
		}
		if (d == fileList) {
			if (c == backCmd) {
				if (curDir == null) {
					fileList = null;
					display(settingsForm);
				} else {
					if (curDir.indexOf("/") == -1) {
						fileList = new List("", List.IMPLICIT);
						fileList.addCommand(backCmd);
						fileList.setTitle("");
						fileList.addCommand(List.SELECT_COMMAND);
						fileList.setSelectCommand(List.SELECT_COMMAND);
						fileList.setCommandListener(this);
						for (int i = 0; i < rootsList.size(); i++) {
							String s = (String) rootsList.elementAt(i);
							if (s.startsWith("file:///")) s = s.substring("file:///".length());
							if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
							fileList.append(s, null);
						}
						curDir = null;
						display(fileList);
						return;
					}
					String sub = curDir.substring(0, curDir.lastIndexOf('/'));
					String fn = "";
					if (sub.indexOf('/') != -1) {
						fn = sub.substring(sub.lastIndexOf('/') + 1);
					} else {
						fn = sub;
					}
					curDir = sub;
					showFileList(sub + "/", fn);
				}
			}
			if (c == dirOpenCmd || c == List.SELECT_COMMAND) {
				String fs = curDir;
				String f = "";
				if (fs != null) f += curDir + "/";
				String is = fileList.getString(fileList.getSelectedIndex());
				if ("- Выбрать".equals(is)) {
					fileList = null;
					// экспорт
					if (fileMode == 2) {
						FileConnection fc = null;
						OutputStream o = null;
						try {
							fc = (FileConnection) Connector.open("file:///" + f + STATIONS_FILEPREFIX + saveZone + ".json", 3);
							if (fc.exists())
								fc.delete();
							fc.create();
							o = fc.openDataOutputStream();
							RecordStore rs = RecordStore.openRecordStore(STATIONS_RECORDPREFIX + saveZone, true);
							o.write(rs.getRecord(1));
							rs.closeRecordStore();
							o.flush();
							display(infoAlert("Станции экспортированы"), settingsForm);
						} catch (Exception e) {
							display(warningAlert("Не удалось экспортировать станции: " + e.toString()), settingsForm);
						} finally {
							try {
								if (o != null)
									o.close();
								if (fc != null)
									fc.close();
							} catch (Exception e) {
							}
						}
					}
					curDir = null;
					return;
				}
				f += is;
				// импорт
				if (fileMode == 1 && is.endsWith(".json") && is.startsWith(STATIONS_FILEPREFIX)) {
					display(loadingAlert("Импортирование"), settingsForm);
					FileConnection fc = null;
					InputStream in = null;
					try {
						int zone = Integer.parseInt(is.substring(STATIONS_FILEPREFIX.length(), is.length()-5));
						String zoneName = null;
						if (zone > 0) {
							int i = 0;
							while (zonesAndCities[i][0] != zone && ++i < zoneNames.length);
							if (i < zoneNames.length) {
								zoneName = zoneNames[i];
								fc = (FileConnection) Connector.open("file:///" + f);
								in = fc.openInputStream();
								byte[] b = readBytes(in, (int) fc.fileSize(), 1024, 2048);
								try {
									RecordStore.deleteRecordStore(STATIONS_RECORDPREFIX + zone);
								} catch (Exception e) {}
								RecordStore rs = RecordStore.openRecordStore(STATIONS_RECORDPREFIX + zone, true);
								rs.addRecord(b, 0, b.length);
								rs.closeRecordStore();
							}
						}
						if (zoneName != null)
							display(infoAlert("Станции зоны \"" + zoneName + "\" импортированы"), settingsForm);
						else
							display(warningAlert("Не поддерживаемый ID зоны"), settingsForm);
					} catch (Exception e) {
						display(warningAlert("Не удалось импортировать станции: " + e.toString()), settingsForm);
					} finally {
						try {
							if (in != null)
								in.close();
							if (fc != null)
								fc.close();
						} catch (Exception e) {
						}
					}
					curDir = null;
			        return;
				}
				curDir = f;
				showFileList(f + "/", is);
				return;
			}
			if (c == dirSelectCmd) {
				fileList = null;
				curDir = null;
				display(settingsForm);
			}
			return;
		}
		if (c == okCmd || c == backCmd) {
			if (d == resForm) {
				// back from results
				display(mainForm);
				resForm = null;
				return;
			}
			if (d == settingsForm) {
				// save settings
				defaultChoiceType = settingsDefaultTypeChoice.getSelectedIndex();
				try {
					RecordStore.deleteRecordStore(SETTINGS_RECORDNAME);
				} catch (Exception e) {}
				try {
					JSONObject j = new JSONObject();
					j.put("defaultChoiceType", defaultChoiceType);
					byte[] b = j.toString().getBytes("UTF-8");
					RecordStore r = RecordStore.openRecordStore(SETTINGS_RECORDNAME, true);
					r.addRecord(b, 0, b.length);
					r.closeRecordStore();
				} catch (Exception e) {}
			}
			if (resForm != null) {
				display(resForm);
				return;
			}
			display(mainForm);
			return;
		}
		if (c == showGoneCmd) {
			showGone = true;
			resForm.removeCommand(showGoneCmd);
			resForm.addCommand(hideGoneCmd);
			commandAction(submitCmd, d);
			return;
		}
		if (c == hideGoneCmd) {
			showGone = false;
			resForm.removeCommand(hideGoneCmd);
			resForm.addCommand(showGoneCmd);
			commandAction(submitCmd, d);
			return;
		}
		if (c == submitCmd) {
			if (running) return;
			if ((fromCity == 0 && fromStation == null) || (toCity == 0 && toStation == null)) {
				display(warningAlert("Не выбран один из пунктов"));
				return;
			}
			Form f = new Form("Результаты");
			f.addCommand(backCmd);
			f.addCommand(showGone ? hideGoneCmd : showGoneCmd);
			f.addCommand(addBookmarkCmd);
			f.addCommand(cacheScheduleCmd);
			f.setCommandListener(this);
			
			text = new StringItem(null, "");
			text.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_BEFORE);
			f.append(text);
			
//			Display.getDisplay(this).setCurrentItem(text);
			resForm = f;
			display(loadingAlert("Загрузка..."));
			
			start(RUN_SUBMIT);
			return;
		}
		if (c == aboutCmd) {
			Form f = new Form("О программе");
			f.addCommand(backCmd);
			f.setCommandListener(this);

			StringItem s;
			try {
				f.append(new ImageItem(null, Image.createImage("/icon.png"), Item.LAYOUT_LEFT, null));
			} catch (IOException ignored) {}
			s = new StringItem(null, "МахоЛички v" + getAppProperty("MIDlet-Version"));
			s.setFont(Font.getFont(0, 0, Font.SIZE_LARGE));
			s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_VCENTER);
			f.append(s);
			s = new StringItem(null, "Разработчик: shinovon\nНазвание, иконка: sym_ansel\nПредложил: MuseCat77\n\n292 labs");
			s.setFont(Font.getDefaultFont());
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_LEFT);
			f.append(s);
			display(f);
			return;
		}
		if (c == settingsCmd) {
			Form f = new Form("Настройки");
			f.addCommand(backCmd);
			f.setCommandListener(this);
			settingsDefaultTypeChoice = new ChoiceGroup("Выбор пункта по умолчанию", Choice.POPUP, new String[] { "Спрашивать", "Город", "Станция" }, null);
			settingsDefaultTypeChoice.setSelectedIndex(defaultChoiceType, true);
			f.append(settingsDefaultTypeChoice);
			StringItem s;
			s = new StringItem("", "Очистить станции", StringItem.BUTTON);
			s.addCommand(clearStationsCmd);
			s.setDefaultCommand(clearStationsCmd);
			s.setItemCommandListener(this);
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_EXPAND);
			f.append(s);
			
			s = new StringItem("", "Очистить расписания", StringItem.BUTTON);
			s.addCommand(clearScheduleCmd);
			s.setDefaultCommand(clearScheduleCmd);
			s.setItemCommandListener(this);
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_EXPAND);
			f.append(s);
			
			s = new StringItem("", "Импорт. кэш станций", StringItem.BUTTON);
			s.addCommand(importStationsCmd);
			s.setDefaultCommand(importStationsCmd);
			s.setItemCommandListener(this);
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_EXPAND);
			f.append(s);
			
			s = new StringItem("", "Экспорт. кэш станций", StringItem.BUTTON);
			s.addCommand(exportStationsCmd);
			s.setDefaultCommand(exportStationsCmd);
			s.setItemCommandListener(this);
			s.setLayout(Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_EXPAND);
			f.append(s);
			
			display(settingsForm = f);
			return;
		}
		if (c == bookmarksCmd) {
			if (running) return;
			display(loadingAlert("Загрузка"));
			start(RUN_BOOKMARKS);
			return;
		}
		if (c == addBookmarkCmd) {
			// пункты не выбраны
			if ((fromCity == 0 && fromStation == null) || (toCity == 0 && toStation == null)) {
				display(warningAlert("Не выбран один из пунктов"));
				return;
			}
			
			String fn = fromBtn.getText();
			String tn = toBtn.getText();
			
			// загрузить закладки
			if (bookmarks == null) {
				try {
					RecordStore r = RecordStore.openRecordStore(BOOKMARKS_RECORDNAME, false);
					bookmarks = JSONObject.parseArray(new String(r.getRecord(1), "UTF-8"));
					r.closeRecordStore();
				} catch (Exception e) {
					bookmarks = new JSONArray();
				}
			} else {
				// есть ли уже такая закладка
				int l = bookmarks.size();
				for (int i = 0; i < l; i++) {
					JSONObject j = bookmarks.getObject(i);
					if (fn.equals(j.getString("fn")) && tn.equals(j.getString("tn")))
					{
						display(infoAlert("Закладка уже существует"));
						return;
					}
				}
			}
			
			JSONObject bm = new JSONObject();
			bm.put("fn", fn);
			bm.put("fz", fromZone);
			if (fromStation != null) {
				bm.put("fs", fromStation);
			} else {
				bm.put("fc", fromCity);
			}
			bm.put("tn", tn);
			bm.put("tz", toZone);
			if (toStation != null) {
				bm.put("ts", toStation);
			} else {
				bm.put("tc", toCity);
			}
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
			display(infoAlert("Закладка добавлена"), null);
			return;
		}
		if (c == cacheScheduleCmd) { // сохранить расписание в кэш
			if (running) return;
			// пункты не выбраны
			if ((fromCity == 0 && fromStation == null) || (toCity == 0 && toStation == null)) {
				display(warningAlert("Не выбран один из пунктов"));
				return;
			}
			progressAlert = loadingAlert("Загрузка");
			progressAlert.setTimeout(Alert.FOREVER);
			display(progressAlert);
			start(RUN_SAVE);
			return;
		}
		if (c == cancelCmd) {
			cancelChoice();
			return;
		}
		if (c == doneCmd || c == doneCmdI) {
			int i = searchChoice.getSelectedIndex();
			if (i == -1) { // если список пустой то отмена
				cancelChoice();
				return;
			}
			String s = searchChoice.getString(i);
			if (searchType == 3) { // выбрана станция
				String esr = null;
				Enumeration e = searchStations.elements();
				while (e.hasMoreElements()) {
					JSONObject j = (JSONObject) e.nextElement();
					if ((j.getString("t") + " - " + j.getString("d")).equals(s)) {
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
		if (c == showStationsCmd) { // показать станции зоны выбранного города
			int idx = searchChoice.getSelectedIndex();
			if (idx == -1 || searchType != 2) // игнорировать если выбор пустой
				return;
			
			String name = searchChoice.getString(idx);

			int i = 0, j, l = zoneNames.length;
			int[] z;
			
			// поиск зоны по городу в ней
			while (i < l) {
				z = zonesAndCities[i];
				
				j = 1;
				while (!name.equals(cityNames[z[j]]) && ++j < z.length);
				
				if (j < z.length) break; // город обнаружен, брейкаемся
				i++;
			}
			
			if (i == l) return; // поиск не удался
			
			int zone = zonesAndCities[i][0];
			
			// копипаста показа станций зоны
			if (choosing == 1) {
				fromZone = zone;
			} else {
				toZone = zone;
			}
			// проверка на наличие станций зоны в памяти
			try {
				RecordStore rs = RecordStore.openRecordStore(STATIONS_RECORDPREFIX + zone, false);
				JSONArray r = JSONObject.parseArray(new String(rs.getRecord(1), "UTF-8"));
				if (choosing == 3) {
					showFileList(2);
				} else {
					display(searchForm(3, zone, r));
				}
				rs.closeRecordStore();
			} catch (Exception e) {
				downloadZone = zone;
				Alert a = new Alert("");
				a.setString("Станции зоны \"" + zoneNames[i] + "\" не найдены в кэше. Загрузить?");
				a.addCommand(new Command("Загрузить", Command.OK, 4));
				a.addCommand(new Command("Отмена", Command.CANCEL, 5));
				a.setCommandListener(midlet);
				display(a);
			}
			return;
		}
		if (c == reverseCmd) { // поменять точки местами
			String s = fromBtn.getText();
			fromBtn.setText(toBtn.getText());
			toBtn.setText(s);
			
			s = fromStation;
			fromStation = toStation;
			toStation = s;
			
			int i = fromZone;
			fromZone = toZone;
			toZone = i;
			
			i = fromCity;
			fromCity = toCity;
			toCity = i;
			return;
		}
		if (c == removeBookmarkCmd) { // удалить закладку
			if (bookmarks == null) return;
			int idx;
			((List)d).delete(idx = ((List)d).getSelectedIndex());
			bookmarks.remove(idx);
			display(infoAlert("Закладка удалена"), null);
			return;
		}
		if (c == List.SELECT_COMMAND) { // выбрана закладка
			if (bookmarks == null) return;
			JSONObject bm = bookmarks.getObject(((List)d).getSelectedIndex());
			String s;
			fromZone = bm.getInt("fz", 0);
			fromBtn.setText(s = bm.getString("fn"));
			if ((fromStation = bm.getString("fs", null)) == null) {
				if ((fromCity = bm.getInt("fc", 0)) == 0) {
					fromCity = getCity(fromZone, s);
				}
			}
			toZone = bm.getInt("tz", 0);
			toBtn.setText(s = bm.getString("tn"));
			if ((toStation = bm.getString("ts", null)) == null) {
				if ((toCity = bm.getInt("tc", 0)) == 0) {
					toCity = getCity(toZone, s);
				}
			}
			display(mainForm);
			commandAction(submitCmd, d);
			return;
		}
		if (c == Alert.DISMISS_COMMAND) { // игнорировать дефолтное ОК
			return;
		}
		// команды диалогов
		if (d instanceof Alert) {
			switch (c.getPriority()) {
			case 1: // выбор зоны перед выбором станции
				display(searchForm(1, choosing == 1 ? fromZone : toZone, null));
				break;
			case 2: // глобальный выбор города
				if (choosing == 1)
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
				start(RUN_DOWNLOAD_STATIONS);
				break;
			case 5:
				display(searchForm);
				break;
			}
		}
	}

	public void itemStateChanged(Item item) {
		if (item == searchField) { // выполнять поиск при изменениях в поле ввода
			if (running) return;
			if (searchThread == null) {
				try {
					synchronized (this) {
						run = RUN_SEARCH_TIMER;
						(searchThread = new Thread(this)).start();
						wait();
						run = 0;
					}
				} catch (Exception e) {}
			}
			searchTimer = 5;
			return;
		}
		if (item == searchChoice) {
			searchDoneCmd();
		}
	}
	
	private static void searchDoneCmd() {
		if (searchChoice.getSelectedIndex() != -1) {
			if (searchDoneCmdAdded) return;
			
			String s = System.getProperty("microedition.platform");
			boolean is92or93 = (s != null && s.indexOf("sw_platform_version=3.2") != -1)
					|| (System.getProperty("com.symbian.midp.serversocket.support") != null
					|| System.getProperty("com.symbian.default.to.suite.icon") != null);
			if (is92or93) {
				searchField.addCommand(doneCmd);
				searchField.setDefaultCommand(doneCmd); // for CSK
				searchChoice.addCommand(doneCmdI); // for cmd menu
			} else {
				searchForm.addCommand(doneCmd);
			}
			if (searchType == 2) searchForm.addCommand(showStationsCmd);
			searchDoneCmdAdded = true;
			return;
		}
		if (!searchDoneCmdAdded) return;
		
		searchField.removeCommand(doneCmd);
		searchChoice.removeCommand(doneCmdI);
		searchForm.removeCommand(doneCmd);
		if (searchType == 2) searchForm.removeCommand(showStationsCmd);
		searchDoneCmdAdded = false;
	}
	
	private static void showFileList(String f, String title) {
		fileList = new List(title, List.IMPLICIT);
		fileList.setTitle(title);
		fileList.addCommand(backCmd);
		fileList.addCommand(List.SELECT_COMMAND);
		fileList.setSelectCommand(List.SELECT_COMMAND);
		fileList.setCommandListener(midlet);
		if (fileMode != 1) {
			fileList.addCommand(dirSelectCmd);
			fileList.append("- Выбрать", null);
		}
		try {
			FileConnection fc = (FileConnection) Connector.open("file:///" + f);
			Enumeration list = fc.list();
			while (list.hasMoreElements()) {
				String s = (String) list.nextElement();
				if (s.endsWith("/")) {
					fileList.append(s.substring(0, s.length() - 1), null);
				} else if (s.startsWith(STATIONS_FILEPREFIX) && s.endsWith(".json")) {
					fileList.append(s, null);
				}
			}
			fc.close();
		} catch (Exception e) {
		}
		display(fileList);
	}
	
	private static void showFileList(int mode) {
		fileMode = mode;
		fileList = new List("", List.IMPLICIT);
		getRoots();
		for (int i = 0; i < rootsList.size(); i++) {
			String s = (String) rootsList.elementAt(i);
			if (s.startsWith("file:///")) s = s.substring("file:///".length());
			if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
			fileList.append(s, null);
		}
		fileList.addCommand(List.SELECT_COMMAND);
		fileList.setSelectCommand(List.SELECT_COMMAND);
		fileList.addCommand(backCmd);
		fileList.setCommandListener(midlet);
		display(fileList);
	}
	
	private static Form searchForm(int type, int zone, JSONArray stations) {
//		String zoneName = null;
//		if (zone != 0) {
//			int i = 0;
//			while (zonesAndCities[i][0] != zone && ++i < zoneNames.length);
//			zoneName = zoneNames[i];
//		}
		Form f = new Form((type == 1 ? "Выбор зоны" : type == 2 ? "Выбор города" : "Выбор станции")
//				+ (zoneName != null ? " (" + zoneName + ")" : "")
				);
		searchDoneCmdAdded = false;
		searchType = type;
		searchZone = zone;
		searchStations = stations;
		searchField = new TextField("Поиск", "", 100, TextField.ANY);
		searchField.setItemCommandListener(midlet);
		f.append(searchField);
		searchChoice = new ChoiceGroup("", Choice.EXCLUSIVE);
		searchChoice.setItemCommandListener(midlet);
		f.append(searchChoice);
		f.addCommand(cancelCmd);
		f.setCommandListener(midlet);
		f.setItemStateListener(midlet);
		return searchForm = f;
	}

	public void run() {
		int run;
		synchronized (this) {
			run = MahoRaspApp.run;
			notify(); // unblocks the UI
		}
		running = run != RUN_SEARCH && run != RUN_SEARCH_TIMER;
		switch(run) {
		case RUN_DOWNLOAD_STATIONS: { // скачать станции зоны
			progressAlert.setString("Очищение");
			try {
				RecordStore.deleteRecordStore(STATIONS_RECORDPREFIX + downloadZone);
			} catch (Exception e) {}
			System.gc();
			try {
				// XXX
				progressAlert.setString("Соединение");
				HttpConnection hc = (HttpConnection) Connector.open("http://export.rasp.yandex.net/v3/suburban/zone/"
						.concat(Integer.toString(downloadZone)));
				JSONArray r = new JSONArray();
				InputStream in = null;
				try {
					hc.setRequestMethod("GET");
					if (hc.getResponseCode() != 200) throw new IOException("Wrong response");
					progressAlert.setString("Скачивание");
					JSONStream j = JSONStream.getStream(in = hc.openInputStream());
//					JSONArray j = api("zone/" + downloadZone).getArray("zone_stations");
					j.expectNextTrim('{');
					j.jumpToKey("zone_stations");
					j.expectNextTrim('[');
					while (true) {
						JSONObject s = j.nextObject();
						JSONObject rs = new JSONObject();
						rs.put("d", s.getNullableString("direction"));
						rs.put("t", s.getString("title"));
						rs.put("i", s.getString("esr"));
						r.add(rs);
						if (j.nextTrim() == ']') break;
					}
				} finally {
					if (in != null) in.close();
					if (hc != null) hc.close();
				}
				progressAlert.setString("Запись");
				RecordStore rs = RecordStore.openRecordStore(STATIONS_RECORDPREFIX + downloadZone, true);
				byte[] b = r.toString().getBytes("UTF-8");
				rs.addRecord(b, 0, b.length);
				rs.closeRecordStore();
				b = null;
				r = null;
				if (choosing == 3) {
					showFileList(2);
					break;
				}
				display(searchForm(3, downloadZone, r));
				break;
			} catch (Throwable e) {
				progressAlert.setString(e.toString());
			}
			progressAlert.setCommandListener(midlet);
			progressAlert.addCommand(okCmd);
			break;
		}
		case RUN_SUBMIT: { // выполнить запрос
			Form f = resForm;
			try {
				uids.clear();
				
				Calendar cal = Calendar.getInstance();
				cal.setTime(dateField.getDate());
				searchDate = cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH);
				
				text.setLabel("Результаты");
				text.setText("Загрузка");
				
				JSONObject j;
				try {
					j = api("search_on_date?date=" + searchDate + "&" + (fromStation != null ? ("station_from=" + fromStation) : ("city_from=" + fromCity)) + "&" + (toStation != null ? ("station_to=" + toStation) : ("city_to=" + toCity)));
				} catch (Exception e) {
					if (e instanceof IOException || e instanceof SecurityException) {
						// попробовать загрузить кэшированное расписание
						try {
							RecordStore r = RecordStore.openRecordStore(SCHEDULE_RECORDPREFIX + searchDate
									+ (fromStation != null ? ("s" + fromStation) : ("c" + fromCity))
									+ (toStation != null ? ("s" + toStation) : ("c" + toCity)), false);
							j = JSONObject.parseObject(new String(r.getRecord(1), "UTF-8"));
							r.closeRecordStore();
							JSONArray a = j.getArray("a");
							if (a.size() > 0) {
								Calendar c = parseDate(j.getString("t"));
								text.setLabel(c.get(Calendar.DAY_OF_MONTH) + "." + (c.get(Calendar.MONTH) + 1) + "."
										+ c.get(Calendar.YEAR) + " " + n(c.get(Calendar.HOUR_OF_DAY)) + ":"
										+ n(c.get(Calendar.MINUTE)));
								text.setText("Результаты (кэш)\n");

								for (Enumeration e2 = a.elements(); e2.hasMoreElements();) {
									JSONObject seg = (JSONObject) e2.nextElement();
									StringItem s = new StringItem(seg.getString("t"), seg.getString("r"));
									s.setFont(smallfont);
									s.addCommand(itemCmd);
									s.setDefaultCommand(itemCmd);
									s.setItemCommandListener(this);
									f.append(s);
									uids.put(s, seg.getString("u"));
								}
							} else {
								text.setLabel("Результаты (кэш)");
								text.setText("Пусто");
							}
						} catch (Exception e2) {
							text.setLabel("Результаты");
							text.setText("Нет сети!\n" + e.toString());
						}
					}
					break;
				}
				// время сервера в UTC
				Calendar server_time = parseDate(j.getObject("date_time").getString("server_time"));

				text.setLabel("");
				text.setText("Результаты\n");
				
				if (j.has("teasers") && (teasers = j.getArray("teasers")) != null && teasers.size() > 0) {
					StringItem s = new StringItem("", "Уведомления", StringItem.BUTTON);
					s.setFont(smallfont);
					s.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
					s.addCommand(teasersCmd);
					s.setDefaultCommand(teasersCmd);
					s.setItemCommandListener(this);
					f.append(s);
				}
				
				int count = 0;
				int count2 = 0;
				// парс маршрутов
				JSONArray days = j.getArray("days");
				int l1 = days.size();
				for (int i = 0; i < l1; i++) { // дни
					JSONObject day = days.getObject(i);
					JSONArray segments = day.getArray("segments");
					int l2 = segments.size();
//					mainForm.append(day.getString("date") + "\n");
					for (int k = 0; k < l2; k++) {  // сегменты
						JSONObject seg = segments.getObject(k);
						count++;

						JSONObject departure = seg.getObject("departure");
						Calendar c = parseDate(departure.getString("time_utc"));
						// пропускать ушедшие сегодня
						if (!showGone && oneDay(parseDate(departure.getString("time")), server_time) && c.before(server_time)) continue;
						count2++;
						
						JSONObject thread = seg.getObject("thread");
						JSONObject arrival = seg.getObject("arrival");

						String res = "";
						// платформа отправления
						if (departure.has("platform")) {
							res += departure.getString("platform") + "\n";
						}
						
						// время отправления - время прибытия (длина)
						// показывается местное время
						String time = time(departure.getString("time")) + " - " + time(arrival.getString("time")) + " (" + duration(seg.getInt("duration")) + ")\n";
						
						// название
						res += thread.getString("title_short", thread.getString("title")) + "\n";
						
						// тариф
						JSONObject tariff = seg.getNullableObject("tariff");
						if (tariff != null) res += replaceOnce(tariff.getString("value"), ".0", "") + " " + tariff.getString("currency") + "\n";
						
						// опоздание
						if (departure.has("state")) {
							JSONObject state = departure.getObject("state");
							int minutes_from = state.getInt("minutes_from", -1);
							int minutes_to = state.getInt("minutes_to", -1);
							if (minutes_from >= 0 && minutes_to >= 0) {
								if (c.before(server_time)) { // ушел
									if (minutes_from == 0) {
										res += "Ушёл по расписанию";
									} else {
										res += "Ушёл позже на " + minutes_from + " мин.";
									}
								} else if (minutes_from > 0 || minutes_to > 0) {
									res += "Возможно опоздание ";
									if (minutes_from == minutes_to) {
										res += "на " + minutes_from + " мин.";
									} else {
										res += "от " + minutes_from + " до " + minutes_to + " мин.";
									}
								}
								res += "\n";
							} else if (state.has("type") && "possible_delay".equals(state.getString("type"))) {
								res += "Возможно опоздание\n";
							}
						}
						
						// транспорт
						if (thread.has("transport") && thread.getObject("transport").has("subtype")) {
							res += replaceOnce(thread.getObject("transport").getObject("subtype").getString("title"),"<br/>","\n") + "\n";
						}

						StringItem s = new StringItem(time, res);
						s.setFont(smallfont);
//						s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_BEFORE);
						s.addCommand(itemCmd);
						s.setDefaultCommand(itemCmd);
						s.setItemCommandListener(this);
						f.append(s);
						uids.put(s, thread.getString("uid"));
					}
				}
				if (count == 0) {
					text.setLabel("Результаты");
					text.setText("Пусто");
				} else if (count2 == 0) {
					text.setLabel("Результаты");
					text.setText("Все электрички уехали");
				}
			} catch (Exception e) {
				e.printStackTrace();
				text.setText(e.toString());
			}
			if (resForm == f)
				display(f);
			break;
		}
		case RUN_THREAD_INFO: { // доп информация по маршруту
			Form f = new Form(threadUid);
			f.addCommand(backCmd);
			f.setCommandListener(this);
			try {
				JSONObject j = api("thread_on_date/" + threadUid + "?date=" + searchDate);
				
				StringItem s;
				
				s = new StringItem(null, j.getString("title") + "\n" + j.getString("number", "") + "\nОстановки: " + j.getString("stops") + "\n");
				s.setFont(Font.getDefaultFont());
				f.append(s);
				
				for (Enumeration e = j.getArray("stations").elements(); e.hasMoreElements();) {
					JSONObject station = (JSONObject) e.nextElement();
					s = new StringItem("", station.getString("title") + " " + time(station.getNullableString("departure_local"))+"\n");
					s.setFont(smallfont);
					f.append(s);
				}
				
				if (j.has("days") && !j.isNull("days")) {
					s = new StringItem(null, "\nДни: " + j.getString("days") + "\n");
					s.setFont(Font.getDefaultFont());
					f.append(s);
				}
			} catch (Exception e) {
				f.append(e.toString());
			}
			display(f);
			break;
		}
		case RUN_CLEAR_RECORDS: { // очистка рекордов
			try {
				String[] records = RecordStore.listRecordStores();
				for (int i = 0; i < records.length; i++) {
					String record = records[i];
					if (record.startsWith(clear)) {
						try {
							RecordStore.deleteRecordStore(record);
						} catch (Exception e) {
						}
					}
				}
			} catch (Exception e) {
			}
			display(mainForm);
			break;
		}
		case RUN_BOOKMARKS: { // закладки
			List l = new List("Закладки", List.IMPLICIT);
			l.addCommand(backCmd);
			l.addCommand(List.SELECT_COMMAND);
			l.setCommandListener(this);
			try {
				if (bookmarks == null) {
					RecordStore r = RecordStore.openRecordStore(BOOKMARKS_RECORDNAME, false);
					bookmarks = JSONObject.parseArray(new String(r.getRecord(1), "UTF-8"));
					r.closeRecordStore();
				}
				l.addCommand(removeBookmarkCmd);
				for (Enumeration e = bookmarks.elements(); e.hasMoreElements();) {
					JSONObject bm = (JSONObject) e.nextElement();
					l.append(bm.getString("fn") + " - " + bm.getString("tn"), null);
				}
			} catch (RecordStoreNotFoundException e) {
			} catch (Exception e) {
				Form erf = new Form("Закладки");
				erf.append(e.toString());
				display(erf);
			}
			display(l);
			break;
		}
		case RUN_SAVE: { // сохранение расписания
			if ((fromCity == 0 && fromStation == null) || (toCity == 0 && toStation == null)) {
				break;
			}
			progressAlert.setString("Удаление");
			String scheduleRecordName = SCHEDULE_RECORDPREFIX + searchDate + (fromStation != null ? ("s" + fromStation) : ("c" + fromCity)) + (toStation != null ? ("s" + toStation) : ("c" + toCity));
			try {
				RecordStore.deleteRecordStore(scheduleRecordName);
			} catch (Exception e) {
			}
			try {
				JSONObject r = new JSONObject();
				JSONArray a = new JSONArray();
				progressAlert.setString("Загрузка");
				JSONObject j = api("search_on_date?date=" + searchDate + "&" + (fromStation != null ? ("station_from=" + fromStation) : ("city_from=" + fromCity)) + "&" + (toStation != null ? ("station_to=" + toStation) : ("city_to=" + toCity)));
				progressAlert.setString("Парс");
				r.put("t", j.getObject("date_time").getString("server_time"));
				for (Enumeration e2 = j.getArray("days").elements(); e2.hasMoreElements();) {
					JSONObject day = (JSONObject) e2.nextElement();
					for (Enumeration e3 = day.getArray("segments").elements(); e3.hasMoreElements();) { 
						JSONObject seg = (JSONObject) e3.nextElement();
						JSONObject sr = new JSONObject();

						JSONObject departure = seg.getObject("departure");
						//sr.put("d", departure.getString("time_utc"));
						
						JSONObject thread = seg.getObject("thread");
						JSONObject arrival = seg.getObject("arrival");
						
						sr.put("u", thread.getString("uid"));
						sr.put("t", time(departure.getString("time")) + " - " + time(arrival.getString("time")) + " (" + seg.getString("duration") + " мин)");
						
						String res = "";
						if (departure.has("platform")) {
							res += departure.getString("platform") + "\n";
						}
						res += thread.getString("title_short", thread.getString("title")) + "\n";
						
						JSONObject tariff = seg.getNullableObject("tariff");
						if (tariff != null) res += replaceOnce(tariff.getString("value"), ".0", "") + " " + tariff.getString("currency") + "\n";
						
						if (departure.has("state")) {
							JSONObject state = departure.getObject("state");
							int minutes_from = state.getInt("minutes_from", -1);
							int minutes_to = state.getInt("minutes_to", -1);
							if (minutes_from > 0 && minutes_to > 0) {
								res += "Возможно опоздание ";
								if (minutes_from == minutes_to) {
									res += "на " + minutes_from + " мин.";
								} else {
									res += "от " + minutes_from + " до " + minutes_to + " мин.";
								}
								res += "\n";
							} else if (state.has("type") && "possible_delay".equals(state.getString("type"))) {
								res += "Возможно опоздание\n";
							}
						}
						
						if (thread.has("transport") && thread.getObject("transport").has("subtype")) {
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
			break;
		}
		case RUN_SEARCH: { // поиск точки
			if (searching) {
				// отменить текущий поиск, если что-то уже ищется
				searchCancel = true;
				try {
					synchronized (searchLock) {
						searchLock.wait();
					}
				} catch (Exception e) {}
			}
			searching = true;
			s: {
				try {
					String query = searchField.getString().toLowerCase().trim();
					
					// варианты для поиска по словам
					String q1 = " ".concat(query);
					String q2 = "(".concat(query);
					String q3 = "-".concat(query);
					
					searchChoice.deleteAll();
					search: {
					if (searchType == 1) { // поиск зоны
						if (query.length() < 2) break search;
						int i = 0;
						while (i < zoneNames.length) {
							String s = zoneNames[i++];
							String t = s.toLowerCase();
							if (!t.startsWith(query) && t.indexOf(q1) == -1 /*&& t.indexOf(q2) == -1*/ && t.indexOf(q3) == -1)
								continue;
							if(t.length() == query.length()) {
								// проверка на точное совпадение. Полноценный еквалс тут слишком дорог, если
								// старт совпадает и длины тоже, то они уже равны.
								searchChoice.insert(0, s, null);
								searchChoice.setSelectedIndex(0, true);
							} else
								searchChoice.append(s, null);
						}
					} else if (searchType == 2) { // поиск города
						if (searchZone == 0) {
							// глобальный
							if (query.length() < 2) break search;
							int i = 0;
							while (i < cityNames.length) {
								String s = cityNames[i++];
								String t = s.toLowerCase();
								if (!t.startsWith(query) && t.indexOf(q1) == -1 /*&& t.indexOf(q2) == -1*/ && t.indexOf(q3) == -1)
									continue;
								if (t.length() == query.length()) {
									// проверка на точное совпадение. Полноценный еквалс тут слишком дорог, если
									// старт совпадает и длины тоже, то они уже равны.
									searchChoice.insert(0, s, null);
									searchChoice.setSelectedIndex(0, true);
								} else
									searchChoice.append(s, null);
							}
						} else {
							// внутри одной зоны
							int i = 0;
							int[] c;
							while ((c = zonesAndCities[i])[0] != searchZone && ++i < zonesAndCities.length);
							i = 0;
							while (i < c.length) {
								String s = cityNames[c[i++]];
								String t = s.toLowerCase();
								if (!t.startsWith(query) && t.indexOf(q1) == -1 /*&& t.indexOf(q2) == -1*/ && t.indexOf(q3) == -1)
									continue;
								searchChoice.append(s, null);
							}
						}
					} else if (searchType == 3) { // поиск станции
						if (query.length() < 2) break search; 
						int l = searchStations.size(), i = 0;
						while (i < l) {
							JSONObject j = searchStations.getObject(i++);
							// поиск в названии и направлении
							String t = j.getString("t").toLowerCase();
							String d = j.getString("d").toLowerCase();
							if (!d.startsWith(query) && !t.startsWith(query) && t.indexOf(q2) == -1 && t.indexOf(q1) == -1 && t.indexOf(q3) == -1)
								continue;
							searchChoice.append(j.getString("t").concat(" - ").concat(j.getString("d")), null);
						}
					}
					}
					if(searchForm == null || searchCancel) break s;
					// добавление функции "готово"
					searchDoneCmd();
				} catch (Throwable e) {
					if (searchCancel) break s;
					Alert a = new Alert("");
					a.setString(e.toString());
					display(a);
					e.printStackTrace();
				}
			}
			searchCancel = searching = false;
			synchronized (searchLock) {
				searchLock.notifyAll();
			}
			return;
		}
		case RUN_SEARCH_TIMER: {
			try {
				while (true) {
					if (searchTimer > 0 && --searchTimer == 0) start(RUN_SEARCH);
					Thread.sleep(100);
				}
			} catch (Exception ignored) {}
			searchThread = null;
			return;
		}
		}
		running = false;
	}

	private void start(int i) {
		try {
			synchronized (this) {
				run = i;
				new Thread(this).start();
				wait(); // blocks UI while thread is starting
			}
		} catch (Exception e) {}
	}
	
	// при type=1, 2: s - название зоны / города, s2 - не используется
	// при type=3: s - esr код станции, s2 - название
	private static void select(int type, String s, String s2) {
		searchForm = null;
		searchField = null;
		searchChoice = null;
		searchStations = null;
		if (type == 1) { // выбрана зона
			int id = 0;
			while (!s.equals(zoneNames[id]) && ++id < zoneNames.length);
			id = zonesAndCities[id][0];
			if (choosing == 3) {
				saveZone = id;
			} else if (choosing == 1) {
				fromZone = id;
			} else {
				toZone = id;
			}
			// проверка на наличие станций зоны в памяти
			try {
				RecordStore rs = RecordStore.openRecordStore(STATIONS_RECORDPREFIX + id, false);
				JSONArray r = JSONObject.parseArray(new String(rs.getRecord(1), "UTF-8"));
				if (choosing == 3) {
					showFileList(2);
				} else {
					display(searchForm(3, id, r));
				}
				rs.closeRecordStore();
			} catch (Exception e) {
				downloadZone = id;
				Alert a = new Alert("");
				a.setString("Станции зоны \"" + s + "\" не найдены в кэше. Загрузить?");
				a.addCommand(new Command("Загрузить", Command.OK, 4));
				a.addCommand(choosing == 3 ? new Command("Отмена", Command.CANCEL, 5) :
						new Command("Выбрать город", Command.CANCEL, 3));
				a.setCommandListener(midlet);
				display(a);
			}
			return;
		}
		if (type == 2) { // выбран город
			int id = getCity(searchZone, s);
			if (choosing == 1) {
				fromStation = null;
				fromCity = id;
				fromBtn.setText(s);
			} else {
				toStation = null;
				toCity = id;
				toBtn.setText(s);
			}
			display(mainForm);
			return;
		}
		if (type == 3) { // выбрана станция
			if (choosing == 1) {
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
		if (choosing == 1) {
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
	
	private static int getCity(int zone, String name) {
		int r = 0;
		if (zone == 0) {
			while (!name.equals(cityNames[r]) && ++r < cityNames.length);
		} else {
			int i = 0;
			int[] z;
			while ((z = zonesAndCities[i])[0] != zone && ++i < zonesAndCities.length);
			i = 0;
			while (!name.equals((cityNames[r = z[i]])) && ++i < z.length);
		}
		return r == cityNames.length ? 0 : cityIds[r];
	}
	
	/// Утилки
	
	private static void display(Alert a, Displayable d) {
		if (d == null) {
			display.setCurrent(a);
			return;
		}
		display.setCurrent(a, d);
	}

	private static void display(Displayable d) {
		if (d instanceof Alert) {
			display.setCurrent((Alert) d, mainForm);
			return;
		}
		display.setCurrent(d);
	}

	private static Alert loadingAlert(String text) {
		Alert a = new Alert("");
		a.setString(text);
		a.setIndicator(new Gauge(null, false, Gauge.INDEFINITE, Gauge.CONTINUOUS_RUNNING));
		a.setTimeout(30000);
		return a;
	}

	private static Alert warningAlert(String text) {
		Alert a = new Alert("");
		a.setType(AlertType.ERROR);
		a.setString(text);
		a.setTimeout(2000);
		return a;
	}
	
	private static Alert infoAlert(String text) {
		Alert a = new Alert("");
		a.setType(AlertType.CONFIRMATION);
		a.setString(text);
		a.setTimeout(1500);
		return a;
	}
	
	private static byte[] readBytes(InputStream inputStream, int initialSize, int bufferSize, int expandSize) throws IOException {
		if (initialSize <= 0) initialSize = bufferSize;
		byte[] buf = new byte[initialSize];
		int count = 0;
		byte[] readBuf = new byte[bufferSize];
		int readLen;
		while ((readLen = inputStream.read(readBuf)) != -1) {
			if (count + readLen > buf.length) {
				byte[] newbuf = new byte[count + expandSize];
				System.arraycopy(buf, 0, newbuf, 0, count);
				buf = newbuf;
			}
			System.arraycopy(readBuf, 0, buf, count, readLen);
			count += readLen;
		}
		if (buf.length == count) {
			return buf;
		}
		byte[] res = new byte[count];
		System.arraycopy(buf, 0, res, 0, count);
		return res;
	}
	
	private static String n(int n) {
		if (n < 10) {
			return "0".concat(Integer.toString(n));
		} else return Integer.toString(n);
	}
	
	// парсер даты ISO 8601 без учета часового пояса
	private static Calendar parseDate(String date) {
		Calendar c = Calendar.getInstance();
		if (date.indexOf('T') != -1) {
			String[] dateSplit = split(date.substring(0, date.indexOf('T')), '-');
			String[] timeSplit = split(date.substring(date.indexOf('T')+1), ':');
			String second = split(timeSplit[2], '.')[0];
			int i = second.indexOf('+');
			if (i == -1) {
				i = second.indexOf('-');
			}
			if (i != -1) {
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
		if (date == null) return "";
		Calendar c = parseDate(date);
		//return c.get(Calendar.DAY_OF_MONTH) + " " + localizeMonthWithCase(c.get(Calendar.MONTH)) + " " +
		return n(c.get(Calendar.HOUR_OF_DAY)) + ":" + n(c.get(Calendar.MINUTE));
	}
	
	private static boolean oneDay(Calendar a, Calendar b) {
		return a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH) &&
				a.get(Calendar.MONTH) == b.get(Calendar.MONTH) &&
				a.get(Calendar.YEAR) == b.get(Calendar.YEAR);
	}
	
	private static String duration(int minutes) {
		if (minutes > 24 * 60) {
			minutes /= 60;
			return (minutes / 24) + " д. " + (minutes % 24) + " ч.";
		}
		if (minutes > 60) {
			return (minutes / 60) + " ч. " + (minutes % 60) + " мин";
		}
		return minutes + " мин";
	}
	
	private static String[] split(String str, char d) {
		int i = 0;
		Vector v = new Vector();
		char[] arr = str.toCharArray();
		for (int j = 0; j < arr.length; ++j) {
			if (arr[j] == d) {
				v.addElement(new String(arr, i, j - i));
				i = j + 1;
			}
		}
		if (v.size() == 0) return new String[] {str};
		if (i < arr.length)
			v.addElement(new String(arr, i, arr.length - i));
		String[] r = new String[v.size()];
		v.copyInto(r);
		return r;
	}
	
	private static JSONObject api(String url) throws Exception {
		Object r;
		{ // getUtf inlined
			HttpConnection hc = null;
			InputStream in = null;
			try {
				(hc = (HttpConnection) Connector.open("http://export.rasp.yandex.net/v3/suburban/".concat(url)))
				.setRequestMethod("GET");
				int i, j;
				in = hc.openInputStream();
				byte[] buf = new byte[(i = (int) hc.getLength()) == -1 ? 1024 : i];
				i = 0;
				while ((j = in.read(buf, i, buf.length - i)) != -1) {
					if ((i += j) == buf.length) {
						System.arraycopy(buf, 0, buf = new byte[i + 2048], 0, i);
					}
				}
				r = new String(buf, 0, i, "UTF-8");
			} finally {
				try {
					if (in != null) in.close();
				} catch (IOException e) {
				}
				try {
					if (hc != null) hc.close();
				} catch (IOException e) {
				}
			}
		}
		r = JSONObject.parseObject((String) r);
		if (((JSONObject) r).has("error")) {
			// выбрасывать эксепшн с текстом ошибки
			throw new Exception(((JSONObject) r).getObject("error").getString("text"));
		}
		return (JSONObject) r;
	}
	
	private static String replaceOnce(String str, String hay, String ned) {
		int idx = str.indexOf(hay);
		if (idx != -1) {
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
	
	public static void getRoots() {
		if (rootsList != null) return;
		rootsList = new Vector();
		try {
			Enumeration roots = FileSystemRegistry.listRoots();
			while (roots.hasMoreElements()) {
				String s = (String) roots.nextElement();
				if (s.startsWith("file:///")) s = s.substring("file:///".length());
				rootsList.addElement(s);
			}
		} catch (Exception e) {
		}
	}

}
