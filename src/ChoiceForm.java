import java.util.Enumeration;

import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;

import cc.nnproject.json.JSONObject;

public class ChoiceForm extends Form implements CommandListener, ItemCommandListener, ItemStateListener, Runnable {
	
	private static final Command searchCmd = new Command("Поиск", Command.ITEM, 2);
	private static final Command doneCmd = new Command("Готово", Command.EXIT, 1);

	private int type; // 1 - зона, 2 - город, 3 - станция
	private int zone;
	private TextField field;
	private StringItem btn;
	private ChoiceGroup choice;
	private Thread thread;

	public ChoiceForm(int type, int zone) {
		super(type == 1 ? "Выбор зоны" : "Выбор города");
		this.type = type;
		this.zone = zone;
		field = new TextField("", "", 100, TextField.ANY);
		field.addCommand(searchCmd);
		field.setDefaultCommand(searchCmd);
		field.setItemCommandListener(this);
		append(field);
		btn = new StringItem("", "Поиск", StringItem.BUTTON);
		btn.addCommand(searchCmd);
		btn.setDefaultCommand(searchCmd);
		btn.setItemCommandListener(this);
		append(btn);
		choice = new ChoiceGroup("", Choice.EXCLUSIVE);
		//choice.addCommand(doneCmd);
		//choice.setDefaultCommand(doneCmd);
		//choice.setItemCommandListener(this);
		append(choice);
		addCommand(doneCmd);
		setCommandListener(this);
		setItemStateListener(this);
	}
	
	public void run() {
		String query = field.getString().toLowerCase().trim();
		choice.deleteAll();
		if(type == 1) {
			Enumeration e = MahoRaspApp.zones.keys();
			while(e.hasMoreElements()) {
				String z = (String) e.nextElement();
				if(z.toLowerCase().indexOf(query) != -1) {
					choice.append(z, null);
				}
			}
		} else if(type == 2) {
			Enumeration e = MahoRaspApp.zones.getTable().elements();
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
				if(s.toLowerCase().indexOf(query) != -1) {
					choice.append(s, null);
				}
			}
		} else if(type == 3) {
			
		}
	}

	public void commandAction(Command c, Item item) {
		commandAction(c, this);
	}

	public void commandAction(Command c, Displayable d) {
		if(c == searchCmd) {
			thread = new Thread(this);
			thread.run();
			return;
		}
		if(c == doneCmd) {
			int i = choice.getSelectedIndex();
			if(i == -1) return;
			MahoRaspApp.midlet.done(type, choice.getString(i));
			return;
		}
	}

	public void itemStateChanged(Item item) {
		if(item == field) {
			commandAction(searchCmd, field);
		}
	}

}
