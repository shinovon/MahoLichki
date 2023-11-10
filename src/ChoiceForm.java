import java.util.Enumeration;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.TextField;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

// форма поиска
public class ChoiceForm extends Form implements CommandListener, ItemCommandListener, ItemStateListener, Runnable {
	
	private static final Command searchCmd = new Command("Поиск", Command.ITEM, 2);
	private static final Command doneCmd = new Command("Готово", Command.EXIT, 1);
	private static final Command cancelCmd = new Command("Отмена", Command.EXIT, 1);

	private int type; // 1 - зона, 2 - город, 3 - станция
	private int zone;
	private TextField field;
	private ChoiceGroup choice;
	private Thread thread;
	private JSONArray stations;
	private boolean cancel = true;
	private boolean searching;

	public ChoiceForm(int type, int zone, JSONArray stations) {
		super(type == 1 ? "Выбор зоны" : type == 2 ? "Выбор города" : "Выбор станции");
		this.type = type;
		this.zone = zone;
		this.stations = stations;
		field = new TextField("Поиск", "", 100, TextField.ANY);
		field.addCommand(searchCmd);
		field.setDefaultCommand(searchCmd);
		field.setItemCommandListener(this);
		append(field);
		// TODO: ж2ме лоадер
//		StringItem btn = new StringItem("", "Поиск", StringItem.BUTTON);
//		btn.addCommand(searchCmd);
//		btn.setDefaultCommand(searchCmd);
//		btn.setItemCommandListener(this);
//		append(btn);
		choice = new ChoiceGroup("", Choice.EXCLUSIVE);
//		choice.addCommand(doneCmd);
//		choice.setDefaultCommand(doneCmd);
//		choice.setItemCommandListener(this);
		append(choice);
		addCommand(cancelCmd);
		setCommandListener(this);
		setItemStateListener(this);
	}
	
	public void run() {
		searching = true;
		try {
		String query = field.getString().toLowerCase().trim();
		choice.deleteAll();
		// TODO: нормальный поиск а не startsWith
		search: {
		if(type == 1) { // поиск зоны
			if(query.length() < 2) break search;
			Enumeration e = MahoRaspApp.zones.keys();
			while(e.hasMoreElements()) {
				String z = (String) e.nextElement();
				if(!z.toLowerCase().startsWith(query)) continue;
				choice.append(z, null);
			}
		} else if(type == 2) { // поиск города
			Enumeration e = MahoRaspApp.zones.getTable().elements();
			if(zone == 0) {
				// глобальный
				if(query.length() < 2) break search;
				while(e.hasMoreElements()) {
					JSONObject z = ((JSONObject) e.nextElement()).getObject("s");
					Enumeration e2 = z.keys();
					while(e2.hasMoreElements()) {
						String s = (String) e2.nextElement();
						if(!s.toLowerCase().startsWith(query)) continue;
						choice.append(s, null);
					}
				}
			} else {
				// внутри одной зоны
				JSONObject z = null;
				while(e.hasMoreElements()) {
					z = (JSONObject) e.nextElement();
					if(z.getInt("i") == zone) {
						break;
					}
				}
				e = z.getObject("s").keys();
				while(e.hasMoreElements()) {
					String s = (String) e.nextElement();
					if(!s.toLowerCase().startsWith(query)) continue;
					choice.append(s, null);
				}
			}
		} else if(type == 3) { // поиск станции
			if(query.length() < 2) break search; 
			Enumeration e = stations.elements();
			while(e.hasMoreElements()) {
				JSONObject s = (JSONObject) e.nextElement();
				// поиск в названии и направлении
				if(!s.getString("d").toLowerCase().startsWith(query)
						&& !s.getString("t").toLowerCase().startsWith(query)) continue;
				choice.append(s.getString("t") + " - " + s.getString("d"), null);
			}
		}
		}
		searching = false;
		// заменить функцию "отмена" на "готово"
		if(choice.getSelectedIndex() != -1) {
			if(!cancel) return;
			removeCommand(cancelCmd);
			addCommand(doneCmd);
			cancel = false;
			return;
		}
		if(cancel) return;
		removeCommand(doneCmd);
		addCommand(cancelCmd);
		cancel = true;
		} catch (Throwable e) {
			Alert a = new Alert("");
			a.setString(e.toString());
			MahoRaspApp.midlet.display(a);
			e.printStackTrace();
		}
	}

	public void commandAction(Command c, Item item) {
		commandAction(c, this);
	}

	public void commandAction(Command c, Displayable d) {
		if(c == searchCmd) {
			// не выполнять поиск если текущий поток поиска занят
			if(thread != null && searching) return;
			thread = new Thread(this);
			thread.run();
			return;
		}
		if(c == cancelCmd) {
			MahoRaspApp.midlet.cancelChoice();
			return;
		}
		if(c == doneCmd) {
			int i = choice.getSelectedIndex();
			if(i == -1) { // если список пустой то отмена
				MahoRaspApp.midlet.cancelChoice();
				return;
			}
			String s = choice.getString(i);
			if(type == 3) { // выбрана станция
				String esr = null;
				Enumeration e = stations.elements();
				while(e.hasMoreElements()) {
					JSONObject j = (JSONObject) e.nextElement();
					if((j.getString("t") + " - " + j.getString("d")).equals(s)) {
						esr = j.getString("i");
						break;
					}
				}
				MahoRaspApp.midlet.select(type, esr, s);
				return;
			}
			// зона или город
			MahoRaspApp.midlet.select(type, s, null);
			return;
		}
	}

	public void itemStateChanged(Item item) {
		if(item == field) { // выполнять поиск при изменениях в поле ввода
			commandAction(searchCmd, field);
		}
	}

}
